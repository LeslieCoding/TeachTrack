# TeachTrack 持续加固待办

建立日期：2026-09-10

## 目标

持续提升 TeachTrack 的架构、性能、稳定性与可验证性。所有任务必须保持 Local First、数据不丢失、非破坏性迁移，并在一加手机重新连接后完成物理回归。

## 待办顺序

- [x] T3-HARDEN-002：为 Dashboard 增加数据库侧限量查询/投影，避免首页订阅完整历史集合。
- [x] T3-HARDEN-003：已覆盖当前 Repository 业务写入的自动 SyncEvent 生成；统一 revision 仍列为后续增强。
- [ ] T3-HARDEN-004：模拟 Device A/Device B 并发修改，验证冲突检测、冲突文件和三种解决方式。
- [ ] T3-HARDEN-005：完成 Workspace 导出、删除本地库、恢复 `.ttbackup` 的完整往返测试，并核对数量与 ID。
- [ ] T3-HARDEN-006：补充 JSONL 损坏行、重复事件、Tombstone、孤儿引用、附件哈希和迁移一致性测试。

## 执行规则

1. 每个任务先检查现有实现，再做最小可回滚修改。
2. 不把共享 Workspace 的 SQLite 数据库作为同步文件。
3. 不把历史真实学生数据硬编码进 APK。
4. 失败时保留复现命令、错误、修复和遗留风险到 `logs/`。
5. 每轮完成后运行构建、单元测试；设备恢复后只使用 OnePlus `92f73a16` 做真机测试。
