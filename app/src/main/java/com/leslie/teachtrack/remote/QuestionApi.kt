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

data class CloudQuestion(
    val id: String,
    val subjectId: String,
    val grade: String,
    val questionType: String,
    val difficulty: Int,
    val stem: String,
    val reviewStatus: String,
    val sourceType: String,
    val version: Int,
    val textbookVersion: String = "",
    val sourceName: String = ""
)

data class CloudQuestionPage(
    val items: List<CloudQuestion>,
    val limit: Int,
    val offset: Int,
    val total: Long
)

data class CloudKnowledgeLink(val knowledgePointId: String, val importance: String)

data class CloudQuestionImage(val assetId: String, val url: String)

data class CloudQuestionDetail(
    val question: CloudQuestion,
    val options: List<Pair<String, String>>,
    val standardAnswer: String,
    val analysis: String,
    val scoringPoints: String = "",
    val solutionSteps: String = "",
    val knowledge: List<CloudKnowledgeLink> = emptyList(),
    val abilityTags: List<String> = emptyList()
)

data class CloudKnowledgePoint(
    val id: String,
    val name: String,
    val grade: String,
    val chapter: String,
    val section: String
)

data class CloudQuestionInput(
    val subjectId: String,
    val grade: String,
    val textbookVersion: String,
    val questionType: String,
    val difficulty: Int,
    val stem: String,
    val sourceType: String,
    val sourceName: String,
    val reviewStatus: String,
    val options: List<Pair<String, String>>,
    val standardAnswer: String,
    val analysis: String,
    val scoringPoints: String = "",
    val solutionSteps: String = "",
    val knowledgePointIds: List<String> = emptyList(),
    val abilityTags: List<String> = emptyList(),
    val baseVersion: Int? = null
)

data class CloudDuplicateCheck(
    val exact: CloudQuestion?,
    val near: CloudQuestion?,
    val similar: List<CloudQuestion>
)

data class CloudBulkResult(val requested: Int, val succeeded: Int, val failed: List<String>)

class QuestionApiException(
    val status: Int,
    val code: String,
    message: String
) : IOException(message)

