package com.leslie.teachtrack

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leslie.teachtrack.remote.CloudCredentials
import com.leslie.teachtrack.remote.CloudQuestion
import com.leslie.teachtrack.remote.CloudQuestionDetail
import com.leslie.teachtrack.remote.CloudQuestionImage
import com.leslie.teachtrack.remote.CloudSubject
import com.leslie.teachtrack.remote.QuestionApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal val QbGreen = Color(0xFF176B57)
private val QbMint = Color(0xFFDDF5EA)
private val QbInk = Color(0xFF15312A)
private val QbBackground = Color(0xFFF4F9F6)

@Composable
fun QuestionBankScreen(vm: TeachTrackViewModel) {
    val context = LocalContext.current
    val credentials = remember { CloudCredentials(context) }
    var baseUrl by remember { mutableStateOf(credentials.baseUrl()) }
    var token by remember { mutableStateOf(credentials.token().orEmpty()) }
    var connectionOpen by remember { mutableStateOf(false) }
    var hubTab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<CloudQuestion>>(emptyList()) }
    var subjects by remember { mutableStateOf<List<CloudSubject>>(emptyList()) }
    var filterOpen by remember { mutableStateOf(false) }
    var filterSubjectId by remember { mutableStateOf("") }
    var filterGrade by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("") }
    var filterDifficulty by remember { mutableStateOf<Int?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var bulkRunning by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<CloudQuestionDetail?>(null) }
    var detailImages by remember { mutableStateOf<List<CloudQuestionImage>>(emptyList()) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorDetail by remember { mutableStateOf<CloudQuestionDetail?>(null) }
    var importOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val questionId = detail?.question?.id
        if (uri != null && questionId != null) {
            scope.launch {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法读取图片")
                    val mime = context.contentResolver.getType(uri) ?: "image/png"
                    val imageApi = QuestionApi(baseUrl, token)
                    imageApi.uploadImage(questionId, bytes, mime)
                    detail = imageApi.detail(questionId)
                    detailImages = imageApi.images(questionId)
                }.onSuccess { notice = "题图已上传" }
                    .onFailure { error = it.message ?: "题图上传失败" }
            }
        }
    }
    val agentExamRequest by com.leslie.teachtrack.agent.QuestionBankAgentBridge.openExamAnalysisRequests.collectAsState()

    LaunchedEffect(agentExamRequest) {
        if (agentExamRequest > 0) hubTab = 1
    }

    fun api(): QuestionApi = QuestionApi(baseUrl, token)

    fun load() {
        if (baseUrl.isBlank() || token.isBlank()) return
        scope.launch {
            loading = true
            error = null
            runCatching {
                api().search(
                    query = query.trim(),
                    subjectId = filterSubjectId,
                    grade = filterGrade,
                    questionType = filterType,
                    difficulty = filterDifficulty,
                    reviewStatus = filterStatus
                )
            }
                .onSuccess {
                    items = it
                    selectedIds.retainAll(it.map { question -> question.id }.toSet())
                }
                .onFailure { error = it.message ?: "无法连接云端题库" }
            loading = false
        }
    }

    fun bulkReview(status: String) {
        if (selectedIds.isEmpty() || bulkRunning) return
        scope.launch {
            bulkRunning = true
            error = null
            runCatching { api().bulkReview(selectedIds.toList(), status) }
                .onSuccess { result ->
                    notice = "批量操作完成：成功 ${result.succeeded}/${result.requested}" +
                        if (result.failed.isEmpty()) "" else "，失败 ${result.failed.size}"
                    selectedIds.clear()
                    selectionMode = false
                    load()
                }
                .onFailure { error = it.message ?: "批量操作失败" }
            bulkRunning = false
        }
    }

    fun bulkDelete() {
        if (selectedIds.isEmpty() || bulkRunning) return
        scope.launch {
            bulkRunning = true
            error = null
            runCatching { api().bulkDelete(selectedIds.toList()) }
                .onSuccess { result ->
                    notice = "已删除 ${result.succeeded}/${result.requested} 道题" +
                        if (result.failed.isEmpty()) "" else "，失败 ${result.failed.size}"
                    selectedIds.clear()
                    selectionMode = false
                    confirmBulkDelete = false
                    load()
                }
                .onFailure { error = it.message ?: "批量删除失败" }
            bulkRunning = false
        }
    }

    LaunchedEffect(token, baseUrl) {
        if (token.isNotBlank() && baseUrl.isNotBlank()) {
            runCatching { api().subjects() }.onSuccess { subjects = it }
            load()
        }
    }

    Column(Modifier.fillMaxSize().background(QbBackground).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(QbMint, RoundedCornerShape(12.dp)), Alignment.Center) {
                Icon(Icons.Rounded.Cloud, null, tint = QbGreen)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("云端工作台", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = QbInk)
                Text(if (token.isBlank()) "尚未连接云端" else baseUrl, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
            }
            IconButton(onClick = { filterOpen = true }, enabled = token.isNotBlank() && hubTab == 0) {
                Icon(Icons.Rounded.FilterList, "题库筛选", tint = QbGreen)
            }
            IconButton(
                onClick = {
                    selectionMode = !selectionMode
                    if (!selectionMode) selectedIds.clear()
                    hubTab = 0
                },
                enabled = token.isNotBlank() && hubTab == 0
            ) {
                Icon(Icons.Rounded.Checklist, "批量操作", tint = QbGreen)
            }
            IconButton(onClick = { connectionOpen = true }) {
                Icon(Icons.Rounded.Settings, "服务器设置", tint = QbGreen)
            }
            IconButton(onClick = { importOpen = true }, enabled = token.isNotBlank()) {
                Icon(Icons.Rounded.Upload, "批量导入", tint = QbGreen)
            }
            IconButton(onClick = { editorDetail = null; editorOpen = true }, enabled = token.isNotBlank()) {
                Icon(Icons.Rounded.Add, "录入题目", tint = QbGreen)
            }
            IconButton(onClick = { load() }, enabled = token.isNotBlank() && !loading) {
                Icon(Icons.Rounded.Refresh, "刷新", tint = QbGreen)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (connectionOpen || token.isBlank() || baseUrl.isBlank()) {
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
                    connectionOpen = false
                    load()
                }
            )
            if (connectionOpen) {
                TextButton(onClick = { connectionOpen = false }) { Text("取消") }
            }
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
        val filterCount = listOf(
            filterSubjectId, filterGrade, filterType, filterStatus,
            filterDifficulty?.toString().orEmpty()
        ).count { it.isNotBlank() }
        if (filterCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已启用 $filterCount 个筛选", fontSize = 12.sp, color = QbGreen)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    filterSubjectId = ""
                    filterGrade = ""
                    filterType = ""
                    filterStatus = ""
                    filterDifficulty = null
                    load()
                }) { Text("清除筛选") }
            }
        }
        if (selectionMode) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("已选 ${selectedIds.size}", fontSize = 13.sp, color = QbInk)
                TextButton(onClick = {
                    if (selectedIds.size == items.size) selectedIds.clear()
                    else {
                        selectedIds.clear()
                        selectedIds.addAll(items.map { it.id })
                    }
                }) { Text(if (selectedIds.size == items.size && items.isNotEmpty()) "取消全选" else "全选") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { bulkReview("APPROVED") }, enabled = selectedIds.isNotEmpty() && !bulkRunning) {
                    Text("通过")
                }
                OutlinedButton(onClick = { bulkReview("REJECTED") }, enabled = selectedIds.isNotEmpty() && !bulkRunning) {
                    Text("拒绝")
                }
                TextButton(onClick = { confirmBulkDelete = true }, enabled = selectedIds.isNotEmpty() && !bulkRunning) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp))
        }
        notice?.let {
            Text(it, color = QbGreen, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
        }

        Spacer(Modifier.height(8.dp))
        if (!loading && items.isEmpty() && error == null) {
            EmptyQuestionState()
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { question ->
                    QuestionCard(
                        question = question,
                        selectionMode = selectionMode,
                        selected = question.id in selectedIds
                    ) {
                        if (selectionMode) {
                            if (question.id in selectedIds) selectedIds.remove(question.id)
                            else selectedIds.add(question.id)
                        } else {
                            scope.launch {
                                runCatching { api().detail(question.id) }
                                    .onSuccess { loaded ->
                                        detail = loaded
                                        detailImages = runCatching { api().images(question.id) }.getOrDefault(emptyList())
                                    }
                                    .onFailure { error = it.message }
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { current ->
        QuestionDetailDialog(
            detail = current,
            images = detailImages,
            onAddImage = { imagePicker.launch("image/*") },
            onDismiss = { detail = null; detailImages = emptyList() },
            onEdit = {
                editorDetail = current
                detail = null
                editorOpen = true
            },
            onReview = { status ->
                scope.launch {
                    runCatching { api().review(current.question.id, status) }
                        .onSuccess { detail = null; load() }
                        .onFailure { error = it.message }
                }
            }
        )
    }
    if (editorOpen) {
        QuestionEditorDialog(
            api = api(),
            initial = editorDetail,
            onDismiss = { editorOpen = false },
            onSaved = {
                editorOpen = false
                notice = if (editorDetail == null) "题目已保存" else "题目修改已保存"
                load()
            },
            onRefreshed = { editorDetail = it }
        )
    }
    if (importOpen) {
        QuestionImportDialog(
            api = api(),
            onDismiss = { importOpen = false },
            onImported = { count ->
                notice = "批量导入新增 $count 道题"
                load()
            }
        )
    }
    if (filterOpen) {
        QuestionFilterDialog(
            subjects = subjects,
            subjectId = filterSubjectId,
            onSubject = { filterSubjectId = it },
            grade = filterGrade,
            onGrade = { filterGrade = it },
            type = filterType,
            onType = { filterType = it },
            status = filterStatus,
            onStatus = { filterStatus = it },
            difficulty = filterDifficulty,
            onDifficulty = { filterDifficulty = it },
            onClear = {
                filterSubjectId = ""
                filterGrade = ""
                filterType = ""
                filterStatus = ""
                filterDifficulty = null
            },
            onApply = {
                filterOpen = false
                load()
            },
            onDismiss = { filterOpen = false }
        )
    }
    if (confirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text("批量删除题目") },
            text = { Text("将软删除选中的 ${selectedIds.size} 道题，数据仍可恢复。确认继续？") },
            confirmButton = {
                TextButton(onClick = { bulkDelete() }, enabled = !bulkRunning) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkDelete = false }, enabled = !bulkRunning) { Text("取消") }
            }
        )
    }
}

@Composable
private fun QuestionFilterDialog(
    subjects: List<CloudSubject>,
    subjectId: String,
    onSubject: (String) -> Unit,
    grade: String,
    onGrade: (String) -> Unit,
    type: String,
    onType: (String) -> Unit,
    status: String,
    onStatus: (String) -> Unit,
    difficulty: Int?,
    onDifficulty: (Int?) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("题库筛选") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("学科", fontSize = 12.sp, color = Color.Gray)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(subjectId.isBlank(), { onSubject("") }, label = { Text("全部") }) }
                    items(subjects, key = { it.id }) { subject ->
                        FilterChip(subjectId == subject.id, { onSubject(subject.id) }, label = { Text(subject.name) })
                    }
                }
                Text("年级", fontSize = 12.sp, color = Color.Gray)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(grade.isBlank(), { onGrade("") }, label = { Text("全部") }) }
                    items(listOf("七上", "七下", "八上", "八下", "九上", "九下")) { value ->
                        FilterChip(grade == value, { onGrade(value) }, label = { Text(value) })
                    }
                }
                Text("题型", fontSize = 12.sp, color = Color.Gray)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(type.isBlank(), { onType("") }, label = { Text("全部") }) }
                    items(QUESTION_TYPE_OPTIONS, key = { it.first }) { (value, label) ->
                        FilterChip(type == value, { onType(value) }, label = { Text(label) })
                    }
                }
                Text("审核状态", fontSize = 12.sp, color = Color.Gray)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(status.isBlank(), { onStatus("") }, label = { Text("全部") }) }
                    items(FILTER_REVIEW_OPTIONS, key = { it.first }) { (value, label) ->
                        FilterChip(status == value, { onStatus(value) }, label = { Text(label) })
                    }
                }
                Text("难度", fontSize = 12.sp, color = Color.Gray)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(difficulty == null, { onDifficulty(null) }, label = { Text("全部") }) }
                    items((1..5).toList()) { value ->
                        FilterChip(difficulty == value, { onDifficulty(value) }, label = { Text("$value 级") })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("应用筛选") } },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("清除") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

