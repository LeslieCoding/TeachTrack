package com.leslie.teachtrack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.leslie.teachtrack.core.model.TaskEntity
import com.leslie.teachtrack.data.BackupPreview
import com.leslie.teachtrack.copilot.ProviderConfig
import com.leslie.teachtrack.copilot.ProviderPreferences
import com.leslie.teachtrack.copilot.OAuthConfig
import com.leslie.teachtrack.copilot.OAuthManager
import com.leslie.teachtrack.copilot.OAuthPreferences
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

private val FeatureGreen = Color(0xFF176B57)
private val FeatureMint = Color(0xFFDDF5EA)
private val FeatureInk = Color(0xFF15312A)
private val FeatureBackground = Color(0xFFF4F9F6)

@Composable
fun PlanningScreen(vm: TeachTrackViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val courses by vm.allCourses.collectAsStateWithLifecycle()
    val plans by vm.allLessonPlans.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(FeatureBackground).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("计划", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FeatureInk); Text("把下一步变成可执行事项", color = Color.Gray) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = !calendar, onClick = { calendar = false }, label = { Text("待办") }); FilterChip(selected = calendar, onClick = { calendar = true }, label = { Text("日历") }); if (!calendar) FilledTonalButton(onClick = { showAdd = true }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = FeatureGreen, contentColor = Color.White)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(4.dp)); Text("新待办") } }
        }
        Spacer(Modifier.height(18.dp))
        if (calendar) CalendarAgenda(tasks, courses, plans) else if (tasks.isEmpty() && plans.isEmpty()) EmptyPlanState { showAdd = true } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(tasks, key = { it.id }) { task -> TaskCard(vm, task) }; if (plans.isNotEmpty()) item { PlanSummaryCard(plans.size) } }
    }
    if (showAdd) TaskDialog(vm, onDismiss = { showAdd = false })
}

@Composable private fun CalendarAgenda(tasks: List<TaskEntity>, courses: List<com.leslie.teachtrack.core.model.CourseSessionEntity>, plans: List<com.leslie.teachtrack.core.model.LessonPlanEntity>) { val events = (courses.map { it.date to "课程 · ${it.title.ifBlank { it.subject }}" } + plans.map { it.plannedDate to "计划 · ${it.focus.ifBlank { it.chapter }}" } + tasks.filter { it.dueDate != null }.map { it.dueDate!! to "待办 · ${it.title}" }).sortedByDescending { it.first }; if (events.isEmpty()) EmptyPlanState {} else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(events) { event -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(event.first, color = FeatureGreen, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp)); Text(event.second, color = FeatureInk) } } } } }

@Composable private fun PlanSummaryCard(count: Int) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = FeatureMint), shape = RoundedCornerShape(14.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.EventNote, null, tint = FeatureGreen); Spacer(Modifier.width(10.dp)); Text("已有 $count 条下一节课计划，切换到日历查看安排", color = FeatureInk) } } }

@Composable private fun TaskCard(vm: TeachTrackViewModel, task: TaskEntity) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EventNote, null, tint = FeatureGreen)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(task.title, fontWeight = FontWeight.SemiBold, color = FeatureInk); Text(listOf(task.type, task.priority, task.dueDate ?: "无截止日期").joinToString(" · "), color = Color.Gray, fontSize = 13.sp) }
            AssistChip(onClick = { vm.updateTaskStatus(task.id, if (task.status == "已完成") "待处理" else "已完成") }, label = { Text(task.status) }, colors = AssistChipDefaults.assistChipColors(containerColor = FeatureMint, labelColor = FeatureGreen))
        }
    }
}

