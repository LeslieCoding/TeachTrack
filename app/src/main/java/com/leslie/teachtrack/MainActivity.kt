package com.leslie.teachtrack

import android.os.Bundle
import android.content.ContentResolver
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.leslie.teachtrack.core.database.TeachTrackDatabase
import com.leslie.teachtrack.core.database.MIGRATION_1_2
import com.leslie.teachtrack.core.database.MIGRATION_2_3
import com.leslie.teachtrack.core.database.MIGRATION_3_4
import com.leslie.teachtrack.core.database.MIGRATION_4_5
import com.leslie.teachtrack.core.database.MIGRATION_5_6
import com.leslie.teachtrack.core.model.*
import com.leslie.teachtrack.data.BackupPreview
import com.leslie.teachtrack.data.TeachTrackRepository
import com.leslie.teachtrack.data.HistoricalSeedImporter
import com.leslie.teachtrack.data.workspace.WorkspaceManager
import com.leslie.teachtrack.data.workspace.WorkspaceExporter
import com.leslie.teachtrack.data.workspace.WorkspaceImporter
import com.leslie.teachtrack.data.workspace.WorkspaceArchiveManager
import org.json.JSONObject
import com.leslie.teachtrack.copilot.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val DeepGreen = Color(0xFF176B57)
private val Green = Color(0xFF16866A)
private val Mint = Color(0xFFDDF5EA)
private val Ink = Color(0xFF15312A)
private val AppBackground = Color(0xFFF4F9F6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); val db = Room.databaseBuilder(applicationContext, TeachTrackDatabase::class.java, "teachtrack.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build(); val repo = TeachTrackRepository(db.dao()); setContent { TeachTrackTheme { TeachTrackShell(repo) } }; OAuthManager.handleCallback(this, intent) }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); OAuthManager.handleCallback(this, intent) }
}

