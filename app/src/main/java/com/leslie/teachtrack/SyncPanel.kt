package com.leslie.teachtrack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leslie.teachtrack.agent.TeachTrackAppContext
import com.leslie.teachtrack.data.CloudPullService
import com.leslie.teachtrack.sync.SyncScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SyncGreen = Color(0xFF176B57)
private val SyncMint = Color(0xFFDDF5EA)
private val SyncInk = Color(0xFF15312A)
private val SyncBackground = Color(0xFFF4F9F6)

@Composable
fun SyncPanel(baseUrl: String, token: String) {
    val context = LocalContext.current
    val database = remember { TeachTrackAppContext.database(context) }
    val syncDao = remember { database.syncDao() }
    val dao = remember { database.dao() }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableIntStateOf(0) }
    var conflicts by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var pulling by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        pending = syncDao.pendingCount()
        conflicts = syncDao.openConflictCount()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        Modifier.fillMaxSize().background(SyncBackground).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(SyncMint, RoundedCornerShape(12.dp)),
                Alignment.Center
            ) { Icon(Icons.Rounded.CloudSync, null, tint = SyncGreen) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("云端同步", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SyncInk)
                Text("Room 本地写入 → outbox → WorkManager 推送", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                scope.launch {
                    loading = true
                    refresh()
                    loading = false
                }
            }) { Icon(Icons.Rounded.Refresh, "刷新", tint = SyncGreen) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SyncMetric("待同步", pending.toString(), Modifier.weight(1f))
            SyncMetric("未解决冲突", conflicts.toString(), Modifier.weight(1f))
        }

        Button(
            onClick = {
                SyncScheduler.schedule(context)
                message = "已排队；网络可用时自动同步"
                scope.launch {
                    delay(1500)
                    refresh()
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("立即同步")
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, fontSize = 12.sp, color = SyncGreen) }

        OutlinedButton(
            onClick = {
                pulling = true
                message = null
                scope.launch {
                    runCatching {
                        CloudPullService(context, baseUrl, token, dao).pullAll()
                    }.onSuccess { result ->
                        message = "已拉取学生 ${result.students}、课堂 ${result.sessions}" +
                            if (result.failedStudents > 0) "；${result.failedStudents} 个学生课堂失败" else ""
                        refresh()
                    }.onFailure {
                        message = it.message ?: "拉取失败"
                    }
                    pulling = false
                }
            },
            enabled = !pulling,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (pulling) "正在拉取…" else "从云端拉取学生 / 课堂") }
        if (pulling) LinearProgressIndicator(Modifier.fillMaxWidth())

        Text(
            "说明：同步失败不会丢失本地数据，会留在 outbox 重试；版本冲突会写入 sync_conflicts，不会静默覆盖。",
            fontSize = 12.sp, color = Color.Gray
        )
    }
}

@Composable
private fun SyncMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SyncGreen)
            Text(label, fontSize = 12.sp, color = Color.Gray)
        }
    }
}
