package com.leslie.teachtrack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.leslie.teachtrack.remote.CloudDuplicateCheck
import com.leslie.teachtrack.remote.CloudKnowledgePoint
import com.leslie.teachtrack.remote.CloudQuestionDetail
import com.leslie.teachtrack.remote.CloudQuestionInput
import com.leslie.teachtrack.remote.CloudSubject
import com.leslie.teachtrack.remote.QuestionApi
import com.leslie.teachtrack.remote.QuestionApiException
import kotlinx.coroutines.launch

internal val QUESTION_TYPE_OPTIONS = listOf(
    "SINGLE_CHOICE" to "单选", "MULTI_CHOICE" to "多选", "FILL_BLANK" to "填空",
    "EXPERIMENT" to "实验", "CALCULATION" to "计算", "SHORT_ANSWER" to "简答",
    "ESSAY" to "论述", "OTHER" to "其他"
)

internal val QUESTION_SOURCE_OPTIONS = listOf(
    "SELF_CREATED" to "教师自编", "TEACHER_IMPORTED" to "教师导入",
    "STUDENT_EXAM" to "学生试卷", "AUTHORIZED_BANK" to "授权题库",
    "AI_GENERATED" to "AI 原创", "AI_VARIANT" to "AI 变式"
)

private data class EditorOption(val key: String, val content: String)

