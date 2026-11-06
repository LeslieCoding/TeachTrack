# RESUME_INSTRUCTIONS

中断后按本文件恢复，无需用户重新解释。

## 1. 读取顺序

1. `DEVELOPMENT_PROGRESS.md`
2. `IMPLEMENTATION_CHECKLIST.md`
3. `BLOCKERS.md`
4. `TEST_RESULTS.md`
5. `docs/ai-practice/CURRENT_ARCHITECTURE.md`

## 2. 环境

| 项 | 值 |
|---|---|
| 项目 | `D:\SoftWare\TeachTrack` |
| Android 构建 JDK | `D:\SoftWare\jdk-17` |
| 后端 JDK | `D:\SoftWare\jdk-21` |
| Android SDK | `D:\SoftWare\android-sdk` |
| PostgreSQL（开发） | `D:\SoftWare\pgsql-16`，端口 55432，库 `teachtrack` |
| 后端端口 | 8080 |

## 3. 常用命令

```bash
# Android 测试
cd /d/SoftWare/TeachTrack
JAVA_HOME='D:\SoftWare\jdk-17' ./gradlew.bat :app:testDebugUnitTest

# 后端构建
JAVA_HOME='D:\SoftWare\jdk-21' ./gradlew.bat -p backend build

# 启动 PostgreSQL（便携版）
D:/SoftWare/pgsql-16/bin/pg_ctl.exe -D D:/SoftWare/pgdata-teachtrack -o "-p 55432" -l D:/SoftWare/pgdata-teachtrack/server.log start

# 启动后端（dev）
JAVA_HOME='D:\SoftWare\jdk-21' ./gradlew.bat -p backend bootRun --args='--spring.profiles.active=dev'

# 健康检查
curl -s http://127.0.0.1:8080/actuator/health
```

## 4. 当前断点

- M01 已验收。
- M02 进行中：PostgreSQL 便携版下载/初始化 → backend 骨架 → Flyway → 存储 → 认证 → 测试。

## 5. 不可违反

- 不改历史学生 ID；不清库；不伪造迁移条数。
- 没有 `.ttseed.json` 就不做“14 人 / 91 条”验收。
- 密钥只走环境变量。
- M02 完成前不改 Android 业务代码。