class TeachTrackViewModel(private val repo: TeachTrackRepository, context: android.content.Context) : ViewModel() {
    private val workspaceManager = WorkspaceManager(context)
    val copilot = CopilotService(ConfigurableCopilotProvider(context), LocalCopilotActionRegistry(repo))
    val students = repo.students.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeCount = repo.activeStudentCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val courseCount = repo.courseCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val scoreCount = repo.scoreCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val pendingTaskCount = repo.pendingTaskCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val tasks = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allCourses = repo.allCourses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allLessonPlans = repo.allLessonPlans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allScores = repo.allScores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allExams = repo.allExams.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allHomework = repo.allHomework.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allCommunications = repo.allCommunications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val knowledge = repo.knowledge().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val templates = repo.templates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun detail(id: String) = repo.student(id)
    fun courses(id: String) = repo.courses(id)
    fun scores(id: String) = repo.scores(id)
    fun saveStudent(name: String, grade: String, school: String, subject: String, onSaved: (String) -> Unit) = viewModelScope.launch { onSaved(repo.saveStudent(name, grade, school, subject)) }
    fun updateStudent(student: StudentEntity) = viewModelScope.launch { repo.updateStudent(student) }
    fun archiveStudent(id: String) = viewModelScope.launch { repo.archiveStudent(id) }
    fun saveCourse(course: CourseSessionEntity) = viewModelScope.launch { repo.saveCourse(course) }
    fun saveScore(exam: ExamEntity, score: StudentScoreEntity, item: ScoreItemEntity? = null) = viewModelScope.launch { repo.saveScore(exam, score, item) }
    fun saveTask(task: TaskEntity) = viewModelScope.launch { repo.saveTask(task) }
    fun updateTaskStatus(id: String, status: String) = viewModelScope.launch { repo.updateTaskStatus(id, status) }
    fun homework(id: String) = repo.homework(id)
    fun communications(id: String) = repo.communications(id)
    fun problemRecords(id: String) = repo.problemRecords(id)
    fun attachments(id: String) = repo.attachments(id)
    fun progress(id: String) = repo.progress(id)
    fun knowledgeEvents(id: String) = repo.knowledgeEvents(id)
    fun saveKnowledge(point: KnowledgePointEntity) = viewModelScope.launch { repo.saveKnowledge(point) }
    fun saveProgress(progress: StudentKnowledgeProgressEntity) = viewModelScope.launch { repo.saveProgress(progress) }
    fun saveHomework(homework: HomeworkEntity) = viewModelScope.launch { repo.saveHomework(homework) }
    fun saveCommunication(record: CommunicationEntity) = viewModelScope.launch { repo.saveCommunication(record) }
    fun saveParentContact(contact: ParentContactEntity) = viewModelScope.launch { repo.saveParentContact(contact) }
    fun saveAttachment(attachment: AttachmentEntity) = viewModelScope.launch { repo.saveAttachment(attachment) }
    fun deleteAttachment(id: String) = viewModelScope.launch { repo.deleteAttachment(id) }
    fun saveTemplate(template: TemplateEntity) = viewModelScope.launch { repo.saveTemplate(template) }
    fun deleteTemplate(id: String) = viewModelScope.launch { repo.deleteTemplate(id) }
    fun renderTemplate(template: TemplateEntity, values: Map<String, String>): String = repo.renderTemplate(template, values)
    fun updateProblemStatus(id: String, status: String, notes: String = "") = viewModelScope.launch { repo.updateProblemStatus(id, status, notes) }
    fun exportBackup(uri: Uri, resolver: ContentResolver, onFinished: (Boolean) -> Unit = {}) = viewModelScope.launch { runCatching { resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(repo.exportBackupJson()) } ?: error("无法打开备份文件") }.onSuccess { onFinished(true) }.onFailure { onFinished(false) } }
    fun previewBackup(uri: Uri, resolver: ContentResolver, onFinished: (String?, BackupPreview?) -> Unit = { _, _ -> }) = viewModelScope.launch { runCatching { resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("无法读取备份文件") }.fold(onSuccess = { text -> runCatching { onFinished(text, repo.previewBackupJson(text)) }.onFailure { onFinished(null, null) } }, onFailure = { onFinished(null, null) }) }
    fun importBackupText(json: String, onFinished: (Int?) -> Unit = {}) = viewModelScope.launch { runCatching { repo.importBackupJson(json) }.onSuccess { onFinished(it) }.onFailure { onFinished(null) } }
    fun importHistoricalSeed(uri: Uri, resolver: ContentResolver, onFinished: (String) -> Unit = {}) = viewModelScope.launch { runCatching { resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("无法读取历史数据文件") }.mapCatching { HistoricalSeedImporter(repo.daoForSeed()).import(it) }.onSuccess { result -> onFinished("历史数据已幂等导入：${result.students} 名学生、${result.courses} 条课程、${result.scores} 条成绩、${result.progress} 条知识点进度、${result.progressEvents} 条历史进度、${result.knowledgePoints} 个知识点") }.onFailure { onFinished("历史数据导入失败：${it.message ?: "请确认 JSON 文件格式"}") } }
    fun exportScoresCsv(uri: Uri, resolver: ContentResolver, onFinished: (Boolean) -> Unit = {}) = viewModelScope.launch { runCatching { resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(repo.exportScoresCsv()) } ?: error("无法打开导出文件") }.onSuccess { onFinished(true) }.onFailure { onFinished(false) } }
    suspend fun latestCourse(studentId: String) = repo.latestCourse(studentId)
    fun workspaceUri(): Uri? = workspaceManager.currentUri()
    fun attachWorkspace(uri: Uri, onFinished: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching {
            workspaceManager.attach(uri)
            val storage = workspaceManager.storage(uri)!!
            val manifest = workspaceManager.readManifest(storage)
            WorkspaceImporter(repo, workspaceManager).import(storage)
            manifest
        }.onSuccess { onFinished("已连接工作区：${it.name}（${it.workspaceId.take(8)}）") }.onFailure { onFinished("工作区打开失败：${it.message ?: "请确认目录包含 workspace.json"}") }
    }
    fun createWorkspace(uri: Uri, name: String = "My Students", onFinished: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching {
            workspaceManager.attach(uri)
            val storage = workspaceManager.storage(uri)!!
            val manifest = workspaceManager.createManifest(name)
            workspaceManager.ensureStructure(storage, manifest)
            WorkspaceExporter(repo, workspaceManager).export(storage, name)
            manifest
        }.onSuccess { onFinished("已创建并导出工作区：${it.name}") }.onFailure { onFinished("工作区创建失败：${it.message ?: "请重试"}") }
    }
    fun exportWorkspace(onFinished: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { val storage = workspaceManager.storage() ?: error("请先连接工作区"); WorkspaceExporter(repo, workspaceManager).export(storage) }.onSuccess { onFinished("已导出 ${it.records} 条记录到工作区") }.onFailure { onFinished("工作区导出失败：${it.message ?: "请重试"}") }
    }
    fun createWorkspaceBackup(uri: Uri, resolver: ContentResolver, onFinished: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching {
            val storage = workspaceManager.storage() ?: error("请先连接工作区")
            val manifest = workspaceManager.readManifest(storage)
            WorkspaceArchiveManager().create(storage, resolver, uri, manifest.workspaceId, "3.0.0-alpha.1", manifest.schemaVersion, JSONObject().put("students", repo.daoForSeed().allStudents().size).put("courses", repo.daoForSeed().allCourses().size).put("scores", repo.daoForSeed().allScores().size))
        }.onSuccess { onFinished(".ttbackup 已创建") }.onFailure { onFinished(".ttbackup 创建失败：${it.message ?: "请重试"}") }
    }
}