@Composable
fun QuestionEditorDialog(
    api: QuestionApi,
    initial: CloudQuestionDetail?,
    onDismiss: () -> Unit,
    onSaved: (CloudQuestionDetail) -> Unit,
    onRefreshed: (CloudQuestionDetail) -> Unit
) {
    val scope = rememberCoroutineScope()
    var subjects by remember { mutableStateOf<List<CloudSubject>>(emptyList()) }
    var knowledgePoints by remember { mutableStateOf<List<CloudKnowledgePoint>>(emptyList()) }
    var subjectId by remember(initial) { mutableStateOf(initial?.question?.subjectId.orEmpty()) }
    var grade by remember(initial) { mutableStateOf(initial?.question?.grade.orEmpty()) }
    var textbookVersion by remember(initial) { mutableStateOf(initial?.question?.textbookVersion.orEmpty()) }
    var questionType by remember(initial) { mutableStateOf(initial?.question?.questionType ?: "SINGLE_CHOICE") }
    var difficulty by remember(initial) { mutableStateOf(initial?.question?.difficulty ?: 3) }
    var sourceType by remember(initial) { mutableStateOf(initial?.question?.sourceType ?: "SELF_CREATED") }
    var sourceName by remember(initial) { mutableStateOf(initial?.question?.sourceName.orEmpty()) }
    var stem by remember(initial) { mutableStateOf(initial?.question?.stem.orEmpty()) }
    var standardAnswer by remember(initial) { mutableStateOf(initial?.standardAnswer.orEmpty()) }
    var analysis by remember(initial) { mutableStateOf(initial?.analysis.orEmpty()) }
    var scoringPoints by remember(initial) { mutableStateOf(initial?.scoringPoints.orEmpty()) }
    var solutionSteps by remember(initial) { mutableStateOf(initial?.solutionSteps.orEmpty()) }
    var knowledgeQuery by remember { mutableStateOf("") }
    var knowledgeCreateOpen by remember { mutableStateOf(false) }
    var newKnowledgeName by remember { mutableStateOf("") }
    var newKnowledgeChapter by remember { mutableStateOf("") }
    var newKnowledgeSection by remember { mutableStateOf("") }
    var selectedKnowledge by remember(initial) {
        mutableStateOf(initial?.knowledge?.map { it.knowledgePointId }?.toSet().orEmpty())
    }
    val options = remember(initial) {
        mutableStateListOf<EditorOption>().apply {
            val saved = initial?.options.orEmpty()
            if (saved.isEmpty()) {
                addAll(listOf(EditorOption("A", ""), EditorOption("B", ""), EditorOption("C", ""), EditorOption("D", "")))
            } else {
                addAll(saved.map { EditorOption(it.first, it.second) })
            }
        }
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var duplicateMessage by remember { mutableStateOf<String?>(null) }
    var duplicateApproved by remember { mutableStateOf(false) }
    var versionConflict by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { api.subjects() }
            .onSuccess {
                subjects = it
                if (subjectId.isBlank()) {
                    subjectId = it.firstOrNull { subject -> subject.name.contains("科学") }?.id
                        ?: it.firstOrNull()?.id.orEmpty()
                }
            }
            .onFailure { error = it.message }
    }
    LaunchedEffect(subjectId) {
        if (subjectId.isBlank()) return@LaunchedEffect
        runCatching { api.knowledgePoints(subjectId) }
            .onSuccess { knowledgePoints = it }
            .onFailure { error = it.message }
    }

    fun input(reviewStatus: String): CloudQuestionInput = CloudQuestionInput(
        subjectId = subjectId,
        grade = grade.trim(),
        textbookVersion = textbookVersion.trim(),
        questionType = questionType,
        difficulty = difficulty,
        stem = stem.trim(),
        sourceType = sourceType,
        sourceName = sourceName.trim(),
        reviewStatus = reviewStatus,
        options = if (questionType in setOf("SINGLE_CHOICE", "MULTI_CHOICE")) {
            options.map { it.key to it.content.trim() }
        } else emptyList(),
        standardAnswer = standardAnswer.trim(),
        analysis = analysis.trim(),
        scoringPoints = scoringPoints.trim(),
        solutionSteps = solutionSteps.trim(),
        knowledgePointIds = knowledgePoints.filter { it.id in selectedKnowledge }.map { it.id },
        abilityTags = buildList {
            initial?.abilityTags?.filterNot { it == "含图待补" }?.let(::addAll)
            if (Regex("如图|见图|右图|左图|下图|上图|图所示|电路图|装置图").containsMatchIn(stem)) add("含图待补")
        }.distinct(),
        baseVersion = initial?.question?.version
    )

    fun validate(): String? = when {
        subjectId.isBlank() -> "请选择学科"
        stem.isBlank() -> "题干不能为空"
        questionType in setOf("SINGLE_CHOICE", "MULTI_CHOICE") && options.size < 2 -> "选择题至少需要 2 个选项"
        questionType in setOf("SINGLE_CHOICE", "MULTI_CHOICE") && options.any { it.content.isBlank() } -> "请填写或删除空选项"
        questionType in setOf("SINGLE_CHOICE", "MULTI_CHOICE") && standardAnswer.isBlank() -> "选择题需要标准答案"
        else -> null
    }

    fun CloudDuplicateCheck.withoutCurrentQuestion(): CloudDuplicateCheck {
        val currentId = initial?.question?.id
        return CloudDuplicateCheck(
            exact = exact?.takeUnless { it.id == currentId },
            near = near?.takeUnless { it.id == currentId },
            similar = similar.filterNot { it.id == currentId }
        )
    }

    fun save(reviewStatus: String) {
        if (saving) return
        validate()?.let {
            error = it
            return
        }
        scope.launch {
            saving = true
            error = null
            duplicateMessage = null
            versionConflict = false
            try {
                val value = input(reviewStatus)
                val duplicates = api.duplicates(value.stem).withoutCurrentQuestion()
                if (duplicates.exact != null) {
                    duplicateMessage = "题库已有完全相同的题干（${duplicates.exact.id}），本次未保存。"
                    return@launch
                }
                val alternatives = listOfNotNull(duplicates.near) + duplicates.similar
                if (alternatives.isNotEmpty() && !duplicateApproved) {
                    duplicateMessage = "发现 ${alternatives.size} 道规范化重复或相似题。请核对题干；确认不是重复题后，再次点击保存。"
                    duplicateApproved = true
                    return@launch
                }
                val saved = if (initial == null) api.create(value) else api.update(initial.question.id, value)
                onSaved(saved)
            } catch (e: QuestionApiException) {
                when (e.code) {
                    "VERSION_CONFLICT" -> {
                        versionConflict = true
                        error = "题目已被其他端修改。请刷新到最新版本后再编辑，当前修改尚未保存。"
                    }
                    "DUPLICATE_QUESTION" -> duplicateMessage = "服务端检测到相同题干，本次未保存：${e.message}"
                    else -> error = e.message
                }
            } catch (e: Exception) {
                error = e.message ?: "保存失败"
            } finally {
                saving = false
            }
        }
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.94f), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (initial == null) "录入题目" else "编辑题目 · v${initial.question.version}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("保存前自动查重；图片题先标记，待图片功能上线后补图", fontSize = 12.sp, color = Color.Gray)
                    }
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("关闭") }
                }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuestionSubjectChips(subjects, subjectId) {
                        subjectId = it
                        selectedKnowledge = emptySet()
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(grade, { grade = it }, Modifier.weight(1f), label = { Text("年级，如八上") }, singleLine = true)
                        OutlinedTextField(textbookVersion, { textbookVersion = it }, Modifier.weight(1f), label = { Text("教材版本") }, singleLine = true)
                    }
                    Text("题型", fontSize = 12.sp, color = Color.Gray)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(QUESTION_TYPE_OPTIONS, key = { it.first }) { (value, label) ->
                            FilterChip(questionType == value, { questionType = value }, label = { Text(label) })
                        }
                    }
                    Text("难度", fontSize = 12.sp, color = Color.Gray)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((1..5).toList()) { value ->
                            FilterChip(difficulty == value, { difficulty = value }, label = { Text("$value 级") })
                        }
                    }
                    Text("来源", fontSize = 12.sp, color = Color.Gray)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(QUESTION_SOURCE_OPTIONS, key = { it.first }) { (value, label) ->
                            FilterChip(sourceType == value, { sourceType = value }, label = { Text(label) })
                        }
                    }
                    OutlinedTextField(sourceName, { sourceName = it }, Modifier.fillMaxWidth(), label = { Text("来源名称，如 2026 温州期末") }, singleLine = true)
                    OutlinedTextField(
                        stem,
                        { stem = it; duplicateApproved = false; duplicateMessage = null },
                        Modifier.fillMaxWidth(),
                        label = { Text("题干") }, minLines = 4, maxLines = 10
                    )
                    if (questionType in setOf("SINGLE_CHOICE", "MULTI_CHOICE")) {
                        Text("选项", fontWeight = FontWeight.SemiBold)
                        options.forEachIndexed { index, option ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    option.content,
                                    { options[index] = option.copy(content = it) },
                                    Modifier.weight(1f),
                                    label = { Text("选项 ${option.key}") }
                                )
                                if (options.size > 2) {
                                    TextButton(onClick = {
                                        options.removeAt(index)
                                        val remaining = options.map { it.content }
                                        options.clear()
                                        remaining.forEachIndexed { position, content ->
                                            options += EditorOption(('A'.code + position).toChar().toString(), content)
                                        }
                                    }) { Text("删除") }
                                }
                            }
                        }
                        if (options.size < 8) {
                            TextButton(onClick = { options += EditorOption(('A'.code + options.size).toChar().toString(), "") }) {
                                Text("添加选项")
                            }
                        }
                    }
                    OutlinedTextField(standardAnswer, { standardAnswer = it }, Modifier.fillMaxWidth(), label = { Text("标准答案") }, minLines = 2)
                    OutlinedTextField(analysis, { analysis = it }, Modifier.fillMaxWidth(), label = { Text("解析") }, minLines = 3)
                    OutlinedTextField(scoringPoints, { scoringPoints = it }, Modifier.fillMaxWidth(), label = { Text("得分点（可选）") }, minLines = 2)
                    OutlinedTextField(solutionSteps, { solutionSteps = it }, Modifier.fillMaxWidth(), label = { Text("解题步骤（可选）") }, minLines = 2)
                    Text("知识点 · 已选 ${selectedKnowledge.size}", fontWeight = FontWeight.SemiBold)
                    if (selectedKnowledge.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(knowledgePoints.filter { it.id in selectedKnowledge }, key = { it.id }) { point ->
                                FilterChip(true, { selectedKnowledge = selectedKnowledge - point.id }, label = { Text(point.name) })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            knowledgeQuery,
                            { knowledgeQuery = it },
                            Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("搜索并选择知识点") }
                        )
                        TextButton(onClick = { knowledgeCreateOpen = true }, enabled = subjectId.isNotBlank()) {
                            Text("新建")
                        }
                    }
                    val filteredKnowledge = knowledgePoints.filter {
                        knowledgeQuery.isBlank() || listOf(it.name, it.chapter, it.section).any { field -> field.contains(knowledgeQuery, true) }
                    }
                    LazyColumn(Modifier.fillMaxWidth().height(190.dp)) {
                        items(filteredKnowledge, key = { it.id }) { point ->
                            FilterChip(
                                selected = point.id in selectedKnowledge,
                                onClick = {
                                    selectedKnowledge = if (point.id in selectedKnowledge) selectedKnowledge - point.id else selectedKnowledge + point.id
                                },
                                label = {
                                    Text(listOf(point.name, point.grade, point.chapter, point.section).filter { it.isNotBlank() }.joinToString(" · "))
                                }
                            )
                        }
                    }
                }
                duplicateMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                if (versionConflict && initial != null) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching { api.detail(initial.question.id) }
                                    .onSuccess {
                                        error = null
                                        versionConflict = false
                                        onRefreshed(it)
                                    }
                                    .onFailure { error = it.message }
                            }
                        },
                        enabled = !saving
                    ) { Text("刷新到最新版本（放弃本次修改）") }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { save("DRAFT") }, enabled = !saving) { Text(if (duplicateApproved) "仍要存草稿" else "保存草稿") }
                    Button(onClick = { save("NEEDS_REVIEW") }, enabled = !saving) { Text(if (duplicateApproved) "仍要提交" else "提交审核") }
                }
            }
        }
    }
    if (knowledgeCreateOpen) {
        AlertDialog(
            onDismissRequest = { if (!saving) knowledgeCreateOpen = false },
            title = { Text("新建知识点") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        newKnowledgeName,
                        { newKnowledgeName = it },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("知识点名称") }
                    )
                    OutlinedTextField(
                        newKnowledgeChapter,
                        { newKnowledgeChapter = it },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("章节，如第三章") }
                    )
                    OutlinedTextField(
                        newKnowledgeSection,
                        { newKnowledgeSection = it },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("小节，如第 7 节") }
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newKnowledgeName.isNotBlank() && subjectId.isNotBlank() && !saving,
                    onClick = {
                        scope.launch {
                            saving = true
                            error = null
                            runCatching {
                                api.createKnowledgePoint(
                                    subjectId = subjectId,
                                    name = newKnowledgeName.trim(),
                                    grade = grade.trim(),
                                    textbookVersion = textbookVersion.trim(),
                                    chapter = newKnowledgeChapter.trim(),
                                    section = newKnowledgeSection.trim()
                                )
                            }.onSuccess { point ->
                                knowledgePoints = knowledgePoints + point
                                selectedKnowledge = selectedKnowledge + point.id
                                newKnowledgeName = ""
                                newKnowledgeChapter = ""
                                newKnowledgeSection = ""
                                knowledgeCreateOpen = false
                            }.onFailure { error = it.message ?: "新建知识点失败" }
                            saving = false
                        }
                    }
                ) { Text("创建并选中") }
            },
            dismissButton = {
                TextButton(onClick = { knowledgeCreateOpen = false }, enabled = !saving) { Text("取消") }
            }
        )
    }
}

@Composable
internal fun QuestionSubjectChips(subjects: List<CloudSubject>, selectedId: String, onSelect: (String) -> Unit) {
    Text("学科", fontSize = 12.sp, color = Color.Gray)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(subjects, key = { it.id }) { subject ->
            FilterChip(selectedId == subject.id, { onSelect(subject.id) }, label = { Text(subject.name) })
        }
    }
}
