# Oracle Always Free 部署

## 现状

- 本机没有 Oracle 账号配置、VM 或 SSH 主机。
- 已生成专用 SSH 密钥：
  - 私钥：`D:\SoftWare\oracle\ssh\teachtrack_ed25519`
  - 公钥：`D:\SoftWare\oracle\ssh\teachtrack_ed25519.pub`
- 需要先在 Oracle 控制台创建 VM，才能继续迁库和部署。

## 1. 创建 Oracle 账号

1. `https://www.oracle.com/cloud/free/`
2. 注册 Always Free；需要邮箱、手机号、信用卡/借记卡做身份验证（不扣费，通常有 1 美元左右临时授权）。
3. Home Region 选 **Singapore** 或 **Japan East (Tokyo)**，国内延迟相对低。

## 2. 创建 VM

Oracle 控制台 → Compute → Instances → Create instance：

| 项 | 值 |
|---|---|
| Name | `teachtrack` |
| Image | Ubuntu 24.04 (aarch64) |
| Shape | `VM.Standard.A1.Flex` |
| OCPU / Memory | **2 OCPU / 12 GB**（Always Free 上限） |
| Boot volume | 50–100 GB（总块存储上限 200 GB） |
| Public IPv4 | 分配 |
| SSH key | 粘贴 `teachtrack_ed25519.pub` 内容 |

公钥内容：

```text
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIDo1LdWqgCL0p9xPfIUyAeZyrG+aBNRoCMfuQn1IJNne teachtrack-oracle
```

如果提示 `Out of host capacity`，换 Availability Domain 或稍后重试；Always Free ARM 容量经常紧张。

## 3. 网络

只保留 SSH 22：

- 不需要开放 8080、5432、80、443。
- Cloudflare Tunnel 是出站连接，不需要入站端口。
- 不要给 PostgreSQL 配公网访问。

## 4. 交给我的信息

只需要：

```text
VM 公网 IP
SSH 用户名（Ubuntu 镜像默认 ubuntu）
```

私钥已在本机，不需要再发密码。

## 5. 我会执行的部署

1. 安装 OpenJDK 21、PostgreSQL 16、Python3/pip、bypy、cloudflared。
2. 创建 `teachtrack` 数据库与用户。
3. 上传并用 `pg_restore` 恢复本地 `teachtrack.dump`。
4. 上传后端 jar，配置 `/etc/teachtrack/env`。
5. 创建 `teachtrack-backend` systemd 服务（`Restart=always`）。
6. 把 `cloudflared` 配置/证书/隧道凭据搬到 VM，运行 connector。
7. 停掉本机 `TeachTrackBackend` / `TeachTrackTunnel`。
8. 验证 `https://teachtrack.leslieholmes.ccwu.cc/actuator/health` 200。
9. 配置每日 `pg_dump` 到百度网盘，并做一次恢复演练。

## 6. 成本与限制

- Always Free：2 OCPU / 12 GB / 200 GB 块存储，长期免费。
- 账号 30 天闲置可能被回收；建议每月至少登录/使用一次。
- 2 GB 内存不够，必须用 12 GB ARM 配置。
- 国内直连 Oracle 新加坡/东京可能波动；Cloudflare Tunnel 可缓解但不保证低延迟。