@Composable fun TeachTrackApp(repo: TeachTrackRepository) {
    val context = LocalContext.current.applicationContext
    val vm: TeachTrackViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = object : ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T = TeachTrackViewModel(repo, context) as T })
    var tab by remember { mutableStateOf(0) }; var selected by remember { mutableStateOf<String?>(null) }; var openAdd by remember { mutableStateOf(false) }; var openCourse by remember { mutableStateOf(false) }; var openScore by remember { mutableStateOf(false) }
    if (selected != null) StudentWorkspace(vm, selected!!, startCourse = openCourse, startScore = openScore, onBack = { selected = null; openCourse = false; openScore = false }) else Scaffold(bottomBar = { NavigationBar(containerColor = Color.White) { listOf(Icons.Rounded.Home to "首页", Icons.Rounded.People to "学生", Icons.Rounded.Event to "计划", Icons.Rounded.Insights to "统计", Icons.Rounded.Settings to "设置").forEachIndexed { i, item -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(item.first, null) }, label = { Text(item.second) }) } } }) { padding -> Box(Modifier.padding(padding)) { when (tab) { 0 -> Dashboard(vm, onStudent = { selected = it }, onNewStudent = { tab = 1; openAdd = true }, onRecordCourse = { id -> if (id != null) { selected = id; openCourse = true } else { tab = 1; openAdd = true } }, onRecordScore = { id -> if (id != null) { selected = id; openScore = true } else { tab = 1; openAdd = true } }); 1 -> StudentsScreen(vm, onStudent = { selected = it }, openAdd = openAdd, onAddConsumed = { openAdd = false }); 2 -> PlanningScreen(vm); 3 -> AnalyticsScreen(vm); 4 -> SettingsScreen(vm); } } } }
@Composable private fun Dashboard(vm: TeachTrackViewModel, onStudent: (String) -> Unit, onNewStudent: () -> Unit, onRecordCourse: (String?) -> Unit, onRecordScore: (String?) -> Unit) { val students by vm.students.collectAsStateWithLifecycle(); val courses by vm.allCourses.collectAsStateWithLifecycle(); val pending by vm.pendingTaskCount.collectAsStateWithLifecycle(); val scores by vm.allScores.collectAsStateWithLifecycle(); val exams by vm.allExams.collectAsStateWithLifecycle(); val homework by vm.allHomework.collectAsStateWithLifecycle(); val communications by vm.allCommunications.collectAsStateWithLifecycle(); val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }; val todayCourses = courses.filter { it.date == today }; val studentMap = students.associateBy { it.id }; Column(Modifier.fillMaxSize().background(AppBackground).verticalScroll(rememberScrollState()).padding(20.dp)) { Text("早上好，老师", fontSize = 14.sp, color = Color.Gray); Text("今天关注谁？", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink); Spacer(Modifier.height(20.dp)); SectionCard("今日课程", Icons.Rounded.Event) { if (todayCourses.isEmpty()) EmptyState("还没有今日课程", "在学生页记录今天课程后，这里会显示真实课程") else todayCourses.take(3).forEach { course -> val student = studentMap[course.studentId]; if (student != null) Row(Modifier.fillMaxWidth().clickable { onStudent(student.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).background(Mint, RoundedCornerShape(14.dp)), Alignment.Center) { Text(student.name.take(1), color = DeepGreen, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(12.dp)); Column { Text(student.name, fontWeight = FontWeight.SemiBold); Text(listOf(course.startTime, course.subject.ifBlank { student.subject }, course.title).filter { it.isNotBlank() }.joinToString(" · "), color = Color.Gray, fontSize = 13.sp) }; Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.ChevronRight, null, tint = Color.Gray) } } }; Spacer(Modifier.height(16.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricCard("在读学生", students.size.toString(), Modifier.weight(1f)); MetricCard("待处理", pending.toString(), Modifier.weight(1f)) }; Spacer(Modifier.height(16.dp)); SectionCard("快速开始", Icons.Rounded.Bolt) { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { ActionButton("新学生", Icons.Rounded.PersonAdd, onNewStudent); ActionButton("记录课程", Icons.Rounded.EditNote) { onRecordCourse(students.firstOrNull()?.id) }; ActionButton("录入成绩", Icons.Rounded.Grade) { onRecordScore(students.firstOrNull()?.id) } } }; DashboardInsights(students, courses, scores, exams, homework, communications, onStudent) } }

