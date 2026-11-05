# TeachTrack 范围与边界

## 当前交付范围

Android 本地优先应用，使用 Kotlin、Jetpack Compose、Material 3、Room、SQLite、Coroutines、Flow、MVVM/Repository。范围覆盖 P0 需求和已实现的 P1 模板、附件、标签、CSV 能力。

## 数据边界

学生、家长联系方式、成绩、课程、作业、知识点、沟通和计划默认只进入本地数据库。删除优先归档或软删除；备份通过 SAF 输出，未经用户操作不上传网络。

## 设备边界

当前真机验收只使用 OnePlus `92f73a16`。OPPO Pad `2d64f765` 仅为历史证据，不再操作，直到用户再次切换设备。

## 明确不纳入当前完成声明

真实云厂商密钥、云端同步、Release 签名、完整语音转写、完整 AI Action Registry 语义、PDF/Excel 导出以及未经用户提供配置的网络 Provider。
