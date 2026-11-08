#!/usr/bin/env python3
import argparse
import json
import os
import re
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

if sys.platform == "win32":
    import winreg

BRANCH = "agent-mailbox"
WORKTREE_NAME = ".agent-mailbox"
NODE_CONFIG = "teachtrack.agentNode"
CLOSED_STATUSES = {"completed", "rejected", "cancelled"}

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")


class MailboxError(RuntimeError):
    pass


class QueuedError(MailboxError):
    pass


def git_environment():
    env = os.environ.copy()
    env["GCM_INTERACTIVE"] = "never"
    env["GIT_TERMINAL_PROMPT"] = "0"
    if sys.platform != "win32" or env.get("HTTPS_PROXY") or env.get("https_proxy"):
        return env
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Microsoft\Windows\CurrentVersion\Internet Settings") as key:
            enabled, _ = winreg.QueryValueEx(key, "ProxyEnable")
            server, _ = winreg.QueryValueEx(key, "ProxyServer")
        if enabled and server:
            proxies = {}
            for part in server.split(";"):
                scheme, separator, address = part.partition("=")
                if separator:
                    proxies[scheme.strip()] = address.strip()
                else:
                    proxies["https"] = part.strip()
            proxy = proxies.get("https") or proxies.get("http")
            if proxy:
                if "://" not in proxy:
                    proxy = f"http://{proxy}"
                env.setdefault("HTTP_PROXY", proxy)
                env.setdefault("HTTPS_PROXY", proxy)
    except OSError:
        pass
    return env


def git(repo, *args, check=True):
    result = subprocess.run(
        ["git", *args],
        cwd=repo,
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=True,
        env=git_environment(),
    )
    if check and result.returncode:
        detail = result.stderr.strip() or result.stdout.strip()
        raise MailboxError(f"git {' '.join(args)}: {detail}")
    return result


def repo_root(path):
    return Path(git(path, "rev-parse", "--show-toplevel").stdout.strip()).resolve()


def node_name(value):
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,39}", value):
        raise argparse.ArgumentTypeError("节点名只能包含小写字母、数字和连字符")
    return value


def utc_now():
    return datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")


def event_id(prefix):
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S")
    return f"{prefix}-{stamp}-{uuid.uuid4().hex[:12]}"


def worktree(root):
    path = root / WORKTREE_NAME
    if not path.is_dir() or not (path / ".git").exists():
        raise MailboxError("信箱尚未初始化，请先运行 init --node <节点名>")
    return path


def current_node(root):
    result = git(root, "config", "--get", NODE_CONFIG, check=False)
    if result.returncode or not result.stdout.strip():
        raise MailboxError("未配置本机节点名，请先运行 init --node <节点名>")
    return result.stdout.strip()


def ensure_clean(mailbox):
    status = git(mailbox, "status", "--porcelain").stdout.strip()
    if status:
        raise MailboxError(f"信箱 worktree 有未提交改动，请先处理：\n{status}")


def sync_mailbox(mailbox):
    ensure_clean(mailbox)
    git(mailbox, "fetch", "origin", BRANCH)
    git(mailbox, "pull", "--rebase", "origin", BRANCH)
    git(mailbox, "push", "origin", BRANCH)


def init_mailbox(root, node):
    mailbox = root / WORKTREE_NAME
    remote = git(root, "ls-remote", "--heads", "origin", f"refs/heads/{BRANCH}")
    remote_exists = bool(remote.stdout.strip())
    local_exists = git(root, "show-ref", "--verify", "--quiet", f"refs/heads/{BRANCH}", check=False).returncode == 0

    if not mailbox.exists():
        if local_exists:
            git(root, "worktree", "add", str(mailbox), BRANCH)
        elif remote_exists:
            git(root, "fetch", "origin", BRANCH)
            git(root, "worktree", "add", "-b", BRANCH, str(mailbox), f"origin/{BRANCH}")
        else:
            git(root, "worktree", "add", "-b", BRANCH, str(mailbox), "HEAD")
    elif not (mailbox / ".git").exists():
        raise MailboxError(f"{mailbox} 已存在但不是信箱 worktree")

    git(root, "config", NODE_CONFIG, node)
    if not remote_exists:
        ensure_clean(mailbox)
        if git(mailbox, "status", "--porcelain").stdout.strip():
            raise MailboxError("信箱初始化后存在意外改动")
        git(mailbox, "commit", "--allow-empty", "-m", "chore: initialize agent mailbox")
        git(mailbox, "push", "-u", "origin", BRANCH)
    else:
        sync_mailbox(mailbox)
    print(f"已初始化节点 {node}；独立 worktree：{mailbox}")