@Composable private fun StudentsScreen(vm: TeachTrackViewModel, onStudent: (String) -> Unit, openAdd: Boolean = false, onAddConsumed: () -> Unit = {}) {
    val students by vm.students.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("全部") }
    var sortByName by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    LaunchedEffect(openAdd) { if (openAdd) showAdd = true }
    val filtered = students.filter { student ->
        val matchesQuery = student.name.contains(query, true) || student.school.contains(query, true) || student.subject.contains(query, true)
        val matchesStatus = when (statusFilter) {
            "在读" -> student.status == StudentStatus.ACTIVE
            "暂停" -> student.status == StudentStatus.PAUSED
            "结束" -> student.status == StudentStatus.ENDED
            else -> true
        }
        matchesQuery && matchesStatus
    }.let { list -> if (sortByName) list.sortedBy { it.name } else list.sortedByDescending { it.updatedAt } }
    Column(Modifier.fillMaxSize().background(AppBackground).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("学生", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink); Text("长期成长档案", color = Color.Gray) }
            FilledTonalButton(onClick = { showAdd = true }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = DeepGreen, contentColor = Color.White)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(4.dp)); Text("新学生") }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索姓名、学校或科目") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("全部", "在读", "暂停", "结束").forEach { value -> FilterChip(selected = statusFilter == value, onClick = { statusFilter = value }, label = { Text(value) }) }
            FilterChip(selected = sortByName, onClick = { sortByName = !sortByName }, label = { Text(if (sortByName) "按姓名" else "按最近更新") })
        }
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) EmptyState(if (students.isEmpty()) "还没有学生" else "没有匹配的学生", "调整搜索或筛选条件，或添加新的学生档案。", "添加学生") { showAdd = true }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(filtered, key = { it.id }) { student -> StudentCard(student, vm) { onStudent(student.id) } } }
    }
    if (showAdd) AddStudentDialog(vm, onDismiss = { showAdd = false; onAddConsumed() }) { id -> showAdd = false; onAddConsumed(); onStudent(id) }
}

@Composable private fun StudentCard(s: StudentEntity, vm: TeachTrackViewModel, onClick: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(Mint, RoundedCornerShape(15.dp)), Alignment.Center) { Text(s.name.take(1), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = DeepGreen) }
                Spacer(Modifier.width(14.dp)); Column { Text(s.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink); Text(listOf(s.grade, s.school).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "档案待完善" }, color = Color.Gray, fontSize = 13.sp); Text(if (s.status == StudentStatus.ACTIVE) "在读 · 当前重点待记录" else "${s.status.name} · 可恢复历史", color = Green, fontSize = 12.sp) }
            }
            IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, "编辑学生", tint = DeepGreen) }
        }
    }
    if (editing) StudentEditDialog(vm, s, onDismiss = { editing = false }, onArchive = { editing = false; confirmArchive = true })
    if (confirmArchive) AlertDialog(onDismissRequest = { confirmArchive = false }, title = { Text("归档学生？") }, text = { Text("将隐藏 ${s.name} 的主列表入口，但课程、成绩和沟通历史都会保留。") }, confirmButton = { Button(onClick = { vm.archiveStudent(s.id); confirmArchive = false }, colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)) { Text("确认归档") } }, dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("取消") } })
}

