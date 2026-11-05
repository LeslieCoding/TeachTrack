# GOVERNANCE/05 — Release Governance（版本治理）

负责：Version、Build、Release Candidate、Stable、Rollback、Known Good
Build。S1/S2 可以用最简形式（"测试通过就发布"）；S3+ 按下面的正式流程走。

## Release Candidate（RC）

发布前建立 RC。RC 阶段原则上停止新增功能，只允许 Bug Fix / Security Fix /
Blocking Issue Fix。

## Stable

只有通过 Release Test、Regression、Security、Upgrade/Migration 测试，才能
定义为 Stable。

## Maintenance Mode

进入 Stable 后转入维护模式，重点处理 Bug、安全、兼容性、性能，不再常规
加新功能——新功能回到下一个版本的 Scope Governance 流程重新走一遍。

## Known Good Build（S4+）

维护记录：Commit、Build、Version、测试结果、平台/设备、日期。任何严重
回归必须能回退到这里，回退路径提前设计好（`GOVERNANCE/03` 的 Rollback
Strategy）。

## Checkpoint（S4+）

开发过程中自动建立里程碑式 Checkpoint（如
`checkpoint-feature-complete`、`checkpoint-build-success`、
`checkpoint-device-boot`、`checkpoint-regression-pass`），禁止连续进行
大量高风险修改却没有一个可以退回去的稳定点。展开见
`AUTOMATION/01_CHECKPOINT_PROTOCOL.md`。
