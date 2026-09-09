package com.leslie.teachtrack.data.workspace

import android.content.Context
import android.os.Build
import com.leslie.teachtrack.core.workspace.WorkspaceDevice
import java.util.UUID
import java.time.Instant
import org.json.JSONObject

class DeviceIdentity(private val context: Context) {
    private val prefs = context.getSharedPreferences("teachtrack_device", Context.MODE_PRIVATE)
    val deviceId: String = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }
    fun descriptor(): WorkspaceDevice {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
        return WorkspaceDevice(deviceId, "${Build.MANUFACTURER} ${Build.MODEL}", "ANDROID", version, Instant.now().toString())
    }
    fun json(): JSONObject = JSONObject().put("deviceId", descriptor().deviceId).put("deviceName", descriptor().deviceName).put("platform", descriptor().platform).put("appVersion", descriptor().appVersion).put("lastSeenAt", descriptor().lastSeenAt)
}
