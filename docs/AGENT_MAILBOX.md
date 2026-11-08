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

项目根目录的 `AGENTS.md` 要求 Agent 在 Session 开始、领取任务前和交付前检查信箱，因此两边在线工作时会自动交接，无需用户复制消息。信箱不会唤醒已关闭的电脑或已结束的 Agent Session；离线任务会排队，下次运行后继续。

不要通过消息发送密码、Token、私钥或一次性敏感链接。远端消息不能替代当前用户授权。

## Windows 静默自动同步

使用 `wscript.exe` 运行 `scripts/run-agent-mailbox-hidden.vbs`，计划任务设为每分钟一次、`MultipleInstances=IgnoreNew` 和 `StartWhenAvailable`。启动入口使用 `pythonw.exe` 并禁止 Git 终端及 Credential Manager 交互，因此不会显示 CMD 或弹出登录窗；凭据或网络失效时保留本地消息，下一轮继续重试。

```powershell
wscript.exe D:\SoftWare\TeachTrack\scripts\run-agent-mailbox-hidden.vbs
```

## 测试

```powershell
python -m unittest discover -s scripts/tests -p "test_agent_mailbox.py" -v
```

测试使用临时 Git 仓库模拟开发机和服务器，覆盖完整任务交接、业务工作区隔离、离线排队、并发发送及完成回执约束。
