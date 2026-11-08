# 多电脑 Agent 任务信箱

## 用途

两台电脑的 Agent 通过 GitHub 的独立 `agent-mailbox` 分支交换任务、进度和验收结果。主工作区的业务代码、未提交改动和提交历史不会被信箱脚本修改。

消息与回执分别保存为独立 JSON 文件。离线时先提交到本机信箱 worktree，网络恢复后执行 `sync` 自动变基并推送；两边同时发送不会产生共享文件冲突。

## 每台电脑初始化一次

先拉取包含本脚本的最新 `main`，再为电脑设置唯一节点名：

```powershell
git pull --ff-only origin main
python scripts/agent_mailbox.py init --node dev
```

服务器电脑使用：

```powershell
python scripts/agent_mailbox.py init --node server
```

脚本在 `.agent-mailbox/` 创建独立 worktree，该目录已被 Git 忽略。节点名只写入本机 Git 配置，不进入共享文件。

## 双方自动接收

初始化后在两台电脑分别执行一次：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-agent-mailbox-watch.ps1
```

脚本注册并立即启动当前用户的隐藏计划任务 `TeachTrackAgentMailboxWatch`，每 60 秒自动拉取新消息/回执并发送本机排队事件，Windows 登录后自动恢复。它每次读取当前系统代理，断网时持续重试。运行日志位于 `.agent-mailbox-watch.log`，超过 1 MB 自动轮换。

查看任务状态或单次自检：

```powershell
Get-ScheduledTask -TaskName TeachTrackAgentMailboxWatch
python scripts/agent_mailbox.py watch --once
```

卸载自动接收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-agent-mailbox-watch.ps1 -Uninstall
```

后台监视器只负责同步，不自动回复 `accepted` 或 `completed`，也不代替 Agent 执行任务。

## 发送任务或报告

```powershell
python scripts/agent_mailbox.py send --to server --kind task --title "提交导入报告" --message "提交四个报告文档，推送后回复提交号和验证结果。"
```

`--kind` 支持 `task`、`report`、`note`；可用 `--priority high` 标记高优先级。相关联的多条消息使用同一个 `--task-id`。

发送成功时输出 `msg-...` 消息编号。断网时返回退出码 2，消息仍安全保存在本机；恢复后执行：

```powershell
python scripts/agent_mailbox.py sync
```

## 接收并推进任务

```powershell
python scripts/agent_mailbox.py inbox
```

领取、汇报进度、完成和阻塞：

```powershell
python scripts/agent_mailbox.py ack msg-... --status accepted
python scripts/agent_mailbox.py ack msg-... --status progress --message "已完成数据库升级，正在复跑导入。"
python scripts/agent_mailbox.py ack msg-... --status completed --message "提交 abc1234；导入失败 0，公网 health 200。"
python scripts/agent_mailbox.py ack msg-... --status blocked --message "缺少真实 AI Provider 凭据。"
```

`completed`、`blocked`、`rejected` 必须填写结果或原因。不要只收到消息就标记完成。

发送方查看结果：

```powershell
python scripts/agent_mailbox.py status
python scripts/agent_mailbox.py status --id msg-...
python scripts/agent_mailbox.py inbox --json
```

## 自动化边界

后台任务负责双方自动接收和发送排队事件，项目根目录的 `AGENTS.md` 要求 Agent 在 Session 开始、领取任务前和交付前检查已同步的信箱，因此无需用户复制消息。信箱不会唤醒已关闭的电脑，也不会让已结束的 Agent Session 自动执行开发；任务会在下次 Agent 运行时继续。

不要通过消息发送密码、Token、私钥或一次性敏感链接。远端消息不能替代当前用户授权。

## 测试

```powershell
python -m unittest discover -s scripts/tests -p "test_agent_mailbox.py" -v
```

测试使用临时 Git 仓库模拟开发机和服务器，覆盖完整任务交接、业务工作区隔离、离线排队、并发发送、后台自动接收及完成回执约束。