@Composable private fun StudentEditDialog(vm: TeachTrackViewModel, student: StudentEntity, onDismiss: () -> Unit, onArchive: () -> Unit) {
    var name by remember(student.id) { mutableStateOf(student.name) }; var grade by remember(student.id) { mutableStateOf(student.grade) }; var school by remember(student.id) { mutableStateOf(student.school) }; var subject by remember(student.id) { mutableStateOf(student.subject) }; var notes by remember(student.id) { mutableStateOf(student.notes) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("编辑学生档案") }, text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) { OutlinedTextField(name, { name = it }, label = { Text("姓名") }, singleLine = true); OutlinedTextField(grade, { grade = it }, label = { Text("年级") }, singleLine = true); OutlinedTextField(school, { school = it }, label = { Text("学校") }, singleLine = true); OutlinedTextField(subject, { subject = it }, label = { Text("科目") }, singleLine = true); OutlinedTextField(notes, { notes = it }, label = { Text("备注") }, minLines = 2); TextButton(onClick = onArchive, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB42318))) { Text("归档并保留历史") } } }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { vm.updateStudent(student.copy(name = name.trim(), grade = grade.trim(), school = school.trim(), subject = subject.trim(), notes = notes)); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)) { Text("保存修改") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StudentWorkspace(vm: TeachTrackViewModel, id: String, startCourse: Boolean = false, startScore: Boolean = false, onBack: () -> Unit) { val student by vm.detail(id).collectAsStateWithLifecycle(null); val courses by vm.courses(id).collectAsStateWithLifecycle(emptyList()); val scores by vm.scores(id).collectAsStateWithLifecycle(emptyList()); val homework by vm.homework(id).collectAsStateWithLifecycle(emptyList()); val communications by vm.communications(id).collectAsStateWithLifecycle(emptyList()); val problems by vm.problemRecords(id).collectAsStateWithLifecycle(emptyList()); val attachments by vm.attachments(id).collectAsStateWithLifecycle(emptyList()); val progress by vm.progress(id).collectAsStateWithLifecycle(emptyList()); val knowledge by vm.knowledge.collectAsStateWithLifecycle(); var showCourse by remember(startCourse) { mutableStateOf(startCourse) }; var showScore by remember(startScore) { mutableStateOf(startScore) }; var showKnowledge by remember { mutableStateOf(false) }; var showHomework by remember { mutableStateOf(false) }; var showCommunication by remember { mutableStateOf(false) }; var showAttachments by remember { mutableStateOf(false) }; Column(Modifier.fillMaxSize().background(AppBackground)) { TopAppBar(title = { Text(student?.name ?: "学生档案") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) } }, actions = { TextButton(onClick = { showCourse = true }) { Icon(Icons.Rounded.PlayArrow, null); Text("开始新课程") }; TextButton(onClick = { showScore = true }) { Icon(Icons.Rounded.Grade, null); Text("录入成绩") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)); if (student == null) EmptyState("学生不存在", "该档案可能已被移除") else LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { ProfileHeader(student!!) }; item { SectionCard("概览", Icons.Rounded.Insights) { Text("当前重点", color = Color.Gray, fontSize = 12.sp); Text("从最近一次课程和知识点记录开始建立长期画像。", color = Ink); Spacer(Modifier.height(12.dp)); Text("下一节课建议", color = Color.Gray, fontSize = 12.sp); Text(courses.firstOrNull()?.nextLessonPlan?.ifBlank { "点击“开始新课程”，快速续上上节课计划。" } ?: "点击“开始新课程”，快速续上上节课计划。", color = DeepGreen, fontWeight = FontWeight.SemiBold) } }; item { ScoreTrendCard(scores) }; item { KnowledgeProgressCard(knowledge, progress) }; item { SectionCard("快速记录", Icons.Rounded.Bolt) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ActionButton("知识点", Icons.Rounded.AccountTree) { showKnowledge = true }; ActionButton("作业", Icons.Rounded.Assignment) { showHomework = true }; ActionButton("沟通", Icons.Rounded.Chat) { showCommunication = true }; ActionButton("附件", Icons.Rounded.AttachFile) { showAttachments = true } } } }; item { StudentTimeline(courses, scores, homework, communications, problems) }; item { if (problems.isNotEmpty()) ProblemSummaryCard(vm, problems) }; item { if (homework.isNotEmpty()) SummaryLine("作业", "${homework.size} 条记录，最近状态：${homework.first().status}") }; item { if (attachments.isNotEmpty()) AttachmentSummaryCard(vm, attachments) }; item { if (communications.isNotEmpty()) SummaryLine("家校沟通", "最近一次：${communications.first().date} · ${communications.first().summary}") } } }; if (showCourse && student != null) CourseDialog(vm, student!!, courses.firstOrNull(), onDismiss = { showCourse = false }); if (showScore && student != null) ScoreDialog(vm, student!!, onDismiss = { showScore = false }); if (showKnowledge && student != null) KnowledgeDialog(vm, student!!, onDismiss = { showKnowledge = false }); if (showHomework && student != null) HomeworkDialog(vm, student!!, onDismiss = { showHomework = false }); if (showCommunication && student != null) CommunicationDialog(vm, student!!, onDismiss = { showCommunication = false }); if (showAttachments && student != null) AttachmentDialog(vm, student!!, onDismiss = { showAttachments = false }) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ProfileHeader(s: StudentEntity) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(64.dp).background(DeepGreen, RoundedCornerShape(20.dp)), Alignment.Center) { Text(s.name.take(1), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(14.dp)); Column { Text(s.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink); Text(listOf(s.grade, s.school, s.subject).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "基础档案" }, color = Color.Gray); AssistChip(onClick = {}, label = { Text("在读") }, colors = AssistChipDefaults.assistChipColors(containerColor = Mint, labelColor = DeepGreen)) } } }

@Composable private fun CourseDialog(vm: TeachTrackViewModel, s: StudentEntity, previous: CourseSessionEntity?, onDismiss: () -> Unit) { var title by remember { mutableStateOf("") }; var content by remember { mutableStateOf(previous?.nextLessonPlan ?: "") }; var problems by remember { mutableStateOf("") }; var next by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("快速续课") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("已自动带入上节课的下一步计划", color = DeepGreen, fontSize = 13.sp); OutlinedTextField(title, { title = it }, label = { Text("本节标题") }, singleLine = true); OutlinedTextField(content, { content = it }, label = { Text("教学内容 / 本节计划") }, minLines = 2); OutlinedTextField(problems, { problems = it }, label = { Text("本节问题（选填）") }, minLines = 2); OutlinedTextField(next, { next = it }, label = { Text("下节课计划") }, minLines = 2) } }, confirmButton = { Button(onClick = { vm.saveCourse(CourseSessionEntity(UUID.randomUUID().toString(), s.id, SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()), subject = s.subject, title = title.ifBlank { "课程记录" }, content = content, problems = problems, nextLessonPlan = next)); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)) { Text("保存课程") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }

