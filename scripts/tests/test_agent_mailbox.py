import json
import os
import subprocess
import sys
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from unittest import mock

from scripts import agent_mailbox

SCRIPT = Path(__file__).resolve().parents[1] / "agent_mailbox.py"


class AgentMailboxTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.origin = self.root / "origin.git"
        self.git(self.root, "init", "--bare", str(self.origin))
        seed = self.root / "seed"
        self.git(self.root, "clone", str(self.origin), str(seed))
        self.identity(seed)
        (seed / "README.md").write_text("mailbox test\n", encoding="utf-8")
        self.git(seed, "add", "README.md")
        self.git(seed, "commit", "-m", "initial")
        self.git(seed, "push", "-u", "origin", "HEAD:main")
        self.dev = self.root / "dev"
        self.server = self.root / "server"
        self.git(self.root, "clone", "-b", "main", str(self.origin), str(self.dev))
        self.git(self.root, "clone", "-b", "main", str(self.origin), str(self.server))
        self.identity(self.dev)
        self.identity(self.server)

    def tearDown(self):
        for repo in [self.dev, self.server]:
            self.git(repo, "worktree", "remove", "--force", str(repo / ".agent-mailbox"), check=False)
        self.temp.cleanup()

    def git(self, repo, *args, check=True):
        return subprocess.run(
            ["git", *args], cwd=repo, check=check, text=True, encoding="utf-8", capture_output=True
        )

    def identity(self, repo):
        self.git(repo, "config", "user.name", "Mailbox Test")
        self.git(repo, "config", "user.email", "mailbox@example.invalid")

    def mailbox(self, repo, *args, expected=0):
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--repo", str(repo), *args],
            text=True,
            encoding="utf-8",
            errors="replace",
            capture_output=True,
        )
        self.assertEqual(expected, result.returncode, result.stdout + result.stderr)
        return result

    def initialize(self):
        self.mailbox(self.dev, "init", "--node", "dev")
        self.mailbox(self.server, "init", "--node", "server")

    def test_full_task_handoff_does_not_touch_business_worktree(self):
        self.initialize()
        business_file = self.dev / "README.md"
        business_file.write_text("uncommitted business work\n", encoding="utf-8")
        main_head = self.git(self.dev, "rev-parse", "HEAD").stdout

        sent = self.mailbox(
            self.dev,
            "send",
            "--to",
            "server",
            "--title",
            "提交导入报告",
            "--message",
            "提交四个报告文档并推送，然后回复完成回执。",
        )
        message_id = sent.stdout.strip()
        self.assertTrue(message_id.startswith("msg-"))
        self.assertEqual(main_head, self.git(self.dev, "rev-parse", "HEAD").stdout)
        self.assertIn(" M README.md", self.git(self.dev, "status", "--porcelain").stdout)

        inbox = json.loads(self.mailbox(self.server, "inbox", "--json").stdout)
        self.assertEqual([message_id], [item["id"] for item in inbox])
        self.assertEqual("pending", inbox[0]["latestStatus"])
        self.mailbox(self.server, "ack", message_id, "--status", "accepted")
        self.mailbox(
            self.server,
            "ack",
            message_id,
            "--status",
            "completed",
            "--message",
            "四个文档已提交并推送。",
        )

        status = json.loads(self.mailbox(self.dev, "status", "--id", message_id, "--json").stdout)
        self.assertEqual("completed", status[0]["latestStatus"])
        self.assertEqual(["accepted", "completed"], [item["status"] for item in status[0]["updates"]])
        self.assertEqual("[]", self.mailbox(self.server, "inbox", "--json").stdout.strip())

    def test_offline_messages_queue_and_concurrent_sends_rebase(self):
        self.initialize()
        for repo in [self.dev, self.server]:
            self.git(repo, "remote", "set-url", "origin", str(self.root / "offline.git"))

        self.mailbox(
            self.dev,
            "send",
            "--to",
            "server",
            "--title",
            "开发机任务",
            "--message",
            "离线排队 A",
            expected=2,
        )
        self.mailbox(
            self.server,
            "send",
            "--to",
            "dev",
            "--title",
            "服务器报告",
            "--message",
            "离线排队 B",
            expected=2,
        )
        for repo in [self.dev, self.server]:
            self.git(repo, "remote", "set-url", "origin", str(self.origin))

        self.mailbox(self.dev, "sync")
        self.mailbox(self.server, "sync")
        server_inbox = json.loads(self.mailbox(self.server, "inbox", "--json").stdout)
        dev_inbox = json.loads(self.mailbox(self.dev, "inbox", "--json").stdout)
        self.assertEqual(["开发机任务"], [item["title"] for item in server_inbox])
        self.assertEqual(["服务器报告"], [item["title"] for item in dev_inbox])
        self.assertEqual(2, len(list((self.dev / ".agent-mailbox" / "messages").glob("*.json"))))

    def test_watch_once_receives_pending_message_and_writes_log(self):
        self.initialize()
        message_id = self.mailbox(
            self.dev,
            "send",
            "--to",
            "server",
            "--title",
            "自动接收",
            "--message",
            "后台监视器应拉取此消息",
        ).stdout.strip()
        result = self.mailbox(self.server, "watch", "--once")
        self.assertIn("自动接收正常", result.stdout)
        log = (self.server / ".agent-mailbox-watch.log").read_text(encoding="utf-8")
        self.assertIn(message_id, log)
        inbox = json.loads(self.mailbox(self.server, "inbox", "--json").stdout)
        self.assertEqual(message_id, inbox[0]["id"])
        self.assertEqual("pending", inbox[0]["latestStatus"])

    def test_fetch_refreshes_remote_tracking_before_rebase(self):
        self.initialize()
        stale = self.git(self.server, "rev-parse", "origin/agent-mailbox").stdout.strip()
        message_id = self.mailbox(
            self.dev,
            "send",
            "--to",
            "server",
            "--title",
            "远端新消息",
            "--message",
            "验证抓取后立即更新远端跟踪分支",
        ).stdout.strip()
        self.git(self.server, "update-ref", "refs/remotes/origin/agent-mailbox", stale)
        self.mailbox(self.server, "sync")
        expected = self.git(self.dev / ".agent-mailbox", "rev-parse", "HEAD").stdout.strip()
        actual = self.git(self.server, "rev-parse", "origin/agent-mailbox").stdout.strip()
        self.assertEqual(expected, actual)
        inbox = json.loads(self.mailbox(self.server, "inbox", "--json").stdout)
        self.assertEqual(message_id, inbox[0]["id"])

    def test_concurrent_sync_is_serialized(self):
        self.initialize()
        with ThreadPoolExecutor(max_workers=4) as executor:
            list(executor.map(lambda _: self.mailbox(self.server, "sync"), range(4)))
        git_dir = Path(self.git(self.server, "rev-parse", "--git-dir").stdout.strip())
        fetch_head = (git_dir / "FETCH_HEAD").read_text(encoding="utf-8")
        self.assertEqual(1, len(fetch_head.strip().splitlines()))

    def test_git_environment_never_prompts(self):
        with mock.patch.dict(os.environ, {"GCM_INTERACTIVE": "always", "GIT_TERMINAL_PROMPT": "1"}):
            env = agent_mailbox.git_environment()
        self.assertEqual("never", env["GCM_INTERACTIVE"])
        self.assertEqual("0", env["GIT_TERMINAL_PROMPT"])

    def test_terminal_receipt_requires_result(self):
        self.initialize()
        message_id = self.mailbox(
            self.dev,
            "send",
            "--to",
            "server",
            "--title",
            "验证",
            "--message",
            "完成后报告结果",
        ).stdout.strip()
        self.mailbox(self.server, "ack", message_id, "--status", "completed", expected=1)


if __name__ == "__main__":
    unittest.main()
