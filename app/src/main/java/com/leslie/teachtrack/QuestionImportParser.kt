package com.leslie.teachtrack

data class ParsedQuestion(
    val number: Int,
    val stem: String,
    val options: List<Pair<String, String>>,
    val questionType: String,
    val difficulty: Int,
    val standardAnswer: String,
    val analysis: String,
    val knowledgeNames: List<String>,
    val grade: String,
    val textbookVersion: String,
    val sourceName: String,
    val requiresImage: Boolean,
    val errors: List<String>
)

object QuestionImportParser {
    private val questionNumber = Regex("""^\s*\d{1,3}\s*[.、．](?!\d)\s*""", RegexOption.MULTILINE)
    private val leadingQuestionNumber = Regex("""^\s*\d{1,3}\s*[.、．](?!\d)\s*""")
    private val optionLine = Regex("""^\s*([A-H])\s*[.、．:：)]\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val answerLine = Regex("""^【?(?:参考答案|答案)】?\s*[:：]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val analysisLine = Regex("""^【?(?:解析|解答|分析)】?\s*[:：]?\s*(.*)$""")
    private val difficultyLine = Regex("""^【?难度】?\s*[:：]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val knowledgeLine = Regex("""^【?知识点】?\s*[:：]?\s*(.*)$""")
    private val gradeLine = Regex("""^【?年级】?\s*[:：]?\s*(.*)$""")
    private val textbookLine = Regex("""^【?(?:教材版本|教材)】?\s*[:：]?\s*(.*)$""")
    private val sourceLine = Regex("""^【?来源】?\s*[:：]?\s*(.*)$""")
    private val typeLine = Regex("""^【?题型】?\s*[:：]?\s*(.*)$""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): List<ParsedQuestion> {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return emptyList()
        val markers = questionNumber.findAll(text).toList()
        val blocks = if (markers.isNotEmpty()) {
            markers.mapIndexed { index, marker ->
                val end = markers.getOrNull(index + 1)?.range?.first ?: text.length
                text.substring(marker.range.first, end).trim()
            }.filter { it.isNotBlank() }
        } else {
            text.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotBlank() }
        }
        return blocks.mapIndexed { index, block -> parseBlock(index + 1, block) }
    }

    fun parseCsv(raw: String): List<ParsedQuestion> {
        val rows = parseCsvRows(raw.replace("\r\n", "\n").replace('\r', '\n'))
        if (rows.size < 2) return emptyList()
        val header = rows.first().map(::normalizeHeader)
        fun column(vararg names: String): Int? {
            val normalized = names.map(::normalizeHeader).toSet()
            return header.indexOfFirst { it in normalized }.takeIf { it >= 0 }
        }
        val stemIndex = column("题干", "题目", "stem", "question", "questionStem") ?: return emptyList()
        val optionIndexes = ('A'..'H').map { key ->
            key.toString() to column("option$key", "选项$key", key.toString())
        }.filter { it.second != null }.map { it.first to it.second!! }
        val answerIndex = column("答案", "标准答案", "answer", "standardAnswer")
        val analysisIndex = column("解析", "analysis", "解答")
        val typeIndex = column("题型", "type", "questionType")
        val difficultyIndex = column("难度", "difficulty")
        val knowledgeIndex = column("知识点", "knowledge", "knowledgePoint")
        val gradeIndex = column("年级", "grade")
        val textbookIndex = column("教材", "教材版本", "textbook", "textbookVersion")
        val sourceIndex = column("来源", "source", "sourceName")

        return rows.drop(1).mapIndexedNotNull { rowIndex, row ->
            fun value(index: Int?): String = index?.let { row.getOrNull(it).orEmpty().trim() }.orEmpty()
            val stem = value(stemIndex)
            if (row.all { it.isBlank() }) return@mapIndexedNotNull null
            val options = optionIndexes.mapNotNull { (key, index) ->
                value(index).takeIf { it.isNotEmpty() }?.let { key to it }
            }
            val answer = value(answerIndex)
            val explicitType = typeIndex?.let { parseType(value(it)) }.orEmpty()
            val type = explicitType.ifBlank { detectType(stem, options, answer) }
            val difficulty = difficultyIndex?.let { parseDifficulty(value(it)) } ?: 3
            val errors = mutableListOf<String>()
            if (stem.isBlank()) errors += "缺少题干"
            if (difficultyIndex != null && value(difficultyIndex).isNotBlank() && parseDifficulty(value(difficultyIndex)) == null) {
                errors += "无法识别难度：${value(difficultyIndex)}"
            }
            if (typeIndex != null && value(typeIndex).isNotBlank() && explicitType.isBlank()) {
                errors += "无法识别题型：${value(typeIndex)}"
            }
            if (type in CHOICE_TYPES) {
                if (options.size < 2) errors += "选择题至少需要 2 个选项"
                if (answer.isBlank()) errors += "选择题缺少答案"
            }
            val knowledgeNames = value(knowledgeIndex)
                .split(Regex("""[、，,;；]"""))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            ParsedQuestion(
                number = rowIndex + 1,
                stem = stem,
                options = options,
                questionType = type,
                difficulty = difficulty,
                standardAnswer = answer,
                analysis = value(analysisIndex),
                knowledgeNames = knowledgeNames,
                grade = value(gradeIndex),
                textbookVersion = value(textbookIndex),
                sourceName = value(sourceIndex),
                requiresImage = IMAGE_REFERENCE.containsMatchIn(stem),
                errors = errors
            )
        }
    }

