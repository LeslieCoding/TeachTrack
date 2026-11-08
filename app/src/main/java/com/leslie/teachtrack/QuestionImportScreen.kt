package com.leslie.teachtrack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.leslie.teachtrack.remote.CloudQuestionInput
import com.leslie.teachtrack.remote.CloudSubject
import com.leslie.teachtrack.remote.QuestionApi
import com.leslie.teachtrack.remote.QuestionApiException
import kotlinx.coroutines.launch

private data class ImportPreviewRow(
    val question: ParsedQuestion,
    val knowledge: List<CloudKnowledgePoint> = emptyList(),
    val unmatchedKnowledge: List<String> = emptyList(),
    val duplicates: CloudDuplicateCheck? = null,
    val checking: Boolean = false,
    val checkFailed: String? = null,
    val imported: Boolean = false,
    val status: String = "待检测"
)

@Composable
fun QuestionImportDialog(
    api: QuestionApi,
    onDismiss: () -> Unit,
    onImported: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val rows = remember { mutableStateListOf<ImportPreviewRow>() }
    var subjects by remember { mutableStateOf<List<CloudSubject>>(emptyList()) }
    var knowledgePoints by remember { mutableStateOf<List<CloudKnowledgePoint>>(emptyList()) }
    var subjectId by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("八上") }
    var textbookVersion by remember { mutableStateOf("") }
    var sourceType by remember { mutableStateOf("TEACHER_IMPORTED") }
    var sourceName by remember { mutableStateOf("") }
    var reviewStatus by remember { mutableStateOf("APPROVED") }
    var rawText by remember { mutableStateOf("") }
    var csvMode by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { api.subjects() }
            .onSuccess {
                subjects = it
                subjectId = it.firstOrNull { subject -> subject.name.contains("科学") }?.id
                    ?: it.firstOrNull()?.id.orEmpty()
            }
            .onFailure { error = it.message }
    }
    LaunchedEffect(subjectId) {
        if (subjectId.isBlank()) return@LaunchedEffect
        runCatching { api.knowledgePoints(subjectId) }
            .onSuccess { knowledgePoints = it }
            .onFailure { error = it.message }
    }

    fun importable(row: ImportPreviewRow): Boolean = row.question.errors.isEmpty() &&
        !row.checking && row.checkFailed == null && !row.imported && row.duplicates?.exact == null

    fun parseAndCheck() {
        if (checking || importing) return
        if (subjectId.isBlank()) {
            error = "请选择学科"
            return
        }
        scope.launch {
            checking = true
            error = null
            summary = null
            val parsed = if (csvMode) QuestionImportParser.parseCsv(rawText) else QuestionImportParser.parse(rawText)
            if (parsed.isEmpty()) {
                error = if (csvMode) "没有识别到 CSV 数据。请确认首行包含“题干”列。" else "没有识别到题目。请按题号或空行分隔题目。"
                checking = false
                return@launch
            }
            rows.clear()
            parsed.forEach { question ->
                val match = matchKnowledge(question, knowledgePoints)
                rows += ImportPreviewRow(
                    question = question,
                    knowledge = match.first,
                    unmatchedKnowledge = match.second,
                    checking = question.errors.isEmpty(),
                    status = if (question.errors.isEmpty()) "查重中" else "解析错误"
                )
            }
            previewVisible = true
            for (index in rows.indices) {
                val row = rows[index]
                if (row.question.errors.isNotEmpty()) continue
                progress = "正在查重 ${index + 1}/${rows.size}"
                try {
                    val duplicates = api.duplicates(row.question.stem)
                    rows[index] = rows[index].copy(
                        duplicates = duplicates,
                        checking = false,
                        status = if (duplicates.exact != null) "完全重复，将跳过" else "待导入"
                    )
                } catch (e: Exception) {
                    rows[index] = rows[index].copy(
                        checking = false,
                        checkFailed = e.message ?: "查重失败",
                        status = "查重失败"
                    )
                }
            }
            progress = ""
            checking = false
        }
    }

    fun inputFor(row: ImportPreviewRow): CloudQuestionInput = CloudQuestionInput(
        subjectId = subjectId,
        grade = row.question.grade.ifBlank { grade.trim() },
        textbookVersion = row.question.textbookVersion.ifBlank { textbookVersion.trim() },
        questionType = row.question.questionType,
        difficulty = row.question.difficulty,
        stem = row.question.stem,
        sourceType = sourceType,
        sourceName = row.question.sourceName.ifBlank { sourceName.trim() },
        reviewStatus = reviewStatus,
        options = row.question.options,
        standardAnswer = row.question.standardAnswer,
        analysis = row.question.analysis,
        knowledgePointIds = row.knowledge.map { it.id },
        abilityTags = if (row.question.requiresImage) listOf("含图待补") else emptyList()
    )

    fun importAll() {
        if (checking || importing) return
        scope.launch {
            importing = true
            error = null
            summary = null
            var created = 0
            var duplicates = 0
            var failed = 0
            val targets = rows.mapIndexedNotNull { index, row -> if (importable(row)) index else null }
            for ((position, index) in targets.withIndex()) {
                progress = "正在导入 ${position + 1}/${targets.size}"
                rows[index] = rows[index].copy(status = "导入中")
                try {
                    val effectiveStatus = if (rows[index].question.requiresImage) "DRAFT" else reviewStatus
                    api.create(inputFor(rows[index]).copy(reviewStatus = effectiveStatus))
                    rows[index] = rows[index].copy(imported = true, status = "已导入")
                    created++
                } catch (e: QuestionApiException) {
                    if (e.code == "DUPLICATE_QUESTION") {
                        rows[index] = rows[index].copy(status = "服务端重复，已跳过")
                        duplicates++
                    } else {
                        rows[index] = rows[index].copy(status = "导入失败：${e.message}")
                        failed++
                    }
                } catch (e: Exception) {
                    rows[index] = rows[index].copy(status = "导入失败：${e.message ?: "未知错误"}")
                    failed++
                }
            }
            progress = ""
            importing = false
            summary = "导入完成：新增 $created，重复跳过 $duplicates，失败 $failed"
            if (created > 0) onImported(created)
        }
    }

    Dialog(onDismissRequest = { if (!importing) onDismiss() }) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.94f), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("文本批量导入", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("先预览、查重，再入库；失败和重复项不会静默丢弃", fontSize = 12.sp, color = Color.Gray)
                    }
                    TextButton(onClick = onDismiss, enabled = !importing) { Text("关闭") }
                }
                if (checking || importing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                if (progress.isNotBlank()) Text(progress, fontSize = 12.sp, color = Color.Gray)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                summary?.let { Text(it, color = QbGreen, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }

                if (!previewVisible) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuestionSubjectChips(subjects, subjectId) { subjectId = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(grade, { grade = it }, Modifier.weight(1f), label = { Text("默认年级") }, singleLine = true)
                            OutlinedTextField(textbookVersion, { textbookVersion = it }, Modifier.weight(1f), label = { Text("教材版本") }, singleLine = true)
                        }
                        Text("来源", fontSize = 12.sp, color = Color.Gray)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(QUESTION_SOURCE_OPTIONS) { _, option ->
                                FilterChip(sourceType == option.first, { sourceType = option.first }, label = { Text(option.second) })
                            }
                        }
                        OutlinedTextField(sourceName, { sourceName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("来源名称（可选）") })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(reviewStatus == "APPROVED", { reviewStatus = "APPROVED" }, label = { Text("完整题直接通过") })
                            FilterChip(reviewStatus == "DRAFT", { reviewStatus = "DRAFT" }, label = { Text("导入为草稿") })
                            FilterChip(reviewStatus == "NEEDS_REVIEW", { reviewStatus = "NEEDS_REVIEW" }, label = { Text("导入后待审核") })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!csvMode, { csvMode = false }, label = { Text("纯文本") })
                            FilterChip(csvMode, { csvMode = true }, label = { Text("CSV") })
                        }
                        Text(
                            if (csvMode) {
                                "CSV 首行支持：题干, optionA–optionH, 答案, 解析, 题型, 难度, 知识点, 年级, 教材, 来源。也支持中英文表头，字段可含逗号或换行。"
                            } else {
                                "按“1. 题干”或空行分隔；支持 A. 选项、答案、解析、难度、知识点、年级、教材、来源、题型标记。含图题只保存文字并标记“含图待补”。"
                            },
                            fontSize = 12.sp, color = Color.Gray
                        )
                        OutlinedTextField(
                            rawText,
                            { rawText = it },
                            Modifier.fillMaxWidth(),
                            label = { Text(if (csvMode) "粘贴 CSV" else "粘贴题目纯文本") },
                            minLines = 12,
                            maxLines = 24,
                            placeholder = {
                                Text(
                                    if (csvMode) {
                                        "题干,optionA,optionB,答案,解析,题型,难度,知识点\n\"液体压强与哪些因素有关？\",\"深度\",\"密度\",\"AB\",\"同种液体…\",\"多选\",3,\"液体压强\""
                                    } else {
                                        "1. 液体内部压强有什么特点？（　　）\nA. 各方向相等\nB. 各方向不等\n答案：A\n解析：同种液体同一深度压强相等\n难度：3\n知识点：液体压强"
                                    }
                                )
                            }
                        )
                    }
                    Button(onClick = { parseAndCheck() }, enabled = !checking && rawText.isNotBlank(), modifier = Modifier.padding(top = 10.dp)) {
                        Text("解析预览并查重")
                    }
                } else {
                    val invalidCount = rows.count { it.question.errors.isNotEmpty() || it.checkFailed != null }
                    val duplicateCount = rows.count { it.duplicates?.exact != null }
                    val importableCount = rows.count(::importable)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "共 ${rows.size} 条 · 可导入 $importableCount · 错误 $invalidCount · 完全重复 $duplicateCount",
                            Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = { previewVisible = false }, enabled = !importing) { Text("返回修改文本") }
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(rows) { index, row -> ImportPreviewCard(index, row) }
                    }
                    Button(onClick = { importAll() }, enabled = !checking && !importing && importableCount > 0) {
                        Text(if (rows.any { it.imported }) "重试剩余 $importableCount 条" else "导入 $importableCount 道题")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportPreviewCard(index: Int, row: ImportPreviewRow) {
    val question = row.question
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}. ${question.stem.take(180)}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(row.status, fontSize = 12.sp, color = if (row.imported) QbGreen else Color(0xFFB26A00))
            }
            Text(
                "题型：${QUESTION_TYPE_OPTIONS.firstOrNull { it.first == question.questionType }?.second ?: question.questionType}" +
                    " · 难度 ${question.difficulty} · ${question.options.size} 个选项" +
                    if (question.requiresImage) " · 含图待补" else "",
                fontSize = 12.sp, color = Color.Gray
            )
            if (question.standardAnswer.isNotBlank()) Text("答案：${question.standardAnswer.take(100)}", fontSize = 12.sp, color = QbGreen)
            Text(
                when {
                    row.knowledge.isNotEmpty() -> "知识点：${row.knowledge.joinToString("、") { it.name }}"
                    row.unmatchedKnowledge.isNotEmpty() -> "未匹配知识点：${row.unmatchedKnowledge.joinToString("、")}"
                    else -> "知识点：未匹配，可入库后在编辑页补充"
                },
                fontSize = 12.sp,
                color = if (row.knowledge.isEmpty()) Color(0xFFB26A00) else Color.DarkGray
            )
            row.duplicates?.near?.let { Text("规范化重复提醒：${it.stem.take(100)}", fontSize = 12.sp, color = Color(0xFFB26A00)) }
            if (row.duplicates?.similar?.isNotEmpty() == true) {
                Text("相似题 ${row.duplicates.similar.size} 道，请在入库前核对", fontSize = 12.sp, color = Color(0xFFB26A00))
            }
            row.duplicates?.exact?.let { Text("完全重复：${it.stem.take(100)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            row.checkFailed?.let { Text("查重失败：$it（可返回后重试）", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            question.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }
}

internal fun matchKnowledge(
    question: ParsedQuestion,
    available: List<CloudKnowledgePoint>
): Pair<List<CloudKnowledgePoint>, List<String>> {
    if (question.knowledgeNames.isEmpty()) {
        val inferred = available
            .filter { it.name.length >= 2 && question.stem.contains(it.name, true) }
            .sortedByDescending { it.name.length }
            .take(5)
        return inferred to emptyList()
    }
    val matches = mutableListOf<CloudKnowledgePoint>()
    val unmatched = mutableListOf<String>()
    question.knowledgeNames.forEach { requested ->
        val exact = available.firstOrNull { it.name.equals(requested, true) }
        val fuzzy = available
            .filter { it.name.contains(requested, true) || requested.contains(it.name, true) }
            .maxByOrNull { it.name.length }
        val match = exact ?: fuzzy
        if (match == null) unmatched += requested else if (matches.none { it.id == match.id }) matches += match
    }
    return matches to unmatched
}
