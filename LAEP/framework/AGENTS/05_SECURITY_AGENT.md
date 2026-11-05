# AGENTS/05 — Security Agent

负责 `SECURITY/` 全部内容：权限最小化、Secrets、加密、输入校验、依赖漏洞、
认证授权、沙箱、安全存储、危险删除操作的风险评估。安全不是项目最后一步，
必须贯穿整个开发过程——每个 Development Agent 的改动如果涉及权限/数据/
外部输入，Security Agent（或扮演该角色的同一个 Agent）都要过一遍
`SECURITY/01_SECURITY_GOVERNANCE.md` 的清单。
