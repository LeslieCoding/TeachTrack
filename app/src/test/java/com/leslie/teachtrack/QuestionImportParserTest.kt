package com.leslie.teachtrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionImportParserTest {
    @Test fun parsesNumberedChoiceQuestionsAndMetadata() {
        val rows = QuestionImportParser.parse(
            """
            1. 如图，同种液体同一深度向各方向的压强有什么关系？（　　）
            A. 相等
            B. 不相等
            C. 无法判断
            答案：A
            解析：液体内部同一深度各方向压强相等。
            难度：3
            知识点：液体压强、压强
            年级：八上
            教材：浙教版
            来源：2026 温州期末

            2. 下列哪些是电路基本元件？（　　）
            A. 电源
            B. 开关
            C. 导线
            答案：ABC
            难度：较难
            """.trimIndent()
        )
        assertEquals(2, rows.size)
        assertEquals("SINGLE_CHOICE", rows[0].questionType)
        assertEquals(3, rows[0].options.size)
        assertEquals("A", rows[0].standardAnswer)
        assertEquals(listOf("液体压强", "压强"), rows[0].knowledgeNames)
        assertEquals("八上", rows[0].grade)
        assertEquals("浙教版", rows[0].textbookVersion)
        assertEquals("2026 温州期末", rows[0].sourceName)
        assertTrue(rows[0].requiresImage)
        assertEquals("MULTI_CHOICE", rows[1].questionType)
        assertEquals(4, rows[1].difficulty)
        assertFalse(rows[1].requiresImage)
        assertTrue(rows.all { it.errors.isEmpty() })
    }

    @Test fun splitsBlankSeparatedQuestionsAndDetectsNonChoiceTypes() {
        val rows = QuestionImportParser.parse(
            """
            液体压强公式中的深度是指________。
            答案：研究点到液面的竖直距离
            题型：填空

            一个物体重 20 N，受力面积为 0.5 m²，计算它对地面的压强。
            答案：40 Pa
            解析：p=F/S=20/0.5=40 Pa。
            """.trimIndent()
        )
        assertEquals(2, rows.size)
        assertEquals("FILL_BLANK", rows[0].questionType)
        assertEquals("CALCULATION", rows[1].questionType)
        assertEquals("40 Pa", rows[1].standardAnswer)
        assertTrue(rows[1].analysis.contains("p=F/S"))
    }

    @Test fun keepsUnrecognizedItemsVisibleWithErrors() {
        val rows = QuestionImportParser.parse(
            """
            1. 没有答案的选择题？（　　）
            A. 甲
            B. 乙
            难度：超难

            2. 只有题干，没有选项却声明单选。
            题型：单选
            答案：A
            """.trimIndent()
        )
        assertEquals(2, rows.size)
        assertTrue(rows[0].errors.any { it.contains("缺少答案") })
        assertTrue(rows[0].errors.any { it.contains("无法识别难度") })
        assertTrue(rows[1].errors.any { it.contains("至少需要 2 个选项") })
    }

    @Test fun decimalAtLineStartIsNotAQuestionNumber() {
        val rows = QuestionImportParser.parse("1.5 千克的物体受到多少牛的重力？\n答案：约 14.7 N")
        assertEquals(1, rows.size)
        assertTrue(rows[0].stem.startsWith("1.5 千克"))
    }

    @Test fun parsesFiftyQuestionsWithoutDroppingAny() {
        val text = (1..50).joinToString("\n\n") { number ->
            "$number. 第 ${number} 道液体压强选择题？（　　）\nA. 正确\nB. 错误\n答案：A\n难度：3"
        }
        val rows = QuestionImportParser.parse(text)
        assertEquals(50, rows.size)
        assertEquals((1..50).toList(), rows.map { it.number })
        assertTrue(rows.all { it.errors.isEmpty() && it.questionType == "SINGLE_CHOICE" })
    }

    @Test fun knowledgeMatchingUsesExplicitNamesOrStemInference() {
        val points = listOf(
            com.leslie.teachtrack.remote.CloudKnowledgePoint("kp1", "液体压强", "八上", "第三章", "第 7 节"),
            com.leslie.teachtrack.remote.CloudKnowledgePoint("kp2", "压强", "八上", "第三章", "第 1 节"),
            com.leslie.teachtrack.remote.CloudKnowledgePoint("kp3", "电功率", "九上", "第三章", "第 6 节")
        )
        val explicit = QuestionImportParser.parse("比较不同液体的压强。\n知识点：液体压强、未知点").single()
        val explicitMatch = matchKnowledge(explicit, points)
        assertEquals(listOf("kp1"), explicitMatch.first.map { it.id })
        assertEquals(listOf("未知点"), explicitMatch.second)

        val inferred = QuestionImportParser.parse("计算用电器的电功率。").single()
        assertEquals(listOf("kp3"), matchKnowledge(inferred, points).first.map { it.id })
    }
}