@Composable private fun TaskDialog(vm: TeachTrackViewModel, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }; var dueDate by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("新待办") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true); OutlinedTextField(dueDate, { dueDate = it }, label = { Text("截止日期（选填）") }, singleLine = true); OutlinedTextField(notes, { notes = it }, label = { Text("备注（选填）") }, minLines = 2) } }, confirmButton = { Button(enabled = title.isNotBlank(), onClick = { vm.saveTask(TaskEntity(UUID.randomUUID().toString(), title, type = "教师待办", dueDate = dueDate.ifBlank { null }, notes = notes)); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = FeatureGreen)) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable private fun EmptyPlanState(onAdd: () -> Unit) { Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.EventNote, null, tint = FeatureGreen, modifier = Modifier.size(44.dp)); Spacer(Modifier.height(10.dp)); Text("还没有待办", fontWeight = FontWeight.SemiBold, color = FeatureInk); Text("把需要跟进的学生事项记录下来。", color = Color.Gray); Spacer(Modifier.height(14.dp)); Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = FeatureGreen)) { Text("添加第一条") } } }

@Composable
fun AnalyticsScreen(vm: TeachTrackViewModel) {
    val students by vm.activeCount.collectAsStateWithLifecycle(); val courses by vm.courseCount.collectAsStateWithLifecycle(); val scores by vm.scoreCount.collectAsStateWithLifecycle(); val tasks by vm.pendingTaskCount.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(FeatureBackground).padding(20.dp)) { Text("统计", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FeatureInk); Text("基于本地真实记录", color = Color.Gray); Spacer(Modifier.height(20.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { StatCard("在读学生", students.toString(), Modifier.weight(1f)); StatCard("课程记录", courses.toString(), Modifier.weight(1f)) }; Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { StatCard("成绩记录", scores.toString(), Modifier.weight(1f)); StatCard("待处理事项", tasks.toString(), Modifier.weight(1f)) }; Spacer(Modifier.height(20.dp)); Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Insights, null, tint = FeatureGreen); Spacer(Modifier.width(8.dp)); Text("成长分析", fontWeight = FontWeight.Bold, color = FeatureInk) }; Spacer(Modifier.height(12.dp)); Text("成绩趋势和知识点变化将在录入更多考试与掌握历史后显示。", color = Color.Gray); Text("系统不会用虚假的百分比代替真实学习数据。", color = FeatureGreen, fontSize = 13.sp) } } }
}

@Composable private fun StatCard(label: String, value: String, modifier: Modifier) { Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(16.dp)) { Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = FeatureGreen); Text(label, color = Color.Gray, fontSize = 13.sp) } } }

@Composable
fun SettingsScreen(vm: TeachTrackViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsScreenContent(vm)
    }
}

