package com.leslie.teachtrack

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leslie.teachtrack.core.model.StudentEntity
import com.leslie.teachtrack.core.model.StudentStatus
import com.leslie.teachtrack.remote.CloudExamDetail
import com.leslie.teachtrack.remote.CloudPractice
import com.leslie.teachtrack.remote.CloudSubject
import com.leslie.teachtrack.remote.ExamPracticeApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val EpGreen = Color(0xFF176B57)
private val EpMint = Color(0xFFDDF5EA)
private val EpInk = Color(0xFF15312A)
private val EpBackground = Color(0xFFF4F9F6)

@Composable
fun ExamPanel(vm: TeachTrackViewModel, baseUrl: String, token: String) {
    val students by vm.students.collectAsStateWithLifecycle()
    val active = students.filter { it.status != StudentStatus.ARCHIVED }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember(baseUrl, token) { ExamPracticeApi(baseUrl, token) }

    var subjects by remember { mutableStateOf<List<CloudSubject>>(emptyList()) }
    var subjectId by remember { mutableStateOf("") }
    var selectedStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var examName by remember { mutableStateOf("手动导入试卷") }
    var examDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }
    var examId by remember { mutableStateOf<String?>(null) }
    val text by com.leslie.teachtrack.agent.QuestionBankAgentBridge.examAnalysisText.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<CloudExamDetail?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { api.subjects() }
            .onSuccess {
                subjects = it
                if (subjectId.isBlank()) subjectId = it.firstOrNull()?.id.orEmpty()
            }
            .onFailure { error = it.message }
    }

    fun importExam() {
        val student = selectedStudent ?: run { error = "先选择学生"; return }
        if (subjectId.isBlank()) {
            error = "先选择学科"
            return
        }
        scope.launch {
            loading = true
            error = null
            runCatching {
                val serverId = api.resolveStudent(student.id)
                api.importExam(serverId, subjectId, examName.trim(), examDate)
            }.onSuccess { examId = it }
                .onFailure { error = it.message ?: "导入失败" }
            loading = false
        }
    }

    fun analyze() {
        val id = examId ?: run { error = "先导入试卷"; return }
        if (text.isBlank()) {
            error = "请粘贴 OCR 文本或按“1. 题干 2分”格式输入题目"
            return
        }
        scope.launch {
            loading = true
            error = null
            runCatching { api.analyzeExam(id, text) }
                .onSuccess { detail = it }
                .onFailure { error = it.message ?: "分析失败" }
            loading = false
        }
    }

    fun approve() {
        val id = examId ?: return
        scope.launch {
            loading = true
            error = null
            runCatching { api.approveExam(id) }
                .onSuccess { detail = it }
                .onFailure { error = it.message ?: "审核失败" }
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(EpBackground)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("试卷分析", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = EpInk)
        Text(
            "选择学生 → 导入试卷 → 粘贴题目文本 → 逐题分析 → 教师审核入库。图片 OCR 由后端 Provider 接入后自动完成。",
            fontSize = 12.sp, color = Color.Gray
        )

        StudentChips(active, selectedStudent) { selectedStudent = it }
        SubjectChips(subjects, subjectId) { subjectId = it }

        OutlinedTextField(
            value = examName,
            onValueChange = { examName = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("试卷名称") }
        )
        OutlinedTextField(
            value = examDate,
            onValueChange = { examDate = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("考试日期 yyyy-MM-dd") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { importExam() }, enabled = !loading) { Text(if (examId == null) "导入试卷" else "重新导入") }
            if (examId != null) {
                AssistChip(onClick = {}, label = { Text("已导入", fontSize = 11.sp) })
            }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { com.leslie.teachtrack.agent.QuestionBankAgentBridge.updateExamAnalysisText(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("题目文本（Agent、OCR 或手工）") },
            minLines = 4,
            maxLines = 10,
            placeholder = { Text("1. 液体压强选择题 2分\n2. 计算液体压强 4分") }
        )
        Button(onClick = { analyze() }, enabled = !loading && examId != null) { Text("逐题分析") }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

        detail?.let { current ->
            Text("状态：${examStatus(current.status)} · ${current.questions.size} 题",
                fontWeight = FontWeight.SemiBold, color = EpGreen)
            current.questions.forEachIndexed { index, question ->
                val analysis = current.analyses.firstOrNull { it.examQuestionId == question.id }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${index + 1}. [${question.type}] ${question.score}/${question.fullScore} 分",
                            fontSize = 12.sp, color = Color.Gray)
                        Text(question.stem.ifBlank { "（无题干）" }, fontWeight = FontWeight.Medium, color = EpInk)
                        analysis?.let {
                            Text(
                                "知识点：${it.knowledgeSummary.ifBlank { "未匹配" }} · 错因：${mistakeLabel(it.mistakeType)}" +
                                    if (it.taughtBefore) " · 课堂讲过" else "",
                                fontSize = 12.sp, color = Color.DarkGray
                            )
                        }
                    }
                }
            }
            if (current.status != "APPROVED") {
                Button(onClick = { approve() }, enabled = !loading) { Text("教师审核通过并入库") }
            }
        }
    }
}