class QuestionApi(
    private val baseUrl: String,
    private val token: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun search(
        query: String,
        limit: Int = 100,
        subjectId: String = "",
        grade: String = "",
        questionType: String = "",
        difficulty: Int? = null,
        reviewStatus: String = "",
        knowledgePointId: String = ""
    ): List<CloudQuestion> = searchPage(
        query = query,
        limit = limit,
        offset = 0,
        subjectId = subjectId,
        grade = grade,
        questionType = questionType,
        difficulty = difficulty,
        reviewStatus = reviewStatus,
        knowledgePointId = knowledgePointId
    ).items

    suspend fun searchPage(
        query: String,
        limit: Int = 100,
        offset: Int = 0,
        subjectId: String = "",
        grade: String = "",
        questionType: String = "",
        difficulty: Int? = null,
        reviewStatus: String = "",
        knowledgePointId: String = ""
    ): CloudQuestionPage = withContext(Dispatchers.IO) {
        val params = buildList {
            add("limit=$limit")
            add("offset=$offset")
            if (query.isNotBlank()) add("q=${URLEncoder.encode(query, "UTF-8")}")
            if (subjectId.isNotBlank()) add("subjectId=${URLEncoder.encode(subjectId, "UTF-8")}")
            if (grade.isNotBlank()) add("grade=${URLEncoder.encode(grade, "UTF-8")}")
            if (questionType.isNotBlank()) add("questionType=${URLEncoder.encode(questionType, "UTF-8")}")
            if (difficulty != null) {
                add("minDifficulty=$difficulty")
                add("maxDifficulty=$difficulty")
            }
            if (reviewStatus.isNotBlank()) add("reviewStatus=${URLEncoder.encode(reviewStatus, "UTF-8")}")
            if (knowledgePointId.isNotBlank()) add("knowledgePointId=${URLEncoder.encode(knowledgePointId, "UTF-8")}")
        }
        val root = getJson("/api/v1/questions?${params.joinToString("&")}")
        val array = root.optJSONArray("items")
        val pageItems = buildList {
            if (array != null) {
                for (i in 0 until array.length()) add(parseQuestion(array.getJSONObject(i)))
            }
        }
        CloudQuestionPage(
            items = pageItems,
            limit = root.optInt("limit", limit),
            offset = root.optInt("offset", offset),
            total = root.optLong("total", pageItems.size.toLong())
        )
    }

    suspend fun bulkReview(ids: List<String>, status: String): CloudBulkResult = withContext(Dispatchers.IO) {
        parseBulk(postJson("/api/v1/questions/bulk-review",
            JSONObject().put("ids", JSONArray(ids)).put("status", status)))
    }

    suspend fun bulkDelete(ids: List<String>): CloudBulkResult = withContext(Dispatchers.IO) {
        parseBulk(postJson("/api/v1/questions/bulk-delete", JSONObject().put("ids", JSONArray(ids))))
    }

    private fun parseBulk(root: JSONObject): CloudBulkResult {
        val failed = mutableListOf<String>()
        root.optJSONArray("failed")?.let { array ->
            for (i in 0 until array.length()) failed += array.optString(i)
        }
        return CloudBulkResult(
            requested = root.optInt("requested"),
            succeeded = root.optInt("succeeded"),
            failed = failed
        )
    }

    private fun postJson(path: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(url(path))
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON))
            .build()
        return executeJson(request)
    }

    suspend fun detail(id: String): CloudQuestionDetail = withContext(Dispatchers.IO) {
        parseDetail(getJson("/api/v1/questions/${URLEncoder.encode(id, "UTF-8")}"))
    }

    suspend fun images(id: String): List<CloudQuestionImage> = withContext(Dispatchers.IO) {
        val array = getJsonArray("/api/v1/questions/${URLEncoder.encode(id, "UTF-8")}/images")
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(CloudQuestionImage(item.getString("assetId"), item.getString("url")))
            }
        }
    }

    suspend fun uploadImage(id: String, bytes: ByteArray, contentType: String): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url("/api/v1/questions/${URLEncoder.encode(id, "UTF-8")}/image"))
            .header("Authorization", "Bearer $token")
            .post(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw responseError(response.body?.string().orEmpty(), response.code)
        }
    }

    suspend fun subjects(): List<CloudSubject> = withContext(Dispatchers.IO) {
        val array = getJsonArray("/api/v1/subjects")
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(CloudSubject(item.getString("id"), item.optString("name")))
            }
        }
    }

    suspend fun knowledgePoints(subjectId: String, query: String = ""): List<CloudKnowledgePoint> =
        withContext(Dispatchers.IO) {
            val subject = URLEncoder.encode(subjectId, "UTF-8")
            val search = URLEncoder.encode(query, "UTF-8")
            val array = getJsonArray("/api/v1/knowledge-points?subjectId=$subject&limit=500&q=$search")
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        CloudKnowledgePoint(
                            id = item.getString("id"),
                            name = item.optString("name"),
                            grade = item.optString("grade"),
                            chapter = item.optString("chapter"),
                            section = item.optString("section")
                        )
                    )
                }
            }
        }

    suspend fun createKnowledgePoint(
        subjectId: String,
        name: String,
        grade: String = "",
        textbookVersion: String = "",
        chapter: String = "",
        section: String = ""
    ): CloudKnowledgePoint = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("subjectId", subjectId)
            .put("name", name)
            .put("grade", grade)
            .put("textbookVersion", textbookVersion)
            .put("chapter", chapter)
            .put("section", section)
        val item = postJson("/api/v1/knowledge-points", body)
        CloudKnowledgePoint(
            id = item.getString("id"),
            name = item.optString("name"),
            grade = item.optString("grade"),
            chapter = item.optString("chapter"),
            section = item.optString("section")
        )
    }

    suspend fun duplicates(stem: String): CloudDuplicateCheck = withContext(Dispatchers.IO) {
        val root = getJson("/api/v1/questions/duplicates?stem=${URLEncoder.encode(stem, "UTF-8")}")
        CloudDuplicateCheck(
            exact = root.optJSONObject("exact")?.let(::parseQuestion),
            near = root.optJSONObject("near")?.let(::parseQuestion),
            similar = root.optJSONArray("similar")?.let { array ->
                buildList {
                    for (i in 0 until array.length()) add(parseQuestion(array.getJSONObject(i)))
                }
            }.orEmpty()
        )
    }

    suspend fun create(input: CloudQuestionInput): CloudQuestionDetail = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url("/api/v1/questions"))
            .header("Authorization", "Bearer $token")
            .post(questionJson(input).toString().toRequestBody(JSON))
            .build()
        parseDetail(executeJson(request))
    }

    suspend fun update(id: String, input: CloudQuestionInput): CloudQuestionDetail = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url("/api/v1/questions/${URLEncoder.encode(id, "UTF-8")}"))
            .header("Authorization", "Bearer $token")
            .patch(questionJson(input).toString().toRequestBody(JSON))
            .build()
        parseDetail(executeJson(request))
    }

    suspend fun review(id: String, status: String): Unit = withContext(Dispatchers.IO) {
        val body = JSONObject().put("status", status).toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(url("/api/v1/questions/${URLEncoder.encode(id, "UTF-8")}/review"))
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw responseError(response.body?.string().orEmpty(), response.code)
        }
    }

    private fun questionJson(input: CloudQuestionInput): JSONObject {
        val options = JSONArray()
        input.options.forEachIndexed { index, (key, content) ->
            options.put(JSONObject().put("key", key).put("content", content).put("sortOrder", index))
        }
        val knowledge = JSONArray()
        input.knowledgePointIds.forEachIndexed { index, id ->
            knowledge.put(
                JSONObject()
                    .put("knowledgePointId", id)
                    .put("importance", if (index == 0) "PRIMARY" else "RELATED")
            )
        }
        return JSONObject()
            .put("subjectId", input.subjectId)
            .put("grade", input.grade)
            .put("textbookVersion", input.textbookVersion)
            .put("questionType", input.questionType)
            .put("difficulty", input.difficulty)
            .put("stem", input.stem)
            .put("sourceType", input.sourceType)
            .put("sourceName", input.sourceName)
            .put("copyrightStatus", "PERSONAL_USE")
            .put("usageScope", "PRIVATE_TEACHING")
            .put("reviewStatus", input.reviewStatus)
            .put("options", options)
            .put("answer", JSONObject()
                .put("standardAnswer", input.standardAnswer)
                .put("analysis", input.analysis)
                .put("scoringPoints", input.scoringPoints)
                .put("solutionSteps", input.solutionSteps))
            .put("knowledge", knowledge)
            .put("abilityTags", JSONArray(input.abilityTags))
            .put("baseVersion", input.baseVersion ?: JSONObject.NULL)
    }

    private fun parseDetail(root: JSONObject): CloudQuestionDetail {
        val answer = root.optJSONObject("answer")
        val options = mutableListOf<Pair<String, String>>()
        root.optJSONArray("options")?.let { array ->
            for (i in 0 until array.length()) {
                val option = array.getJSONObject(i)
                options += option.optString("optionKey") to option.optString("content")
            }
        }
        val knowledge = mutableListOf<CloudKnowledgeLink>()
        root.optJSONArray("knowledge")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                knowledge += CloudKnowledgeLink(
                    knowledgePointId = item.optString("knowledgePointId"),
                    importance = item.optString("importance", "PRIMARY")
                )
            }
        }
        val abilityTags = mutableListOf<String>()
        root.optJSONArray("abilityTags")?.let { array ->
            for (i in 0 until array.length()) abilityTags += array.optString(i)
        }
        return CloudQuestionDetail(
            question = parseQuestion(root.getJSONObject("question")),
            options = options,
            standardAnswer = answer?.optString("standardAnswer").orEmpty(),
            analysis = answer?.optString("analysis").orEmpty(),
            scoringPoints = answer?.optString("scoringPoints").orEmpty(),
            solutionSteps = answer?.optString("solutionSteps").orEmpty(),
            knowledge = knowledge,
            abilityTags = abilityTags
        )
    }

    private fun parseQuestion(item: JSONObject) = CloudQuestion(
        id = item.getString("id"),
        subjectId = item.optString("subjectId"),
        grade = item.optString("grade"),
        questionType = item.optString("questionType"),
        difficulty = item.optInt("difficulty", 3),
        stem = item.optString("stem"),
        reviewStatus = item.optString("reviewStatus"),
        sourceType = item.optString("sourceType"),
        version = item.optInt("version", 1),
        textbookVersion = item.optString("textbookVersion"),
        sourceName = item.optString("sourceName")
    )

    private fun getJson(path: String): JSONObject {
        val request = Request.Builder().url(url(path)).header("Authorization", "Bearer $token").get().build()
        return executeJson(request)
    }

    private fun getJsonArray(path: String): JSONArray {
        val request = Request.Builder().url(url(path)).header("Authorization", "Bearer $token").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw responseError(body, response.code)
            return JSONArray(body)
        }
    }

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw responseError(body, response.code)
            return JSONObject(body)
        }
    }

    private fun url(path: String) = "${baseUrl.trimEnd('/')}$path"

    private fun responseError(body: String, status: Int): QuestionApiException {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val code = error?.optString("code").orEmpty().ifBlank { "HTTP_$status" }
        val message = error?.optString("message").orEmpty().ifBlank { "HTTP $status" }
        return QuestionApiException(status, code, message)
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