def read_events(mailbox, directory):
    folder = mailbox / directory
    events = []
    for path in folder.glob("*.json"):
        try:
            events.append(json.loads(path.read_text(encoding="utf-8")))
        except (OSError, json.JSONDecodeError) as exc:
            raise MailboxError(f"无法读取 {path}: {exc}") from exc
    return sorted(events, key=lambda item: (item.get("createdAt", ""), item.get("id", "")))


def all_events(mailbox):
    return read_events(mailbox, "messages"), read_events(mailbox, "updates")


def commit_event(mailbox, path, message):
    relative = path.relative_to(mailbox).as_posix()
    git(mailbox, "add", "--", relative)
    git(mailbox, "commit", "-m", message, "--", relative)


def publish_or_queue(mailbox, identifier):
    try:
        sync_mailbox(mailbox)
    except MailboxError as exc:
        raise QueuedError(f"{identifier} 已保存在本机，联网后运行 sync 发送。原因：{exc}") from exc


def send_message(root, args):
    mailbox = worktree(root)
    sender = current_node(root)
    ensure_clean(mailbox)
    identifier = event_id("msg")
    message = {
        "schemaVersion": 1,
        "id": identifier,
        "taskId": args.task_id or identifier,
        "kind": args.kind,
        "priority": args.priority,
        "from": sender,
        "to": args.to,
        "title": args.title,
        "body": args.message,
        "createdAt": utc_now(),
    }
    path = mailbox / "messages" / f"{identifier}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(message, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    commit_event(mailbox, path, f"agent: {sender} -> {args.to} {args.title}")
    publish_or_queue(mailbox, identifier)
    print(identifier)


def acknowledge(root, args):
    mailbox = worktree(root)
    node = current_node(root)
    ensure_clean(mailbox)
    messages, _ = all_events(mailbox)
    target = next((item for item in messages if item["id"] == args.message_id), None)
    if target is None:
        raise MailboxError(f"找不到消息 {args.message_id}；请先 sync")
    if target["to"] not in {node, "*"}:
        raise MailboxError(f"消息 {args.message_id} 不是发给节点 {node} 的")
    if args.status in {"completed", "blocked", "rejected"} and not args.message.strip():
        raise MailboxError(f"{args.status} 回执必须填写结果或原因")

    identifier = event_id("ack")
    update = {
        "schemaVersion": 1,
        "id": identifier,
        "messageId": target["id"],
        "taskId": target.get("taskId", target["id"]),
        "from": node,
        "status": args.status,
        "body": args.message,
        "createdAt": utc_now(),
    }
    path = mailbox / "updates" / f"{identifier}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(update, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    commit_event(mailbox, path, f"agent: {node} {args.status} {target['id']}")
    publish_or_queue(mailbox, identifier)
    print(identifier)


def updates_for(updates, message_id):
    return [item for item in updates if item.get("messageId") == message_id]


def view_for_node(messages, updates, node, incoming, include_closed, message_id=None):
    view = []
    for message in messages:
        if message_id and message.get("id") != message_id:
            continue
        is_incoming = message.get("to") in {node, "*"}
        is_outgoing = message.get("from") == node
        if not is_incoming and not is_outgoing:
            continue
        if incoming and not is_incoming:
            continue
        history = updates_for(updates, message["id"])
        relevant = [item for item in history if message.get("to") == "*" or item.get("from") == message.get("to")]
        latest = relevant[-1] if relevant else None
        if incoming and not include_closed and latest and latest.get("status") in CLOSED_STATUSES:
            continue
        view.append({**message, "latestStatus": latest.get("status") if latest else "pending", "updates": history})
    return view


def print_view(view, as_json):
    if as_json:
        print(json.dumps(view, ensure_ascii=False, indent=2))
        return
    if not view:
        print("没有符合条件的消息")
        return
    for item in view:
        print(f"{item['id']} [{item['latestStatus']}] {item['from']} -> {item['to']}")
        print(f"标题：{item['title']}")
        print(item["body"])
        for update in item["updates"]:
            print(f"回执 {update['from']} / {update['status']}：{update['body']}")
        print()