@Composable
private fun SettingsScreenContent(vm: TeachTrackViewModel) {
    val students by vm.activeCount.collectAsStateWithLifecycle(); val templates by vm.templates.collectAsStateWithLifecycle(); val context = LocalContext.current; var backupState by remember { mutableStateOf("") }; var pendingBackup by remember { mutableStateOf<Pair<String, BackupPreview>?>(null) }; var showTemplate by remember { mutableStateOf(false) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.exportBackup(uri, context.contentResolver) { ok -> backupState = if (ok) "备份已保存" else "备份失败，请重试" } }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.previewBackup(uri, context.contentResolver) { json, preview -> if (json != null && preview != null) pendingBackup = json to preview else backupState = "恢复失败，请确认文件格式" } }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) vm.exportScoresCsv(uri, context.contentResolver) { ok -> backupState = if (ok) "成绩 CSV 已导出" else "CSV 导出失败，请重试" } }
    Column(Modifier.fillMaxSize().background(FeatureBackground).padding(20.dp)) { Text("设置", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FeatureInk); Text("本地优先，数据掌握在你手里", color = Color.Gray); Spacer(Modifier.height(20.dp)); SettingCard(Icons.Rounded.Lock, "隐私保护", "学生、成绩和家校信息默认只保存在本机"); SettingCard(Icons.Rounded.Storage, "本地数据库", "$students 位在读学生，当前无需账户和云服务"); SettingCard(Icons.Rounded.CloudOff, "离线可用", "没有网络也可以记录课程、成绩和计划"); Spacer(Modifier.height(18.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = { createBackup.launch("TeachTrack-Backup.json") }, colors = ButtonDefaults.buttonColors(containerColor = FeatureGreen)) { Text("导出完整备份") }; OutlinedButton(onClick = { openBackup.launch(arrayOf("application/json", "text/*")) }) { Text("恢复备份") } }; Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = { exportCsv.launch("TeachTrack-Scores.csv") }) { Text("导出成绩 CSV") }; Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = { showTemplate = true }) { Text("新建课后反馈模板") }; if (backupState.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(backupState, color = FeatureGreen, fontSize = 13.sp) }; Spacer(Modifier.height(8.dp)); Text("恢复会先展示备份日期和数据数量，确认后按 ID 合并，不会自动清空当前内容。", color = Color.Gray, fontSize = 13.sp) }
    if (templates.isNotEmpty()) TemplateSummaryCard(vm, templates)
    ProviderSettingsCard(context)
    OAuthSettingsCard(context)
    pendingBackup?.let { (json, preview) ->
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(preview.exportedAt))
        AlertDialog(onDismissRequest = { pendingBackup = null }, title = { Text("确认恢复备份？") }, text = { Text("备份时间：$date\n共 ${preview.total} 条核心记录\n学生 ${preview.students} · 课程 ${preview.courses} · 成绩 ${preview.scores}（失分项 ${preview.scoreItems}）\n知识点 ${preview.knowledgePoints} · 作业 ${preview.homework} · 沟通 ${preview.communications} · 待办 ${preview.tasks}\n\n恢复将按 ID 合并现有数据，不会删除当前内容。") }, confirmButton = { Button(onClick = { pendingBackup = null; vm.importBackupText(json) { count -> backupState = if (count != null) "已合并导入 $count 条记录" else "恢复失败，请确认文件格式" } }, colors = ButtonDefaults.buttonColors(containerColor = FeatureGreen)) { Text("确认恢复") } }, dismissButton = { TextButton(onClick = { pendingBackup = null }) { Text("取消") } })
    }
    if (showTemplate) TemplateDialog(vm, onDismiss = { showTemplate = false })
}

