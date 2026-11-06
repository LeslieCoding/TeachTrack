# Cloudflare Tunnel（永久 HTTPS）

Quick Tunnel 已验证可用，但 URL 每次重启都会变，且没有 Cloudflare Access。永久使用需要 Cloudflare 账号 + 一个已接入 Cloudflare 的域名。

## 1. 登录

```bash
cloudflared tunnel login
```

浏览器选择域名并授权。一次即可。

## 2. 创建 tunnel

```bash
cloudflared tunnel create teachtrack
```

记下输出的 Tunnel ID。

## 3. 配置

`%USERPROFILE%\.cloudflared\config.yml`：

```yaml
tunnel: <TUNNEL_ID>
credentials-file: C:\Users\<你>\.cloudflared\<TUNNEL_ID>.json

ingress:
  - hostname: teachtrack.example.com
    service: http://127.0.0.1:8080
  - service: http_status:404
```

## 4. DNS

```bash
cloudflared tunnel route dns teachtrack teachtrack.example.com
```

## 5. 启动

```bash
cloudflared tunnel run teachtrack
```

Windows 服务：

```powershell
cloudflared service install
```

## 6. 安全

- 不要把 8080 直接映射到公网，Tunnel 是出站连接，不需要开放入站端口。
- 后端 `/api/v1/**` 仍要求 Bearer Token；`/actuator/health` 是公开的。
- 建议在 Cloudflare Zero Trust 里给 `teachtrack.example.com` 加 Access 策略（邮箱/OTP），这样连页面都先登录。
- 定期轮换 API Token；不要把 Token 写进 APK 或仓库。
- Quick Tunnel 仅用于测试；永久入口用 Named Tunnel。

## 当前状态

- `cloudflared 2026.10.7` 已安装在 `D:\SoftWare\cloudflared.exe`，已登记工具清单。
- Quick Tunnel 实测：HTTPS health 200、API 200、MCP 11 tools；测试后已停止。
- Named Tunnel 需要你的 Cloudflare 账号和域名授权，尚未配置。

## 本机已完成的 Named Tunnel（2026-11-05）

```text
hostname: teachtrack.leslieholmes.ccwu.cc
tunnel:   teachtrack (a1a25351-0179-436a-aadc-675d9d0c7356)
config:   C:\Users\Leslie\.cloudflared\config.yml
startup:  hidden scheduled task TeachTrackTunnel (AtLogOn)
```

验证：health 200、API 200（新 Token），旧 Token 401。
Token 明文只在 `backend/.cloud-token`，已 gitignore；Cloudflare Access 尚未启用。
