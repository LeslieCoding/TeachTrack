package com.leslie.teachtrack.agent

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.openminis.app.MinisApp
import com.openminis.app.deeplink.DeepLinkAction
import com.openminis.app.service.SessionActivityTracker
import com.openminis.app.ui.navigation.AppNavigation
import com.openminis.app.ui.navigation.Routes
import com.openminis.app.ui.navigation.safeNavigate
import com.openminis.app.ui.settings.KEY_FONT_APP_BASE
import com.openminis.app.ui.settings.KEY_THEME_MODE
import com.openminis.app.ui.settings.fontScaleForLevel
import com.openminis.app.ui.settings.getAppearancePrefs
import com.openminis.app.ui.theme.MinisTheme
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The Agent tab's full OpenMinis host (spec §83/84; successor to the deleted
 * `AgentSplitPanel`, which embedded only a bare `ChatScreen` and made the
 * Agent tab feel like a second app bolted on).
 *
 * Embeds the COMPLETE OpenMinis UI — the same `MinisTheme { AppNavigation +
 * ConfigConfirmDialogHost }` tree `com.openminis.app.MainActivity` renders —
 * inside TeachTrack's own Activity, above TeachTrack's bottom nav. Session
 * list, chat, settings, skills and terminal are all reachable without ever
 * leaving the tab, so there is exactly one task in recents.
 *
 * Repositories come from the `MinisApp` application singleton. Only
 * `chatRepositoryOrNull`/`providerRepositoryOrNull` have null-safe variants;
 * the other four are `lateinit`. Rather than touch each one blindly we gate
 * on `MinisApp.subsystemsReady()` — the same flag the OrNull accessors check
 * — and poll briefly, since `Application.onCreate` init normally finishes
 * long before the user reaches this tab (a spinner only shows under
 * safe-mode / failed init, mirroring `AgentSplitPanel`'s fallback).
 *
 * [openNewChatRequests] is a monotonic counter: each increment navigates the
 * embedded NavController to a fresh chat (`__new__<uuid>`, same route shape
 * as `MainActivity.onStart`'s Launch-Session mode-2 handling). Used by
 * TeachTrack's "Ask Agent" action to land the user on a new conversation
 * whose composer `ChatScreen` pre-fills from
 * `DeepLinkCoordinator.consumePendingChatDraft()`.
 */
@Composable
fun AgentHost(
    modifier: Modifier = Modifier,
    initialDeepLink: DeepLinkAction? = null,
    openNewChatRequests: StateFlow<Int>? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? MinisApp

    val ready by produceState(initialValue = app?.subsystemsReady() == true, app) {
        while (!value) {
            delay(300)
            value = app?.subsystemsReady() == true
        }
    }

    Box(modifier.fillMaxSize()) {
        if (!ready || app == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Box
        }

        // Same appearance-prefs wiring as OpenMinis MainActivity.setContent:
        // re-read theme mode and app font scale whenever they change so the
        // embedded UI stays in sync with its own Settings screen.
        val prefs = remember { getAppearancePrefs(context) }
        var themeMode by remember { mutableIntStateOf(prefs.getInt(KEY_THEME_MODE, 0)) }
        var appBaseLevel by remember { mutableIntStateOf(prefs.getInt(KEY_FONT_APP_BASE, 0)) }
        DisposableEffect(prefs) {
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                when (key) {
                    KEY_THEME_MODE -> themeMode = sp.getInt(KEY_THEME_MODE, 0)
                    KEY_FONT_APP_BASE -> appBaseLevel = sp.getInt(KEY_FONT_APP_BASE, 0)
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        val darkTheme = when (themeMode) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }

        MinisTheme(darkTheme = darkTheme, fontScale = fontScaleForLevel(appBaseLevel)) {
            val navController = rememberNavController()

            // SessionActivityTracker presence hook, ported from OpenMinis
            // MainActivity (T166): the foreground-service keep-alive must run
            // the whole time the user is inside a chat, or the process drops
            // to adj=700 on Home and gets reclaimed mid-task. The Activity
            // field `currentChatSessionId` becomes composable state here;
            // presence is also cleared on dispose (the Activity-level
            // onDestroy clear doesn't exist for an embedded host).
            val scope = rememberCoroutineScope()
            val currentChatSessionId = remember { mutableStateOf<String?>(null) }
            DisposableEffect(navController) {
                val job = scope.launch {
                    navController.currentBackStackEntryFlow.collect { entry ->
                        val isChatRoute = entry.destination.route == Routes.CHAT
                        val sid = entry.arguments?.getString("sessionId").takeIf { isChatRoute }
                        val previous = currentChatSessionId.value
                        if (sid != previous) {
                            previous?.let { SessionActivityTracker.setAbsent(it) }
                            sid?.let { SessionActivityTracker.setPresent(it) }
                            currentChatSessionId.value = sid
                        }
                    }
                }
                onDispose {
                    job.cancel()
                    currentChatSessionId.value?.let { SessionActivityTracker.setAbsent(it) }
                    currentChatSessionId.value = null
                }
            }

            // "Ask Agent" handoff: each counter bump opens a fresh chat above
            // the session list. The seed value is consumed without navigating
            // so merely re-composing the tab never pushes a route.
            if (openNewChatRequests != null) {
                DisposableEffect(openNewChatRequests, navController) {
                    var seen = openNewChatRequests.value
                    val job = scope.launch {
                        openNewChatRequests.collect { count ->
                            if (count != seen) {
                                seen = count
                                navController.safeNavigate(Routes.chat("__new__${UUID.randomUUID()}")) {
                                    popUpTo(Routes.SESSION_LIST) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                    onDispose { job.cancel() }
                }
            }

            AppNavigation(
                chatRepository = app.chatRepository,
                providerRepository = app.providerRepository,
                envVarRepository = app.envVarRepository,
                skillRepository = app.skillRepository,
                mcpRepository = app.mcpRepository,
                memoryRepository = app.memoryRepository,
                navController = navController,
                initialDeepLink = initialDeepLink,
                // This host sits inside TeachTrack's bottom-nav Scaffold,
                // which already reserves ime space — see ChatScreen's KDoc on
                // handlesOwnImePadding (T-teachtrack-agent-ime-double-padding).
                chatHandlesOwnImePadding = false,
            )

            // Root-level minis-config confirm dialog, same as OpenMinis
            // MainActivity — renders on top of any active screen.
            com.openminis.app.ui.settings.ConfigConfirmDialogHost()
        }
    }
}
