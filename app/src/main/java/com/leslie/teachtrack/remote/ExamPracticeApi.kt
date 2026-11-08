package com.leslie.teachtrack.remote

import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class CloudSubject(val id: String, val name: String)
data class CloudExamQuestion(
    val id: String,
    val number: String,
    val type: String,
    val score: Double,
    val fullScore: Double,
    val stem: String
)
data class CloudExamAnalysis(
    val examQuestionId: String,
    val mistakeType: String,
    val knowledgeSummary: String,
    val taughtBefore: Boolean
)
data class CloudExamDetail(
    val id: String,
    val status: String,
    val questions: List<CloudExamQuestion>,
    val analyses: List<CloudExamAnalysis>
)
data class CloudPracticeItem(val id: String, val stem: String, val score: Double, val sourceKind: String)
data class CloudMastery(
    val knowledgePointId: String,
    val masteryScore: Double,
    val confidence: Double,
    val correctCount: Int,
    val wrongCount: Int
)
data class CloudPractice(
    val id: String,
    val title: String,
    val status: String,
    val items: List<CloudPracticeItem>,
    val mastery: List<CloudMastery> = emptyList()
)

class ExamPracticeApi(
    private val baseUrl: String,
    private val token: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    suspend fun subjects(): List<CloudSubject> = withContext(Dispatchers.IO) {
        val array = getJsonArray("/api/v1/subjects")
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(CloudSubject(item.getString("id"), item.optString("name")))
            }
        }
    }

    suspend fun resolveStudent(localId: String): String = withContext(Dispatchers.IO) {
        getJson("/api/v1/students/${URLEncoder.encode(localId, "UTF-8")}").getString("id")
    }

    suspend fun importExam(studentId: String, subjectId: String, name: String, date: String): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("studentId", studentId)
                .put("subjectId", subjectId)
                .put("examName", name)
                .put("examDate", date)
            postJson("/api/v1/exams/import", body).getString("id")
        }

    suspend fun analyzeExam(examId: String, text: String): CloudExamDetail = withContext(Dispatchers.IO) {
        parseExam(postJson("/api/v1/exams/$examId/analyze", JSONObject().put("text", text)))
    }

    suspend fun approveExam(examId: String): CloudExamDetail = withContext(Dispatchers.IO) {
        parseExam(postJson("/api/v1/exams/$examId/approve?importToBank=true", JSONObject()))
    }

    suspend fun approvedQuestionCount(subjectId: String): Int = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(subjectId, "UTF-8")
        getJson("/api/v1/questions?reviewStatus=APPROVED&subjectId=$encoded&limit=1")
            .optInt("total", 0)
    }

    suspend fun generatePractice(
        studentId: String,
        subjectId: String,
        count: Int,
        mode: String
    ): CloudPractice = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("studentId", studentId)
            .put("subjectId", subjectId)
            .put("questionCount", count)
            .put("mode", mode)
            .put("allowAi", false)
        parsePractice(postJson("/api/v1/practices/generate", body))
    }

    suspend fun approvePractice(id: String): CloudPractice = withContext(Dispatchers.IO) {
        parsePractice(postJson("/api/v1/practices/$id/approve", JSONObject()))
    }

    suspend fun submitPractice(id: String, answers: Map<String, String>): CloudPractice =
        withContext(Dispatchers.IO) {
            val payload = JSONArray()
            answers.forEach { (itemId, answer) ->
                payload.put(JSONObject().put("itemId", itemId).put("studentAnswer", answer))
            }
            parsePractice(postJson("/api/v1/practices/$id/submit",
                JSONObject().put("answers", payload)))
        }

    suspend fun exportPractice(id: String, variant: String, format: String): String =
        withContext(Dispatchers.IO) {
            getJson("/api/v1/practices/$id/export?variant=$variant&format=$format").getString("downloadUrl")
        }

    private fun parseExam(root: JSONObject): CloudExamDetail {
        val paper = root.optJSONObject("paper") ?: JSONObject()
        val questions = mutableListOf<CloudExamQuestion>()
        root.optJSONArray("questions")?.let { array ->
            for (i in 0 until array.length()) {
                val q = array.getJSONObject(i)
                questions += CloudExamQuestion(
                    id = q.getString("id"),
                    number = q.optString("questionNumber"),
                    type = q.optString("questionType"),
                    score = q.optDouble("score", 0.0),
                    fullScore = q.optDouble("fullScore", 0.0),
                    stem = q.optString("stem")
                )
            }
        }
        val analyses = mutableListOf<CloudExamAnalysis>()
        root.optJSONArray("analyses")?.let { array ->
            for (i in 0 until array.length()) {
                val a = array.getJSONObject(i)
                analyses += CloudExamAnalysis(
                    examQuestionId = a.optString("examQuestionId"),
                    mistakeType = a.optString("mistakeType"),
                    knowledgeSummary = a.optString("knowledgeSummary"),
                    taughtBefore = a.optBoolean("taughtBefore", false)
                )
            }
        }
        return CloudExamDetail(
            id = paper.optString("id"),
            status = paper.optString("analysisStatus"),
            questions = questions,
            analyses = analyses
        )
    }

    private fun parsePractice(root: JSONObject): CloudPractice {
        val set = root.optJSONObject("set") ?: JSONObject()
        val items = mutableListOf<CloudPracticeItem>()
        root.optJSONArray("items")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                items += CloudPracticeItem(
                    id = item.getString("id"),
                    stem = item.optString("stem"),
                    score = item.optDouble("score", 0.0),
                    sourceKind = item.optString("sourceKind")
                )
            }
        }
        val mastery = mutableListOf<CloudMastery>()
        root.optJSONArray("mastery")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                mastery += CloudMastery(
                    knowledgePointId = item.optString("knowledgePointId"),
                    masteryScore = item.optDouble("masteryScore", 0.0),
                    confidence = item.optDouble("confidence", 0.0),
                    correctCount = item.optInt("correctCount"),
                    wrongCount = item.optInt("wrongCount")
                )
            }
        }
        return CloudPractice(
            id = set.optString("id"),
            title = set.optString("title"),
            status = set.optString("status"),
            items = items,
            mastery = mastery
        )
    }

    private fun getJson(path: String): JSONObject {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return execute(request)
    }

    private fun getJsonArray(path: String): JSONArray {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(errorMessage(text, response.code))
            return JSONArray(text)
        }
    }

    private fun postJson(path: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON))
            .build()
        return execute(request)
    }

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(errorMessage(text, response.code))
            return JSONObject(text)
        }
    }

    private fun errorMessage(body: String, code: Int): String = runCatching {
        JSONObject(body).getJSONObject("error").optString("message")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $code"

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