@Composable private fun ScoreDialog(vm: TeachTrackViewModel, s: StudentEntity, onDismiss: () -> Unit) { var examName by remember { mutableStateOf("") }; var score by remember { mutableStateOf("") }; var fullScore by remember { mutableStateOf("100") }; var category by remember { mutableStateOf("") }; var lostScore by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("录入成绩") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(examName, { examName = it }, label = { Text("考试名称") }, singleLine = true); OutlinedTextField(score, { score = it }, label = { Text("得分") }, singleLine = true); OutlinedTextField(fullScore, { fullScore = it }, label = { Text("满分") }, singleLine = true); OutlinedTextField(category, { category = it }, label = { Text("失分类型（选填）") }, singleLine = true); OutlinedTextField(lostScore, { lostScore = it }, label = { Text("失分分值（选填）") }, singleLine = true) } }, confirmButton = { Button(enabled = examName.isNotBlank() && score.toDoubleOrNull() != null && fullScore.toDoubleOrNull() != null, onClick = { val examId = UUID.randomUUID().toString(); val scoreId = UUID.randomUUID().toString(); val lost = lostScore.toDoubleOrNull(); vm.saveScore(ExamEntity(examId, examName, SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()), s.subject), StudentScoreEntity(scoreId, s.id, examId, score.toDouble(), fullScore.toDouble()), if (category.isNotBlank() && lost != null) ScoreItemEntity(UUID.randomUUID().toString(), scoreId, category, lost) else null); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)) { Text("保存成绩") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }

@Composable private fun KnowledgeDialog(vm: TeachTrackViewModel, s: StudentEntity, onDismiss: () -> Unit) { var name by remember { mutableStateOf("") }; var level by remember { mutableStateOf(2) }; AlertDialog(onDismissRequest = onDismiss, title = { Text("记录知识点") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name, { name = it }, label = { Text("知识点名称") }, singleLine = true); Text("掌握状态", color = Color.Gray, fontSize = 13.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("未学习", "已学习", "需巩固", "基本掌握", "熟练掌握").forEachIndexed { index, text -> FilterChip(selected = level == index, onClick = { level = index }, label = { Text(text, fontSize = 11.sp) }) } } } }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { val id = UUID.randomUUID().toString(); vm.saveKnowledge(KnowledgePointEntity(id, name = name, subject = s.subject)); vm.saveProgress(StudentKnowledgeProgressEntity(s.id, id, level)); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }

@Composable private fun HomeworkDialog(vm: TeachTrackViewModel, s: StudentEntity, onDismiss: () -> Unit) { var name by remember { mutableStateOf("") }; var content by remember { mutableStateOf("") }; var due by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("布置作业") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name, { name = it }, label = { Text("作业名称") }, singleLine = true); OutlinedTextField(content, { content = it }, label = { Text("内容") }, minLines = 2); OutlinedTextField(due, { due = it }, label = { Text("截止日期（选填）") }, singleLine = true) } }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { vm.saveHomework(HomeworkEntity(UUID.randomUUID().toString(), s.id, name = name, assignedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()), dueDate = due.ifBlank { null }, content = content)); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }

@Composable private fun CommunicationDialog(vm: TeachTrackViewModel, s: StudentEntity, onDismiss: () -> Unit) { var contact by remember { mutableStateOf("") }; var summary by remember { mutableStateOf("") }; var followUp by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("记录家校沟通") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(contact, { contact = it }, label = { Text("沟通对象") }, singleLine = true); OutlinedTextField(summary, { summary = it }, label = { Text("内容摘要") }, minLines = 2); OutlinedTextField(followUp, { followUp = it }, label = { Text("后续事项（选填）") }, minLines = 2) } }, confirmButton = { Button(enabled = summary.isNotBlank(), onClick = { vm.saveCommunication(CommunicationEntity(UUID.randomUUID().toString(), s.id, SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()), contact = contact, summary = summary, followUp = followUp)); if (contact.isNotBlank()) vm.saveParentContact(ParentContactEntity(UUID.randomUUID().toString(), s.id, name = contact, isPrimary = true)); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }

@Composable private fun SummaryLine(title: String, text: String) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.SemiBold, color = Ink); Text(text, color = Color.Gray, fontSize = 13.sp) } } }
@Composable private fun ProblemSummaryCard(vm: TeachTrackViewModel, records: List<ProblemRecordEntity>) {
    var selected by remember { mutableStateOf<ProblemRecordEntity?>(null) }
    SectionCard("问题记录 · ${records.size}", Icons.Rounded.ReportProblem) {
        records.take(5).forEach { record ->
            Row(Modifier.fillMaxWidth().clickable { selected = record }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(record.title, color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("${record.date} · ${record.status}", color = Color.Gray, fontSize = 12.sp)
                }
                Text(record.description.take(36), color = Color(0xFFB7791F), fontSize = 12.sp)
            }
        }
    }
    selected?.let { record -> ProblemStatusDialog(vm, record, onDismiss = { selected = null }) }
}
@Composable private fun ProblemStatusDialog(vm: TeachTrackViewModel, record: ProblemRecordEntity, onDismiss: () -> Unit) {
    var status by remember(record.id) { mutableStateOf(record.status) }
    var notes by remember(record.id) { mutableStateOf(record.notes) }
    val statuses = listOf("待复习", "复习中", "已改善", "已解决")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新问题记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(record.title, fontWeight = FontWeight.SemiBold, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    statuses.forEach { value -> FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value, fontSize = 11.sp) }) }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("备注") }, minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = { vm.updateProblemStatus(record.id, status, notes); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable private fun ScoreTrendCard(scores: List<StudentScoreEntity>) { SectionCard("成绩趋势", Icons.Rounded.ShowChart) { if (scores.isEmpty()) Text("还没有成绩记录，录入考试后这里会按得分率显示趋势。", color = Color.Gray, fontSize = 13.sp) else { scores.takeLast(5).forEach { score -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text("${(score.scoreRate * 100).toInt()}%", color = DeepGreen, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp)); Box(Modifier.weight(1f).height(10.dp).background(Color(0xFFE5EFEA), RoundedCornerShape(6.dp))) { Box(Modifier.fillMaxWidth(score.scoreRate.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(DeepGreen, RoundedCornerShape(6.dp))) }; Spacer(Modifier.width(8.dp)); Text("${score.score}/${score.fullScore}", color = Color.Gray, fontSize = 12.sp) } } } } }

