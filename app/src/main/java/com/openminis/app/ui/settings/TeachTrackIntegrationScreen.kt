package com.openminis.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.leslie.teachtrack.agent.TeachTrackAppContext
import com.leslie.teachtrack.agent.TeachTrackIntegrationSettings
import com.leslie.teachtrack.copilot.CopilotAuditEntry
import com.leslie.teachtrack.copilot.CopilotAuditStore
import com.leslie.teachtrack.data.workspace.WorkspaceHealthChecker
import com.leslie.teachtrack.data.workspace.WorkspaceManager
import com.openminis.app.data.repository.TEACHTRACK_SKILL_VERSION
import com.openminis.app.sandbox.NativeOffloadServer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Spec §47 "TeachTrack Integration" status page, hosted inside the OpenMinis
 * settings tree. Because `AgentHost` embeds the full `AppNavigation`, this
 * screen is reachable in the embedded Agent tab with no extra wiring — it
 * only touches application-scope state (`TeachTrackAppContext`,
 * SharedPreferences, [NativeOffloadServer]), never an Activity.
 */
@Composable
fun TeachTrackIntegrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val registry = remember(context) { TeachTrackAppContext.actionRegistry(context) }
    val auditStore = remember(context) { CopilotAuditStore(context) }
    var auditEntries by remember { mutableStateOf<List<CopilotAuditEntry>>(emptyList()) }
    var workspaceSummary by remember { mutableStateOf<String?>(null) }
    var accessMode by remember { mutableStateOf(TeachTrackIntegrationSettings.accessMode(context)) }

    LaunchedEffect(refreshKey) {
        auditEntries = withContext(Dispatchers.IO) { auditStore.list() }
        workspaceSummary = withContext(Dispatchers.IO) {
            val manager = WorkspaceManager(context)
            val storage = manager.storage()
            when {
                storage == null -> "未连接工作区"
                else -> runCatching { WorkspaceHealthChecker(manager).check(storage) }.fold(
                    onSuccess = { "${it.checkedFiles} 个文件、${it.records} 条记录、${it.issues.size} 个问题" },
                    onFailure = { "检查失败：${it.message ?: "未知错误"}" },
                )
            }
        }
    }

    SettingsScaffold(title = "TeachTrack Integration", onBack = onBack) {
        SettingsSection(header = "桥接状态") {
            val bridgeRegistered = NativeOffloadServer.registeredHandlers.contains("teachtrack")
            SettingsRow(
                icon = Icons.Outlined.Hub,
                iconColor = Color(0xFF007AFF),
                title = "Bridge handler",
                subtitle = if (bridgeRegistered) "teachtrack 已注册" else "teachtrack 未注册",
                showChevron = false,
            )
            SettingsRow(
                icon = Icons.Outlined.Hub,
                iconColor = Color(0xFF34C759),
                title = "Native offload server",
                subtitle = if (NativeOffloadServer.isRunning) {
                    "运行中（abstract socket '${NativeOffloadServer.socketName}'）"
                } else {
                    "未运行"
                },
                showChevron = false,
            )
            SettingsRow(
                title = "能力数量",
                subtitle = "${registry.allowedActions.size} 个动作",
                showChevron = false,
            )
            SettingsRow(
                title = "技能版本",
                subtitle = "teachtrack skill $TEACHTRACK_SKILL_VERSION",
                showChevron = false,
            )
            SettingsRow(
                title = "Workspace",
                subtitle = workspaceSummary ?: "检查中…",
                showChevron = false,
                showDivider = false,
            )
        }

        SettingsSection(
            header = "访问模式",
            footer = "标准模式下，Agent 的写操作会被拒绝并提示 CONFIRMATION_REQUIRED；只读与导航动作始终允许。",
        ) {
            SettingsRow(
                icon = Icons.Outlined.Shield,
                iconColor = Color(0xFF007AFF),
                title = "Owner Trusted（默认）",
                subtitle = "Agent 直接执行写操作（本机个人使用）",
                onClick = {
                    TeachTrackIntegrationSettings.setAccessMode(context, TeachTrackIntegrationSettings.MODE_OWNER_TRUSTED)
                    accessMode = TeachTrackIntegrationSettings.MODE_OWNER_TRUSTED
                },
                showChevron = false,
                trailing = {
                    RadioButton(
                        selected = accessMode == TeachTrackIntegrationSettings.MODE_OWNER_TRUSTED,
                        onClick = null,
                    )
                },
            )
            SettingsRow(
                icon = Icons.Outlined.Shield,
                iconColor = Color(0xFFFF9500),
                title = "标准模式",
                subtitle = "写操作需要在应用内确认",
                onClick = {
                    TeachTrackIntegrationSettings.setAccessMode(context, TeachTrackIntegrationSettings.MODE_STANDARD)
                    accessMode = TeachTrackIntegrationSettings.MODE_STANDARD
                },
                showChevron = false,
                showDivider = false,
                trailing = {
                    RadioButton(
                        selected = accessMode == TeachTrackIntegrationSettings.MODE_STANDARD,
                        onClick = null,
                    )
                },
            )
        }

        SettingsSection(
            header = "Agent 操作记录",
            footer = statusMessage ?: "仅可撤销支持回退的操作（待办状态、批量完成待办、新建学生）；其余操作不提供撤销。",
        ) {
            if (auditEntries.isEmpty()) {
                SettingsRow(
                    icon = Icons.Outlined.History,
                    iconColor = Color(0xFF8E8E93),
                    title = "暂无记录",
                    showChevron = false,
                    showDivider = false,
                )
            } else {
                val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
                val shown = auditEntries.take(20)
                shown.forEachIndexed { index, entry ->
                    SettingsRow(
                        icon = Icons.Outlined.History,
                        iconColor = if (entry.status == "SUCCESS") Color(0xFF34C759) else Color(0xFFFF3B30),
                        title = entry.action,
                        subtitle = "${timeFormat.format(Date(entry.timestamp))} · ${entry.status}",
                        showChevron = false,
                        showDivider = index < shown.lastIndex,
                        trailing = if (entry.undoToken != null && entry.status == "SUCCESS") {
                            {
                                TextButton(onClick = {
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { registry.undo(entry.undoToken) }
                                        statusMessage = result.message
                                        refreshKey++
                                    }
                                }) {
                                    Text("撤销", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