@Composable
private fun OAuthSettingsCard(context: android.content.Context) {
    val preferences = remember(context) { OAuthPreferences(context) }
    var config by remember { mutableStateOf(preferences.load()) }
    var expanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(preferences.status()) }
    Card(Modifier.fillMaxWidth().padding(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("OAuth 登录认证", fontWeight = FontWeight.SemiBold, color = FeatureInk)
                    Text(if (preferences.hasSession()) "已登录；Token 使用 Android Keystore 加密" else "可用浏览器登录，不必把账号密码交给 App", color = Color.Gray, fontSize = 13.sp)
                }
                Switch(checked = config.enabled, onCheckedChange = { config = config.copy(enabled = it); preferences.save(config) })
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起 OAuth 配置" else "配置 OAuth 登录") }
            if (expanded) {
                OutlinedTextField(config.provider, { config = config.copy(provider = it) }, label = { Text("认证服务商") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(config.authorizationEndpoint, { config = config.copy(authorizationEndpoint = it) }, label = { Text("Authorization Endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(config.tokenEndpoint, { config = config.copy(tokenEndpoint = it) }, label = { Text("Token Endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(config.clientId, { config = config.copy(clientId = it) }, label = { Text("Client ID（移动端公开客户端）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(config.scopes, { config = config.copy(scopes = it) }, label = { Text("Scopes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Redirect URI：${OAuthManager.REDIRECT_URI}", color = Color.Gray, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { preferences.save(config); status = "OAuth 配置已保存" }, colors = ButtonDefaults.buttonColors(containerColor = FeatureGreen)) { Text("保存配置") }
                    OutlinedButton(onClick = { preferences.save(config); status = if (OAuthManager.startAuthorization(context, config)) "已打开浏览器，请完成登录" else "请先填写 Authorization Endpoint 和 Client ID" }) { Text("开始登录") }
                }
                if (preferences.hasSession()) OutlinedButton(onClick = { preferences.clearSession(); status = "OAuth 会话已清除" }) { Text("退出登录") }
                Text(status, color = FeatureGreen, fontSize = 13.sp)
                Text("安全边界：使用 Authorization Code + PKCE；App 不接收账号密码，回调校验 state，Token 不写入 Room、备份、日志或截图。", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ProviderSettingsCard(context: android.content.Context) {
    val preferences = remember(context) { ProviderPreferences(context) }
    var config by remember { mutableStateOf(preferences.load()) }
    var apiKey by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth().padding(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("云端模型 Provider", fontWeight = FontWeight.SemiBold, color = FeatureInk)
                    Text(if (config.enabled) "已启用配置；本地数据不会自动上传" else "未启用，继续使用离线 Copilot", color = Color.Gray, fontSize = 13.sp)
                }
                Switch(checked = config.enabled, onCheckedChange = { config = config.copy(enabled = it); preferences.save(config) })
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起配置" else "配置 Provider") }
            if (expanded) {
                OutlinedTextField(config.vendor, { config = config.copy(vendor = it) }, label = { Text("厂商标识") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(config.endpoint, { config = config.copy(endpoint = it) }, label = { Text("Endpoint（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(config.model, { config = config.copy(model = it) }, label = { Text("Model（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(config.timeoutSeconds.toString(), { value -> config = config.copy(timeoutSeconds = value.toIntOrNull() ?: 30) }, label = { Text("超时秒数") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key（仅安全存储）") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { preferences.save(config); if (apiKey.isNotBlank()) { preferences.saveApiKey(apiKey); apiKey = "" }; status = if (preferences.hasApiKey()) "Provider 配置与 API Key 已安全保存" else "Provider 配置已保存" }, colors = ButtonDefaults.buttonColors(containerColor = FeatureGreen)) { Text("保存配置") }
                    OutlinedButton(onClick = { scope.launch { status = if (!config.enabled) "离线 Provider 可用" else when (val result = com.leslie.teachtrack.copilot.OpenAiCompatibleProviderGateway(preferences).testConnection(config)) { is com.leslie.teachtrack.copilot.ProviderResponse.Success -> "云端 Provider 连接成功"; is com.leslie.teachtrack.copilot.ProviderResponse.Failure -> result.message } } }) { Text("测试连接") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (preferences.hasApiKey()) "API Key 已安全保存" else "尚未配置 API Key", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (preferences.hasApiKey()) OutlinedButton(onClick = { preferences.clearApiKey(); status = "API Key 已清除" }) { Text("清除 Key") }
                }
                Text("安全边界：API Key 使用 Android Keystore 加密，不写入 Room、备份、日志或截图。endpoint 采用 OpenAI-compatible chat completions 格式，可接入多个厂商。", color = Color.Gray, fontSize = 12.sp)
                if (status.isNotBlank()) Text(status, color = FeatureGreen, fontSize = 13.sp)
            }
        }
    }
}

@Composable private fun TemplateSummaryCard(templates: List<com.leslie.teachtrack.core.model.TemplateEntity>) { Card(Modifier.fillMaxWidth().padding(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(16.dp)) { Text("课后反馈模板 · ${templates.size}", fontWeight = FontWeight.SemiBold, color = FeatureInk); templates.take(3).forEach { Text("${it.name}：${it.content.lineSequence().firstOrNull().orEmpty()}", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) } } } }

@Composable private fun TemplateSummaryCard(vm: TeachTrackViewModel, templates: List<com.leslie.teachtrack.core.model.TemplateEntity>) {
    var selected by remember { mutableStateOf<com.leslie.teachtrack.core.model.TemplateEntity?>(null) }
    var editing by remember { mutableStateOf<com.leslie.teachtrack.core.model.TemplateEntity?>(null) }
    Column {
        TemplateSummaryCard(templates)
        Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("模板管理", fontWeight = FontWeight.SemiBold, color = FeatureInk)
                templates.take(5).forEach { template ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(template.name, Modifier.weight(1f), color = FeatureInk)
                        IconButton(onClick = { editing = template }) { Icon(Icons.Rounded.Edit, "编辑") }
                        IconButton(onClick = { vm.deleteTemplate(template.id) }) { Icon(Icons.Rounded.Delete, "删除", tint = Color(0xFFB91C1C)) }
                    }
                }
            }
        }
        OutlinedButton(onClick = { selected = templates.firstOrNull() }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = FeatureGreen)) { Text("使用最近模板") }
    }
    selected?.let { TemplateUseDialog(vm, it, onDismiss = { selected = null }) }
    editing?.let { template -> TemplateEditDialog(vm, template, onDismiss = { editing = null }) }
}
@Composable private fun TemplateEditDialog(vm: TeachTrackViewModel, template: com.leslie.teachtrack.core.model.TemplateEntity, onDismiss: () -> Unit) {
    var name by remember(template.id) { mutableStateOf(template.name) }
    var content by remember(template.id) { mutableStateOf(template.content) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("编辑反馈模板") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("模板名称") }, singleLine = true); OutlinedTextField(content, { content = it }, label = { Text("模板内容") }, minLines = 5) } }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { vm.saveTemplate(template.copy(name = name, content = content, updatedAt = System.currentTimeMillis())); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = FeatureGreen)) { Text("保存修改") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}


@Composable private fun TemplateUseDialog(vm: TeachTrackViewModel, template: com.leslie.teachtrack.core.model.TemplateEntity, onDismiss: () -> Unit) {
    var student by remember { mutableStateOf("") }; var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }; var content by remember { mutableStateOf("") }; var performance by remember { mutableStateOf("") }; var nextPlan by remember { mutableStateOf("") }
    val rendered = vm.renderTemplate(template, mapOf("student_name" to student, "date" to date, "course_content" to content, "performance" to performance, "next_plan" to nextPlan))
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("使用模板 · ${template.name}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(student, { student = it }, label = { Text("学生姓名") }, singleLine = true); OutlinedTextField(date, { date = it }, label = { Text("日期") }, singleLine = true); OutlinedTextField(content, { content = it }, label = { Text("本节内容") }, minLines = 2); OutlinedTextField(performance, { performance = it }, label = { Text("课堂表现") }, minLines = 2); OutlinedTextField(nextPlan, { nextPlan = it }, label = { Text("后续计划") }, minLines = 2); Text("生成预览", fontWeight = FontWeight.SemiBold, color = FeatureInk); Text(rendered, color = Color.Gray, fontSize = 13.sp) } }, confirmButton = { Button(onClick = { val clipboard = context.getSystemService(android.content.ClipboardManager::class.java); clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("TeachTrack 课后反馈", rendered)); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = FeatureGreen)) { Text("复制全文") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable private fun TemplateDialog(vm: TeachTrackViewModel, onDismiss: () -> Unit) { var name by remember { mutableStateOf("") }; var content by remember { mutableStateOf("本节课主要内容：\n课堂学习情况：\n后续巩固方向：") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("新建课后反馈模板") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name, { name = it }, label = { Text("模板名称") }, singleLine = true); OutlinedTextField(content, { content = it }, label = { Text("模板内容") }, minLines = 5); Text("支持变量：{{student_name}}、{{date}}、{{course_content}}、{{performance}}、{{next_plan}}", color = Color.Gray, fontSize = 12.sp) } }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { vm.saveTemplate(com.leslie.teachtrack.core.model.TemplateEntity(UUID.randomUUID().toString(), name, content = content)); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = FeatureGreen)) { Text("保存模板") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }

@Composable private fun SettingCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) { Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = FeatureGreen); Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold, color = FeatureInk); Text(detail, color = Color.Gray, fontSize = 13.sp) } } } }
