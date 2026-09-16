# TEMPLATES/ANDROID_DEVICE_STATE（项目本地实例：`/LAEP/STATE/ANDROID_DEVICE_STATE.md`）

```
Device Serial:
Model:
Android Version:
API Level:
ABI:
Resolution:
Density:
Authorization: authorized | unauthorized | offline | disconnected
Last Deployment:
Last Successful Build:
Package:
Launch Activity:
```

避免 AI 每轮重新"学习"设备信息；设备断开时把 `Authorization` 更新为
`disconnected`，不删除其余字段（重新连接后大概率还是同一台设备）。