def load_view(root, args, incoming):
    mailbox = worktree(root)
    node = current_node(root)
    try:
        sync_mailbox(mailbox)
    except MailboxError as exc:
        print(f"警告：同步失败，显示本机已有消息：{exc}", file=sys.stderr)
    messages, updates = all_events(mailbox)
    return view_for_node(messages, updates, node, incoming, args.include_closed, getattr(args, "message_id", None))


def append_watch_log(root, message):
    path = root / ".agent-mailbox-watch.log"
    if path.exists() and path.stat().st_size > 1_000_000:
        path.replace(path.with_suffix(".log.1"))
    with path.open("a", encoding="utf-8") as stream:
        stream.write(f"{utc_now()} {message}\n")


def watch_mailbox(root, interval, once):
    if interval < 5:
        raise MailboxError("自动接收间隔不能小于 5 秒")
    mailbox = worktree(root)
    node = current_node(root)
    seen = set()
    last_error = ""
    append_watch_log(root, f"watch started; node={node}; interval={interval}s")
    while True:
        try:
            sync_mailbox(mailbox)
            messages, updates = all_events(mailbox)
            events = messages + updates
            new_events = [item for item in events if item.get("id") not in seen]
            if new_events:
                append_watch_log(root, f"received {len(new_events)} new event(s): " + ", ".join(item["id"] for item in new_events))
            seen = {item.get("id") for item in events}
            last_error = ""
            if once:
                print(f"自动接收正常：{len(messages)} 条消息，{len(updates)} 条回执")
                return 0
        except MailboxError as exc:
            if once:
                raise
            if str(exc) != last_error:
                append_watch_log(root, f"sync error: {exc}")
                last_error = str(exc)
        time.sleep(interval)


def build_parser():
    parser = argparse.ArgumentParser(description="通过独立 Git 分支在多台电脑的 Agent 之间传递任务和结果")
    parser.add_argument("--repo", default=str(Path(__file__).resolve().parents[1]), help=argparse.SUPPRESS)
    commands = parser.add_subparsers(dest="command", required=True)

    init = commands.add_parser("init", help="初始化本机节点")
    init.add_argument("--node", required=True, type=node_name)

    commands.add_parser("sync", help="接收远端消息并发送本机排队消息")

    watch = commands.add_parser("watch", help="持续自动接收消息和发送排队事件")
    watch.add_argument("--interval", type=int, default=60, help="同步间隔秒数，默认 60")
    watch.add_argument("--once", action="store_true", help=argparse.SUPPRESS)

    send = commands.add_parser("send", help="发送任务、报告或备注")
    send.add_argument("--to", required=True, type=node_name)
    send.add_argument("--kind", choices=["task", "report", "note"], default="task")
    send.add_argument("--priority", choices=["low", "normal", "high"], default="normal")
    send.add_argument("--task-id")
    send.add_argument("--title", required=True)
    send.add_argument("--message", required=True)

    inbox = commands.add_parser("inbox", help="查看发给本机的未完成任务")
    inbox.add_argument("--include-closed", action="store_true")
    inbox.add_argument("--json", action="store_true")

    status = commands.add_parser("status", help="查看收发消息及全部回执")
    status.add_argument("--id", dest="message_id")
    status.add_argument("--include-closed", action="store_true", default=True)
    status.add_argument("--json", action="store_true")

    ack = commands.add_parser("ack", help="回复接收、进度、完成或阻塞状态")
    ack.add_argument("message_id")
    ack.add_argument("--status", required=True, choices=["accepted", "progress", "completed", "blocked", "rejected", "cancelled"])
    ack.add_argument("--message", default="")
    return parser


def main():
    args = build_parser().parse_args()
    root = repo_root(Path(args.repo).resolve())
    try:
        if args.command == "init":
            init_mailbox(root, args.node)
        elif args.command == "sync":
            sync_mailbox(worktree(root))
            print("信箱已同步")
        elif args.command == "watch":
            return watch_mailbox(root, args.interval, args.once)
        elif args.command == "send":
            send_message(root, args)
        elif args.command == "ack":
            acknowledge(root, args)
        elif args.command == "inbox":
            print_view(load_view(root, args, True), args.json)
        elif args.command == "status":
            print_view(load_view(root, args, False), args.json)
        return 0
    except QueuedError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    except MailboxError as exc:
        print(f"错误：{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