@Composable private fun KnowledgeProgressCard(points: List<KnowledgePointEntity>, progress: List<StudentKnowledgeProgressEntity>) { val names = points.associateBy { it.id }; SectionCard("知识点掌握", Icons.Rounded.AccountTree) { if (progress.isEmpty()) Text("还没有掌握记录，点击“知识点”开始建立长期历史。", color = Color.Gray, fontSize = 13.sp) else { val weak = progress.filter { it.level == 2 }; if (weak.isNotEmpty()) { Text("学习提醒：${weak.size} 个知识点近期需要巩固", color = Color(0xFFB7791F), fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Spacer(Modifier.height(6.dp)) }; progress.takeLast(5).forEach { item -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(names[item.knowledgePointId]?.name ?: "知识点", color = Ink, modifier = Modifier.weight(1f)); Text(listOf("未学习", "已学习", "需巩固", "基本掌握", "熟练掌握").getOrElse(item.level) { "未学习" }, color = if (item.level <= 2) Color(0xFFB7791F) else DeepGreen, fontSize = 12.sp) } } } } }

@Composable private fun AddStudentDialog(vm: TeachTrackViewModel, onDismiss: () -> Unit, onSaved: (String) -> Unit) { var name by remember { mutableStateOf("") }; var grade by remember { mutableStateOf("") }; var school by remember { mutableStateOf("") }; var subject by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("添加学生") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name, { name = it }, label = { Text("姓名") }, singleLine = true); OutlinedTextField(grade, { grade = it }, label = { Text("年级（选填）") }, singleLine = true); OutlinedTextField(school, { school = it }, label = { Text("学校（选填）") }, singleLine = true); OutlinedTextField(subject, { subject = it }, label = { Text("科目（选填）") }, singleLine = true) } }, confirmButton = { Button(onClick = { vm.saveStudent(name, grade, school, subject, onSaved) }, enabled = name.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }

@Composable fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Green); Spacer(Modifier.width(8.dp)); Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink) }; Spacer(Modifier.height(10.dp)); content() } } }
@Composable private fun DashboardInsights(students: List<StudentEntity>, courses: List<CourseSessionEntity>, scores: List<StudentScoreEntity>, exams: List<ExamEntity>, homework: List<HomeworkEntity>, communications: List<CommunicationEntity>, onStudent: (String) -> Unit) {
    val studentMap = students.associateBy { it.id }
    val examMap = exams.associateBy { it.id }
    val recentScores = scores.take(3)
    val reminders = homework.filter { it.status != "已检查" && it.status != "已完成" }.take(3)
    val followUps = communications.filter { it.followUp.isNotBlank() }.take(3)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            SectionCard("待跟进提醒", Icons.Rounded.Notifications) {
                if (reminders.isEmpty() && followUps.isEmpty()) Text("目前没有需要额外跟进的提醒。", color = Color.Gray, fontSize = 13.sp)
                reminders.forEach { item -> Text("作业：${studentMap[item.studentId]?.name ?: "学生"} · ${item.name}（${item.status}）", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                followUps.forEach { item -> Text("沟通：${studentMap[item.studentId]?.name ?: "学生"} · ${item.followUp}", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            }
        }
        Column(Modifier.weight(1f)) {
            SectionCard("最近考试", Icons.Rounded.Grade) {
                if (recentScores.isEmpty()) Text("还没有考试成绩。", color = Color.Gray, fontSize = 13.sp)
                recentScores.forEach { score -> Text("${studentMap[score.studentId]?.name ?: "学生"} · ${examMap[score.examId]?.name ?: "考试"} · ${score.score}/${score.fullScore}（${(score.scoreRate * 100).toInt()}%）", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp).clickable { onStudent(score.studentId) }) }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SectionCard("最近课程", Icons.Rounded.History) {
        if (courses.isEmpty()) Text("还没有课程记录。", color = Color.Gray, fontSize = 13.sp)
        courses.take(3).forEach { course -> val student = studentMap[course.studentId]; if (student != null) Text("${course.date} · ${student.name} · ${course.title.ifBlank { course.subject }}", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp).clickable { onStudent(student.id) }) }
    }
}


@Composable private fun MetricCard(label: String, value: String, modifier: Modifier) { Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(16.dp)) { Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DeepGreen); Text(label, color = Color.Gray, fontSize = 13.sp) } } }
@Composable private fun ActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) { FilledTonalButton(onClick, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Mint, contentColor = DeepGreen), contentPadding = PaddingValues(horizontal = 10.dp)) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(text, fontSize = 12.sp) } }
data class StudentTimelineEvent(val date: String, val type: String, val detail: String)