@Composable
fun PracticePanel(
    vm: TeachTrackViewModel,
    baseUrl: String,
    token: String,
    onOpenQuestionBank: () -> Unit = {}
) {
    val students by vm.students.collectAsStateWithLifecycle()
    val active = students.filter { it.status != StudentStatus.ARCHIVED }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember(baseUrl, token) { ExamPracticeApi(baseUrl, token) }

    var subjects by remember { mutableStateOf<List<CloudSubject>>(emptyList()) }
    var subjectId by remember { mutableStateOf("") }
    var selectedStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var approvedCount by remember { mutableIntStateOf(-1) }
    var count by remember { mutableStateOf("5") }
    var mode by remember { mutableStateOf("BASIC") }
    var practice by remember { mutableStateOf<CloudPractice?>(null) }
    val answers = remember { mutableStateMapOf<String, String>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { api.subjects() }
            .onSuccess {
                subjects = it
                if (subjectId.isBlank()) subjectId = it.firstOrNull()?.id.orEmpty()
            }
            .onFailure { error = it.message }
    }
    LaunchedEffect(subjectId) {
        approvedCount = if (subjectId.isBlank()) -1 else runCatching {
            api.approvedQuestionCount(subjectId)
        }.getOrDefault(-1)
    }

    fun generate() {
        val student = selectedStudent ?: run { error = "先选择学生"; return }
        if (subjectId.isBlank()) {
            error = "先选择学科"
            return
        }
        scope.launch {
            loading = true
            error = null
            runCatching {
                val serverId = api.resolveStudent(student.id)
                api.generatePractice(serverId, subjectId, count.toIntOrNull()?.coerceIn(1, 50) ?: 5, mode)
            }.onSuccess {
                practice = it
                answers.clear()
                notice = null
            }
                .onFailure { error = it.message ?: "生成失败" }
            loading = false
        }
    }

    fun approve() {
        val id = practice?.id ?: return
        scope.launch {
            loading = true
            error = null
            runCatching { api.approvePractice(id) }
                .onSuccess { practice = it }
                .onFailure { error = it.message ?: "审核失败" }
            loading = false
        }
    }

    fun submit() {
        val current = practice ?: return
        if (current.items.any { answers[it.id].orEmpty().isBlank() }) {
            error = "请先填写全部题目答案"
            return
        }
        scope.launch {
            loading = true
            error = null
            runCatching { api.submitPractice(current.id, answers.toMap()) }
                .onSuccess {
                    practice = it
                    notice = "练习已提交并自动判分，掌握度已更新"
                }
                .onFailure { error = it.message ?: "提交失败" }
            loading = false
        }
    }

    fun export(variant: String, format: String) {
        val id = practice?.id ?: return
        scope.launch {
            loading = true
            error = null
            runCatching { api.exportPractice(id, variant, format) }
                .onSuccess { url ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.onFailure { error = "无法打开下载链接：$url" }
                }
                .onFailure { error = it.message ?: "导出失败" }
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(EpBackground)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("AI 精准练习", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = EpInk)
        Text("从已审核题库选题；题库不足时明确报错，不会用 Mock 伪造题目。", fontSize = 12.sp, color = Color.Gray)

        StudentChips(active, selectedStudent) { selectedStudent = it }
        SubjectChips(subjects, subjectId) { subjectId = it }

        Text("模式", fontSize = 12.sp, color = Color.Gray)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(PRACTICE_MODES) { (key, label) ->
                FilterChip(selected = mode == key, onClick = { mode = key }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        OutlinedTextField(
            value = count,
            onValueChange = { count = it.filter(Char::isDigit).take(2) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("题量（1–50）") }
        )
        if (approvedCount >= 0) {
            Text(
                if (approvedCount == 0) "该学科还没有已审核题目，先在题库录入完整题目并保存通过。"
                else "该学科可用已审核题目：$approvedCount 道（少于题量时按实际数量生成）",
                fontSize = 12.sp,
                color = if (approvedCount == 0) MaterialTheme.colorScheme.error else EpGreen
            )
        }
        if (approvedCount == 0) {
            OutlinedButton(onClick = onOpenQuestionBank, modifier = Modifier.fillMaxWidth()) {
                Text("去题库录入 / 审核题目")
            }
        }
        Button(onClick = { generate() }, enabled = !loading && approvedCount != 0) { Text("生成练习草稿") }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        notice?.let { Text(it, color = EpGreen, fontSize = 13.sp) }

        practice?.let { current ->
            Text("${current.title} · ${current.status} · ${current.items.size} 题",
                fontWeight = FontWeight.SemiBold, color = EpGreen)
            current.items.forEachIndexed { index, item ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${index + 1}. ${item.score} 分 · ${item.sourceKind}", fontSize = 12.sp, color = Color.Gray)
                        Text(item.stem.ifBlank { "（无题干）" }, color = EpInk)
                        OutlinedTextField(
                            value = answers[item.id].orEmpty(),
                            onValueChange = { answers[item.id] = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = current.status != "COMPLETED",
                            label = { Text("学生答案") }
                        )
                    }
                }
            }
            if (current.mastery.isNotEmpty()) {
                Text(
                    current.mastery.joinToString(" · ") {
                        "掌握度 ${it.masteryScore.toInt()}%（对 ${it.correctCount} / 错 ${it.wrongCount}）"
                    },
                    fontSize = 12.sp,
                    color = EpGreen
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current.status == "DRAFT" || current.status == "GENERATED") {
                    Button(onClick = { approve() }, enabled = !loading) { Text("审核通过") }
                }
                if (current.status == "APPROVED") {
                    Button(onClick = { submit() }, enabled = !loading) { Text("提交并自动判分") }
                }
                OutlinedButton(onClick = { export("STUDENT", "PDF") }, enabled = !loading) { Text("学生版 PDF") }
                OutlinedButton(onClick = { export("TEACHER", "PDF") }, enabled = !loading) { Text("教师版 PDF") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { export("STUDENT", "DOCX") }, enabled = !loading) { Text("学生版 DOCX") }
                OutlinedButton(onClick = { export("TEACHER", "DOCX") }, enabled = !loading) { Text("教师版 DOCX") }
            }
        }
    }
}

@Composable
private fun StudentChips(
    students: List<StudentEntity>,
    selected: StudentEntity?,
    onSelect: (StudentEntity) -> Unit
) {
    if (students.isEmpty()) {
        Text("本地还没有学生；先到“学生”页添加。", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        return
    }
    Text("学生", fontSize = 12.sp, color = Color.Gray)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(students, key = { it.id }) { student ->
            FilterChip(
                selected = selected?.id == student.id,
                onClick = { onSelect(student) },
                modifier = Modifier.testTag("student-chip-${student.id}"),
                label = { Text(student.displayName.ifBlank { student.name }, fontSize = 11.sp) }
            )
        }
    }
}

@Composable
private fun SubjectChips(subjects: List<CloudSubject>, selectedId: String, onSelect: (String) -> Unit) {
    if (subjects.isEmpty()) return
    Text("学科", fontSize = 12.sp, color = Color.Gray)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(subjects, key = { it.id }) { subject ->
            FilterChip(
                selected = selectedId == subject.id,
                onClick = { onSelect(subject.id) },
                label = { Text(subject.name, fontSize = 11.sp) }
            )
        }
    }
}

private val PRACTICE_MODES = listOf(
    "BASIC" to "基础巩固",
    "ERROR_FOCUS" to "易错突破",
    "TOPIC" to "专项强化",
    "COMPREHENSIVE" to "综合训练",
    "PRE_EXAM" to "考前复习",
    "RETEST" to "错题复测"
)

private fun examStatus(status: String): String = when (status) {
    "AI_PARSED" -> "AI 已解析，待审核"
    "APPROVED" -> "已审核"
    "NEEDS_REVIEW" -> "待审核"
    "REJECTED" -> "已拒绝"
    "FAILED", "PARSE_FAILED" -> "解析失败"
    else -> status.ifBlank { "未知" }
}

private fun mistakeLabel(type: String): String = when (type) {
    "NEEDS_TEACHER_CONFIRMATION" -> "需教师确认"
    "KNOWLEDGE_GAP" -> "知识缺口"
    "CONCEPT_ERROR" -> "概念错误"
    "CALCULATION_ERROR" -> "计算错误"
    "READING_ERROR" -> "审题错误"
    "UNIT_ERROR" -> "单位错误"
    "CARELESS" -> "粗心"
    "UNKNOWN" -> "未确定"
    else -> type
}
