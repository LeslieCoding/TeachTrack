# AUTOMATION/03 — Git Transaction Protocol

每一次有意义的改动都当作一次事务处理，而不是攒一堆修改最后才第一次测试：

```
Known Good State → 小改动 → build → test → PASS → commit
                                        ↓ FAIL
                                    rollback，不要在失败状态上继续叠加
```

一个 commit 尽量对应一个逻辑改动（"原子变更集"），message 建议包含：
Task ID、关联的 Requirement ID、跑过的测试、风险等级，例如：

```
Fix settings text clipping (TASK-0142, REQ-023, R1)

Tests: manual at 1024x768/1280x720/1920x1080 — OBSERVED PASS
Regression: buttons/navigation/theme — OBSERVED PASS
```

好处：出问题时 `git bisect` 才有意义，也让 Stagnation Detection
（`CORE/06`）能清楚看出"最后一个已知良好状态"具体是哪一个 commit。
禁止在没有中间测试的情况下连续改几十个文件才第一次跑构建。
