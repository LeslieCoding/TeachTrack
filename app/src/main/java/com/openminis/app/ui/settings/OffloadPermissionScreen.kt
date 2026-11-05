package com.openminis.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leslie.teachtrack.R
import com.openminis.app.logging.AppLogger
import com.openminis.app.offload.OffloadPermissionManager
import com.openminis.app.ui.components.MinisMenu
import com.openminis.app.ui.components.MinisTextButton

@Composable
fun OffloadPermissionScreen(
    onBack: () -> Unit,
    // [T-android-privileged-backend] Navigate to the multi-backend
    // (Shizuku + AXManager) screen from the privileged-backend integration row.
    onOpenPrivilegedBackend: () -> Unit = {},
) {
    val grouped = OffloadPermissionManager.toolRegistry
        .filter { it.showInSettings }
        .groupBy { it.category }
    // T336: Integrations category gets dedicated handcrafted SectionCards
    // (one per CLI) with system-layer state + action rows. Skip it from
    // the auto-rendered loop below.
    val autoCategories = grouped.entries
        .filter { it.key != OffloadPermissionManager.PermissionCategory.INTEGRATIONS }

    var showResetConfirm by remember { mutableStateOf(false) }

    val configEnabled by com.openminis.app.config.MinisConfigPermissionStore.enabled.collectAsState()

    SettingsScaffold(
        title = stringResource(R.string.perm_title),
        onBack = onBack,
        actions = {
            MinisTextButton(onClick = { showResetConfirm = true }) {
                Text(stringResource(R.string.perm_reset_all))
            }
        },
    ) {
        // T-config: master switch for the minis-config CLI surface.
        SettingsSection(
            header = stringResource(R.string.perm_section_config_tool),
            footer = stringResource(R.string.perm_minis_config_desc),
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.perm_allow_minis_config),
                checked = configEnabled,
                onCheckedChange = {
                    com.openminis.app.config.MinisConfigPermissionStore.setEnabled(it)
                },
                showDivider = false,
            )
        }

        autoCategories.forEach { (category, tools) ->
            SettingsSection(header = stringResource(categoryHeaderRes(category))) {
                tools.forEachIndexed { idx, tool ->
                    PermissionRow(
                        tool = tool,
                        showDivider = idx < tools.size - 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.perm_reset_confirm_title)) },
            text = { Text(stringResource(R.string.perm_reset_confirm_text)) },
            confirmButton = {
                MinisTextButton(onClick = {
                    OffloadPermissionManager.resetAll()
                    com.openminis.app.config.MinisConfigPermissionStore.setEnabled(true)
                    AppLogger.info("PermissionsScreen", "user confirmed Reset All — all tool permissions cleared, minis-config switch reset to default")
                    showResetConfirm = false
                }) {
                    Text(stringResource(R.string.perm_reset_confirm))
                }
            },
            dismissButton = {
                MinisTextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * T336: composite SectionCard for a single integration CLI. Layout:
 *
 *   [icon]  CLI title
 *           CLI description (one-liner)
 *   ─────────
 *   Agent permission        [tri-state ▾]
 *   ─────────
 *   System authorization   [status text in color]
 *   ─────────
 *   (only when systemReady=false): [Open system settings →]
 *
 * The system-status row is read-only — the user goes to the OS settings
 * page to flip it. systemReady drives both the status row's color/icon
 * and the visibility of the action row below.
 */
@Composable
private fun IntegrationSection(
    iconVector: ImageVector,
    iconTint: Color,
    sectionHeaderRes: Int,
    sectionFooterRes: Int,
    toolName: String,
    descriptionRes: Int,
    systemReady: Boolean,
    systemStatusTitleRes: Int,
    systemActionTitleRes: Int,
    onSystemAction: () -> Unit,
    // [T-android-privileged-backend] When set, the system-status row becomes a
    // chevron-clickable entry to a dedicated detail screen (the multi-backend
    // Shizuku/AXManager page) — shown REGARDLESS of systemReady, so the user can
    // open it even when the backend is already authorized. Null keeps the
    // original read-only status row + conditional action behavior (a11y row).
    onStatusRowClick: (() -> Unit)? = null,
) {
    SettingsSection(
        header = stringResource(sectionHeaderRes),
        footer = stringResource(sectionFooterRes),
    ) {
        // Header row: CLI title + description, with a colored leading icon.
        SettingsRow(
            icon = iconVector,
            iconColor = iconTint,
            title = stringResource(toolTitleRes(toolName)),
            subtitle = stringResource(descriptionRes),
            showChevron = false,
        )

        // Agent tri-state policy.
        AgentPolicyRow(toolName = toolName, showDivider = true)

        // System-layer status. When [onStatusRowClick] is provided the row is a
        // navigation entry (chevron, always shown); otherwise it's read-only.
        SettingsRow(
            title = stringResource(R.string.perm_system_authorization),
            onClick = onStatusRowClick,
            showChevron = onStatusRowClick != null,
            showDivider = onStatusRowClick == null && !systemReady,
            trailing = {
                Text(
                    text = stringResource(systemStatusTitleRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (systemReady) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                )
            },
        )

        // Conditional action row: only for the read-only (a11y) variant, and
        // only when the system layer needs attention. The privileged-backend
        // variant routes everything through [onStatusRowClick] above instead.
        if (onStatusRowClick == null && !systemReady) {
            SettingsRow(
                title = stringResource(systemActionTitleRes),
                onClick = onSystemAction,
                showDivider = false,
            )
        }
    }
}

/**
 * Shared tri-state row. Reused by the dedicated IntegrationSection cards
 * AND by the auto-rendered category loop below — same dropdown menu,
 * same chip in trailing.
 */
@Composable
private fun AgentPolicyRow(
    toolName: String,
    showDivider: Boolean,
) {
    var currentLevel by remember { mutableStateOf(OffloadPermissionManager.getLevel(toolName)) }
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsRow(
            title = stringResource(R.string.perm_agent_policy),
            onClick = { expanded = true },
            // [T-android-perm-row-affordance] This row OPENS A DROPDOWN, but with
            // showChevron=false it looked like a read-only status line — nothing
            // hinted it was tappable. It also broke alignment with the sibling
            // "system authorization" row: a chevron costs 4dp spacer + 20dp icon,
            // so that row's value text sits 24dp further left and the two values
            // visibly failed to line up inside the same card.
            // Showing the chevron fixes both at once.
            showChevron = true,
            showDivider = showDivider,
            trailing = {
                Text(
                    text = levelDisplayName(currentLevel),
                    style = MaterialTheme.typography.labelLarge,
                    color = levelColor(currentLevel),
                )
            },
        )
        // T336-followup: anchor the menu's right edge to the row's right
        // edge so it grows down-and-left from the trailing chip instead
        // of Material3's default down-and-right (which on a narrow phone
        // pushed the menu off the screen edge).
        MinisMenu(expanded = expanded, onDismissRequest = { expanded = false }, alignEnd = true) {
            for (level in OffloadPermissionManager.PermissionLevel.entries) {
                DropdownMenuItem(
                    text = {
                        Text(
                            levelDisplayName(level),
                            color = levelColor(level),
                        )
                    },
                    onClick = {
                        currentLevel = level
                        OffloadPermissionManager.setLevel(toolName, level)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    tool: OffloadPermissionManager.ToolPermissionInfo,
    showDivider: Boolean,
) {
    var currentLevel by remember { mutableStateOf(OffloadPermissionManager.getLevel(tool.toolName)) }
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsRow(
            title = toolTitle(tool),
            subtitle = tool.toolName,
            onClick = { expanded = true },
            // [T-android-perm-row-affordance] Same dropdown affordance as
            // AgentPolicyRow — this row opens the same tri-state menu, so it gets
            // the same chevron. Keeping the two in sync also keeps every value in
            // the Privacy list on one right edge.
            showChevron = true,
            showDivider = showDivider,
            trailing = {
                Text(
                    text = levelDisplayName(currentLevel),
                    style = MaterialTheme.typography.labelLarge,
                    color = levelColor(currentLevel),
                )
            },
        )
        // T336-followup: anchor the menu's right edge to the row's right
        // edge so it grows down-and-left from the trailing chip instead
        // of Material3's default down-and-right (which on a narrow phone
        // pushed the menu off the screen edge).
        MinisMenu(expanded = expanded, onDismissRequest = { expanded = false }, alignEnd = true) {
            for (level in OffloadPermissionManager.PermissionLevel.entries) {
                DropdownMenuItem(
                    text = {
                        Text(
                            levelDisplayName(level),
                            color = levelColor(level),
                        )
                    },
                    onClick = {
                        currentLevel = level
                        OffloadPermissionManager.setLevel(tool.toolName, level)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun categoryHeaderRes(category: OffloadPermissionManager.PermissionCategory): Int = when (category) {
    OffloadPermissionManager.PermissionCategory.PRIVACY -> R.string.perm_section_privacy
    OffloadPermissionManager.PermissionCategory.MEDIA -> R.string.perm_section_media
    OffloadPermissionManager.PermissionCategory.SYSTEM -> R.string.perm_section_system
    // INTEGRATIONS is rendered by IntegrationSection above; this branch is
    // unreachable through the auto-loop but kept exhaustive for `when`
    // exhaustiveness. Reuse the a11y header as a harmless fallback.
    OffloadPermissionManager.PermissionCategory.INTEGRATIONS -> R.string.perm_section_a11y
}

@Composable
private fun toolTitle(tool: OffloadPermissionManager.ToolPermissionInfo): String {
    val res = toolTitleRes(tool.toolName)
    if (res == 0) return tool.displayName
    return stringResource(res)
}

private fun toolTitleRes(toolName: String): Int = when (toolName) {
    "calendar" -> R.string.perm_tool_calendar
    "location" -> R.string.perm_tool_location
    "clipboard" -> R.string.perm_tool_clipboard
    "contacts" -> R.string.perm_tool_contacts
    "photos" -> R.string.perm_tool_photos
    else -> 0
}

@Composable
private fun levelDisplayName(level: OffloadPermissionManager.PermissionLevel): String = stringResource(
    when (level) {
        OffloadPermissionManager.PermissionLevel.BYPASS -> R.string.perm_level_bypass
        OffloadPermissionManager.PermissionLevel.ASK_ONCE -> R.string.perm_level_ask_once
        OffloadPermissionManager.PermissionLevel.NOT_ALLOWED -> R.string.perm_level_not_allowed
    },
)

@Composable
private fun levelColor(level: OffloadPermissionManager.PermissionLevel): Color = when (level) {
    OffloadPermissionManager.PermissionLevel.BYPASS -> MaterialTheme.colorScheme.primary
    OffloadPermissionManager.PermissionLevel.ASK_ONCE -> MaterialTheme.colorScheme.tertiary
    OffloadPermissionManager.PermissionLevel.NOT_ALLOWED -> MaterialTheme.colorScheme.error
}