    private fun normalizeHeader(value: String): String =
        value.removePrefix("\uFEFF").trim().lowercase().replace("_", "").replace(" ", "")

    private fun parseCsvRows(raw: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            if (quoted) {
                if (char == '"') {
                    if (index + 1 < raw.length && raw[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(char)
                }
            } else {
                when (char) {
                    '"' -> quoted = true
                    ',' -> {
                        row += field.toString().trim()
                        field.clear()
                    }
                    '\n' -> {
                        row += field.toString().trim()
                        field.clear()
                        if (row.any { it.isNotBlank() }) rows += row.toList()
                        row.clear()
                    }
                    else -> field.append(char)
                }
            }
            index++
        }
        row += field.toString().trim()
        if (row.any { it.isNotBlank() }) rows += row.toList()
        return rows
    }

    private fun parseBlock(number: Int, block: String): ParsedQuestion {
        val lines = block.replaceFirst(leadingQuestionNumber, "").lines()
        val stem = mutableListOf<String>()
        val options = mutableListOf<Pair<String, MutableList<String>>>()
        val answer = mutableListOf<String>()
        val analysis = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var knowledgeNames = emptyList<String>()
        var grade = ""
        var textbookVersion = ""
        var sourceName = ""
        var explicitType = ""
        var difficulty = 3
        var section = Section.STEM

        for (rawLine in lines) {
            val line = rawLine.trim()
            val option = optionLine.matchEntire(line)
            val answerMatch = answerLine.matchEntire(line)
            val analysisMatch = analysisLine.matchEntire(line)
            val difficultyMatch = difficultyLine.matchEntire(line)
            val knowledgeMatch = knowledgeLine.matchEntire(line)
            val gradeMatch = gradeLine.matchEntire(line)
            val textbookMatch = textbookLine.matchEntire(line)
            val sourceMatch = sourceLine.matchEntire(line)
            val typeMatch = typeLine.matchEntire(line)
            when {
                answerMatch != null -> {
                    section = Section.ANSWER
                    answerMatch.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let(answer::add)
                }
                analysisMatch != null -> {
                    section = Section.ANALYSIS
                    analysisMatch.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let(analysis::add)
                }
                difficultyMatch != null -> {
                    section = Section.METADATA
                    val value = difficultyMatch.groupValues[1].trim()
                    difficulty = parseDifficulty(value) ?: run {
                        errors += "无法识别难度：$value"
                        3
                    }
                }
                knowledgeMatch != null -> {
                    section = Section.METADATA
                    knowledgeNames = knowledgeMatch.groupValues[1]
                        .split(Regex("""[、，,;；]"""))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                }
                gradeMatch != null -> {
                    section = Section.METADATA
                    grade = gradeMatch.groupValues[1].trim()
                }
                textbookMatch != null -> {
                    section = Section.METADATA
                    textbookVersion = textbookMatch.groupValues[1].trim()
                }
                sourceMatch != null -> {
                    section = Section.METADATA
                    sourceName = sourceMatch.groupValues[1].trim()
                }
                typeMatch != null -> {
                    section = Section.METADATA
                    explicitType = parseType(typeMatch.groupValues[1])
                    if (explicitType.isEmpty()) errors += "无法识别题型：${typeMatch.groupValues[1]}"
                }
                option != null && section in setOf(Section.STEM, Section.OPTIONS) -> {
                    section = Section.OPTIONS
                    options += option.groupValues[1].uppercase() to mutableListOf(option.groupValues[2].trim())
                }
                section == Section.ANALYSIS -> if (line.isNotEmpty()) analysis += line
                section == Section.ANSWER -> if (line.isNotEmpty()) answer += line
                section == Section.OPTIONS -> if (line.isNotEmpty()) options.last().second += line
                section == Section.STEM -> if (line.isNotEmpty()) stem += line
            }
        }

        val cleanStem = stem.joinToString("\n").trim()
        val cleanOptions = options.map { (key, content) -> key to content.joinToString("\n").trim() }
        val cleanAnswer = answer.joinToString("\n").trim()
        val questionType = explicitType.ifBlank { detectType(cleanStem, cleanOptions, cleanAnswer) }
        if (cleanStem.isBlank()) errors += "缺少题干"
        if (questionType in CHOICE_TYPES) {
            if (cleanOptions.size < 2) errors += "选择题至少需要 2 个选项"
            if (cleanAnswer.isBlank()) errors += "选择题缺少答案"
        }
        if (cleanOptions.any { it.second.isBlank() }) errors += "存在空选项"
        if (cleanOptions.isNotEmpty() && questionType !in CHOICE_TYPES) errors += "选项与题型不一致"

        return ParsedQuestion(
            number = number,
            stem = cleanStem,
            options = cleanOptions,
            questionType = questionType,
            difficulty = difficulty,
            standardAnswer = cleanAnswer,
            analysis = analysis.joinToString("\n").trim(),
            knowledgeNames = knowledgeNames,
            grade = grade,
            textbookVersion = textbookVersion,
            sourceName = sourceName,
            requiresImage = IMAGE_REFERENCE.containsMatchIn(cleanStem),
            errors = errors
        )
    }

    private fun detectType(stem: String, options: List<Pair<String, String>>, answer: String): String {
        if (options.isNotEmpty()) {
            val answerLetters = Regex("[A-H]").findAll(answer.uppercase()).count()
            return if (answerLetters > 1) "MULTI_CHOICE" else "SINGLE_CHOICE"
        }
        if (Regex("""实验|探究|器材|实验装置|实验现象""").containsMatchIn(stem)) return "EXPERIMENT"
        if (Regex("""_{3,}|（\s*）|\(\s*\)|（　+）""").containsMatchIn(stem)) return "FILL_BLANK"
        if (Regex("""计算|求出|是多少|结果为|多少[克米秒安伏欧瓦帕]|数值和单位""").containsMatchIn(stem)) return "CALCULATION"
        if (Regex("""简答|说明理由|解释|原因|谈谈|论述""").containsMatchIn(stem)) return "SHORT_ANSWER"
        return "OTHER"
    }

    private fun parseDifficulty(value: String): Int? = when {
        value.toIntOrNull()?.let { it in 1..5 } == true -> value.toInt()
        value.contains("容易") || value.contains("简单") -> 1
        value.contains("较易") -> 2
        value.contains("中等") || value.contains("普通") -> 3
        value.contains("较难") -> 4
        value.contains("困难") || value.contains("很难") -> 5
        else -> null
    }

    private fun parseType(value: String): String = when (value.trim().uppercase()) {
        "单选", "单选题", "SINGLE_CHOICE" -> "SINGLE_CHOICE"
        "多选", "多选题", "MULTI_CHOICE" -> "MULTI_CHOICE"
        "填空", "填空题", "FILL_BLANK" -> "FILL_BLANK"
        "实验", "实验题", "实验探究", "EXPERIMENT" -> "EXPERIMENT"
        "计算", "计算题", "CALCULATION" -> "CALCULATION"
        "简答", "简答题", "SHORT_ANSWER" -> "SHORT_ANSWER"
        "论述", "论述题", "ESSAY" -> "ESSAY"
        "其他", "OTHER" -> "OTHER"
        else -> ""
    }

    private enum class Section { STEM, OPTIONS, ANSWER, ANALYSIS, METADATA }

    private val CHOICE_TYPES = setOf("SINGLE_CHOICE", "MULTI_CHOICE")
    private val IMAGE_REFERENCE = Regex("""如图|见图|右图|左图|下图|上图|图所示|图甲|图乙|电路图|装置图|图片""")
}
