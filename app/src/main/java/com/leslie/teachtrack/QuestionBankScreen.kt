package com.leslie.teachtrack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leslie.teachtrack.remote.CloudCredentials
import com.leslie.teachtrack.remote.CloudQuestion
import com.leslie.teachtrack.remote.CloudQuestionDetail
import com.leslie.teachtrack.remote.QuestionApi
import kotlinx.coroutines.launch

private val QbGreen = Color(0xFF176B57)
private val QbMint = Color(0xFFDDF5EA)
private val QbInk = Color(0xFF15312A)
private val QbBackground = Color(0xFFF4F9F6)

@Composable
fun QuestionBankScreen(vm: TeachTrackViewModel) {
    val context = LocalContext.current
    val credentials = remember { CloudCredentials(context) }
    var baseUrl by remember { mutableStateOf(credentials.baseUrl().ifBlank { "https://teachtrack.leslieholmes.ccwu.cc" }) }
    var token by remember { mutableStateOf(credentials.token().orEmpty()) }
    var hubTab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<CloudQuestion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<CloudQuestionDetail?>(null) }
    val scope = rememberCoroutineScope()

    fun api(): QuestionApi = QuestionApi(baseUrl, token)

    fun load() {
        if (baseUrl.isBlank() || token.isBlank()) return
        scope.launch {
            loading = true
            error = null
            runCatching { api().search(query.trim()) }
                .onSuccess { items = it }
                .onFailure { error = it.message ?: "无法连接云端题库" }
            loading = false
        }
    }

    LaunchedEffect(token, baseUrl) {
        if (token.isNotBlank() && baseUrl.isNotBlank()) load()
    }

    Column(Modifier.fillMaxSize().background(QbBackground).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(QbMint, RoundedCornerShape(12.dp)), Alignment.Center) {
                Icon(Icons.Rounded.Cloud, null, tint = QbGreen)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("云端工作台", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = QbInk)
                Text(if (token.isBlank()) "尚未连接云端" else baseUrl, fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { load() }, enabled = token.isNotBlank() && !loading) {
                Icon(Icons.Rounded.Refresh, "刷新", tint = QbGreen)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (token.isBlank() || baseUrl.isBlank()) {
            ConnectionCard(
                baseUrl = baseUrl,
                onBaseUrlChange = { baseUrl = it },
                token = token,
                onTokenChange = { token = it },
                onSave = {
                    credentials.saveBaseUrl(baseUrl)
                    credentials.saveToken(token)
                    baseUrl = credentials.baseUrl()
                    token = credentials.token().orEmpty()
                    load()
                }
            )
            return@Column
        }

        TabRow(selectedTabIndex = hubTab) {
            Tab(selected = hubTab == 0, onClick = { hubTab = 0 }, text = { Text("题库") })
            Tab(selected = hubTab == 1, onClick = { hubTab = 1 }, text = { Text("试卷分析") })
            Tab(selected = hubTab == 2, onClick = { hubTab = 2 }, text = { Text("精准练习") })
            Tab(selected = hubTab == 3, onClick = { hubTab = 3 }, text = { Text("同步") })
        }
        Spacer(Modifier.height(8.dp))
        if (hubTab == 3) {
            Box(Modifier.weight(1f)) { SyncPanel(baseUrl, token) }
            return@Column
        }
        if (hubTab == 1) {
            Box(Modifier.weight(1f)) { ExamPanel(vm, baseUrl, token) }
            return@Column
        }
        if (hubTab == 2) {
            Box(Modifier.weight(1f)) { PracticePanel(vm, baseUrl, token) }
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索题干 / 知识点") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                TextButton(onClick = { load() }, enabled = !loading) { Text("搜索") }
            }
        )

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(Modifier.height(8.dp))
        if (!loading && items.isEmpty() && error == null) {
            EmptyQuestionState()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { question ->
                    QuestionCard(question) {
                        scope.launch {
                            runCatching { api().detail(question.id) }
                                .onSuccess { detail = it }
                                .onFailure { error = it.message }
                        }
                    }
                }
            }
        }
    }

    detail?.let { current ->
        QuestionDetailDialog(
            detail = current,
            onDismiss = { detail = null },
            onReview = { status ->
                scope.launch {
                    runCatching { api().review(current.question.id, status) }
                        .onSuccess { detail = null; load() }
                        .onFailure { error = it.message }
                }
            }
        )
    }
}

@Composable
private fun ConnectionCard(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("连接云端题库", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                "填写后端地址与 Owner Token。Token 使用 AndroidKeyStore 加密保存，不会写入备份或日志。",
                fontSize = 12.sp, color = Color.Gray
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("后端地址，例如 http://192.168.1.10:8080") }
            )
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Owner Token") }
            )
            Button(onClick = onSave, enabled = baseUrl.isNotBlank() && token.isNotBlank()) {
                Text("保存并连接")
            }
        }
    }
}

@Composable
private fun EmptyQuestionState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.Search, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(8.dp))
        Text("题库还没有题目", color = Color.Gray)
        Text("可通过后端 API 或迁移导入题目", fontSize = 12.sp, color = Color.LightGray)
    }
}

@Composable
private fun QuestionCard(question: CloudQuestion, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(question.stem.take(160), fontWeight = FontWeight.Medium, color = QbInk)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(typeLabel(question.questionType), fontSize = 11.sp) })
                Text("难度 ${question.difficulty}", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.weight(1f))
                Text(
                    statusLabel(question.reviewStatus),
                    fontSize = 12.sp,
                    color = if (question.reviewStatus == "APPROVED") QbGreen else Color(0xFFB26A00)
                )
            }
        }
    }
}

@Composable
private fun QuestionDetailDialog(
    detail: CloudQuestionDetail,
    onDismiss: () -> Unit,
    onReview: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("题目详情") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(detail.question.stem, fontWeight = FontWeight.Medium)
                if (detail.options.isNotEmpty()) {
                    detail.options.forEach { (key, content) -> Text("$key. $content", fontSize = 13.sp) }
                }
                if (detail.standardAnswer.isNotBlank()) {
                    Text("答案：${detail.standardAnswer}", fontSize = 13.sp, color = QbGreen)
                }
                if (detail.analysis.isNotBlank()) {
                    Text("解析：${detail.analysis}", fontSize = 13.sp, color = Color.DarkGray)
                }
                Text(
                    "状态：${statusLabel(detail.question.reviewStatus)} · 难度 ${detail.question.difficulty}",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onReview("APPROVED") }) { Text("通过") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onReview("REJECTED") }) { Text("拒绝") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

private fun typeLabel(type: String): String = when (type) {
    "SINGLE_CHOICE" -> "单选"
    "MULTI_CHOICE" -> "多选"
    "FILL_BLANK" -> "填空"
    "EXPERIMENT" -> "实验"
    "CALCULATION" -> "计算"
    "SHORT_ANSWER" -> "简答"
    "ESSAY" -> "论述"
    else -> "其他"
}

private fun statusLabel(status: String): String = when (status) {
    "APPROVED" -> "已审核"
    "REJECTED" -> "已拒绝"
    "NEEDS_TEACHER_REVIEW" -> "待教师审核"
    "NEEDS_REVIEW" -> "待审核"
    "AI_PARSED" -> "AI 已解析"
    else -> "草稿"
}