private val FILTER_REVIEW_OPTIONS = listOf(
    "DRAFT" to "草稿",
    "NEEDS_REVIEW" to "待审核",
    "NEEDS_TEACHER_REVIEW" to "待教师审核",
    "APPROVED" to "已通过",
    "REJECTED" to "已拒绝"
)

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
        Text("点击右上角 + 录题，或使用上传图标批量导入", fontSize = 12.sp, color = Color.LightGray)
    }
}

@Composable
private fun QuestionCard(
    question: CloudQuestion,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
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
}

@Composable
private fun QuestionDetailDialog(
    detail: CloudQuestionDetail,
    images: List<CloudQuestionImage>,
    onAddImage: () -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onReview: (String) -> Unit
) {
    val needsImage = detail.abilityTags.contains("含图待补")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("题目详情") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(detail.question.stem, fontWeight = FontWeight.Medium)
                images.forEach { image -> RemoteQuestionImage(image.url) }
                if (needsImage) {
                    Text("含图题尚未上传图片，不能审核通过，也不能进入组卷。", color = Color(0xFFB42318), fontSize = 12.sp)
                }
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
            TextButton(enabled = !needsImage, onClick = { onReview("APPROVED") }) { Text("通过") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onAddImage) { Text("添加题图") }
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = { onReview("REJECTED") }) { Text("拒绝") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun RemoteQuestionImage(url: String) {
    var bitmap by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).build()
                OkHttpClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    BitmapFactory.decodeStream(response.body?.byteStream())
                }
            }.getOrNull()
        }
        if (loaded != null) bitmap = loaded.asImageBitmap() else failed = true
    }
    when {
        bitmap != null -> Image(
            bitmap = bitmap!!,
            contentDescription = "题图",
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            contentScale = ContentScale.Fit
        )
        failed -> Text("题图加载失败", color = Color(0xFFB42318), fontSize = 12.sp)
        else -> CircularProgressIndicator(Modifier.size(24.dp))
    }
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