@Composable private fun StudentTimeline(courses: List<CourseSessionEntity>, scores: List<StudentScoreEntity>, homework: List<HomeworkEntity>, communications: List<CommunicationEntity>, problems: List<ProblemRecordEntity>) {
    val events = (courses.map { StudentTimelineEvent(it.date, "课程", it.title.ifBlank { it.subject }.ifBlank { "课程记录" } + if (it.content.isBlank()) "" else " · " + it.content) } +
        scores.map { StudentTimelineEvent("", "成绩", "得分 ${it.score}/${it.fullScore} · 得分率 ${(it.scoreRate * 100).toInt()}%") } +
        homework.map { StudentTimelineEvent(it.assignedDate, "作业", "${it.name} · ${it.status}") } +
        communications.map { StudentTimelineEvent(it.date, "沟通", it.summary.ifBlank { "家校沟通记录" }) } +
        problems.map { StudentTimelineEvent(it.date, "问题", it.title.ifBlank { it.description }) }).sortedByDescending { it.date }
    SectionCard("学生时间线", Icons.Rounded.Timeline) {
        if (events.isEmpty()) EmptyState("暂无历史事件", "保存课程、成绩、作业或沟通后，这里会持续记录学生发生的变化。")
        else events.take(30).forEach { event -> TimelineEventItem(event) }
    }
}

@Composable private fun TimelineEventItem(event: StudentTimelineEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(event.date.ifBlank { "未标日期" }, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(82.dp))
        Column(Modifier.weight(1f)) {
            Text(event.type, color = DeepGreen, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(event.detail, color = Ink, fontSize = 13.sp)
        }
    }
}

@Composable private fun CourseTimelineItem(c: CourseSessionEntity) { Row(Modifier.padding(vertical = 5.dp)) { Text(c.date, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(82.dp)); Column { Text(c.title.ifBlank { "课程" }, fontWeight = FontWeight.SemiBold, color = Ink); Text(c.content.ifBlank { "暂无摘要" }, color = Color.Gray, fontSize = 13.sp); if (c.problems.isNotBlank()) Text("问题：${c.problems}", color = Color(0xFFB7791F), fontSize = 12.sp) } } }
@Composable private fun EmptyState(title: String, message: String, button: String? = null, onClick: () -> Unit = {}) { Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.FolderOpen, null, tint = Green, modifier = Modifier.size(42.dp)); Spacer(Modifier.height(8.dp)); Text(title, fontWeight = FontWeight.SemiBold, color = Ink); Text(message, color = Color.Gray, fontSize = 13.sp); if (button != null) { Spacer(Modifier.height(12.dp)); Button(onClick, colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)) { Text(button) } } } }
@Composable private fun TeachTrackTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = lightColorScheme(primary = DeepGreen, secondary = Green, tertiary = Color(0xFFB7791F), background = AppBackground, surface = Color.White, onBackground = Ink, onSurface = Ink), typography = Typography(bodyMedium = LocalTextStyle.current.copy(fontSize = 14.sp)), content = content) }
@Composable private fun AttachmentSummaryCard(vm: TeachTrackViewModel, attachments: List<AttachmentEntity>) {
    val context = LocalContext.current
    SectionCard("附件 · ${attachments.size}", Icons.Rounded.AttachFile) {
        attachments.take(5).forEach { attachment ->
            Row(Modifier.fillMaxWidth().clickable { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(attachment.uri)).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)) } }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.InsertDriveFile, "打开附件", tint = DeepGreen)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(attachment.name, color = Ink, fontWeight = FontWeight.SemiBold)
                    Text(attachment.mimeType, color = Color.Gray, fontSize = 12.sp)
                }
                IconButton(onClick = { vm.deleteAttachment(attachment.id) }) { Icon(Icons.Rounded.Delete, "删除附件", tint = Color(0xFFB91C1C)) }
            }
        }
    }
}

@Composable private fun AttachmentDialog(vm: TeachTrackViewModel, student: StudentEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedName by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "附件" } ?: "附件"
            selectedName = name
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.saveAttachment(AttachmentEntity(UUID.randomUUID().toString(), studentId = student.id, name = name, mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream", uri = uri.toString()))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加附件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("附件只记录本机文件 URI，不上传网络。", color = Color.Gray, fontSize = 13.sp)
                if (selectedName.isNotBlank()) Text("已选择：$selectedName", color = DeepGreen)
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.AttachFile, null); Spacer(Modifier.width(6.dp)); Text("选择文件") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
