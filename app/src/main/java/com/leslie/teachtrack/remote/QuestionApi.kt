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
    val version: Int
)

data class CloudQuestionDetail(
    val question: CloudQuestion,
    val options: List<Pair<String, String>>,
    val standardAnswer: String,
    val analysis: String
)

class QuestionApi(
    private val baseUrl: String,
    private val token: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun search(query: String, limit: Int = 50): List<CloudQuestion> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/questions?limit=$limit&q=$encoded")
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(errorMessage(body, response.code))
            val root = JSONObject(body)
            val items = root.optJSONArray("items") ?: return@use emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    add(
                        CloudQuestion(
                            id = item.getString("id"),
                            subjectId = item.optString("subjectId"),
                            grade = item.optString("grade"),
                            questionType = item.optString("questionType"),
                            difficulty = item.optInt("difficulty", 3),
                            stem = item.optString("stem"),
                            reviewStatus = item.optString("reviewStatus"),
                            sourceType = item.optString("sourceType"),
                            version = item.optInt("version", 1)
                        )
                    )
                }
            }
        }
    }

    suspend fun detail(id: String): CloudQuestionDetail = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/questions/$id")
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(errorMessage(body, response.code))
            val root = JSONObject(body)
            val q = root.getJSONObject("question")
            val options = mutableListOf<Pair<String, String>>()
            root.optJSONArray("options")?.let { array ->
                for (i in 0 until array.length()) {
                    val option = array.getJSONObject(i)
                    options += option.optString("optionKey") to option.optString("content")
                }
            }
            val answer = root.optJSONObject("answer")
            CloudQuestionDetail(
                question = CloudQuestion(
                    id = q.getString("id"),
                    subjectId = q.optString("subjectId"),
                    grade = q.optString("grade"),
                    questionType = q.optString("questionType"),
                    difficulty = q.optInt("difficulty", 3),
                    stem = q.optString("stem"),
                    reviewStatus = q.optString("reviewStatus"),
                    sourceType = q.optString("sourceType"),
                    version = q.optInt("version", 1)
                ),
                options = options,
                standardAnswer = answer?.optString("standardAnswer").orEmpty(),
                analysis = answer?.optString("analysis").orEmpty()
            )
        }
    }

    suspend fun review(id: String, status: String): Unit = withContext(Dispatchers.IO) {
        val body = JSONObject().put("status", status).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/questions/$id/review")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(errorMessage(response.body?.string().orEmpty(), response.code))
            }
        }
    }

    private fun errorMessage(body: String, code: Int): String = runCatching {
        JSONObject(body).getJSONObject("error").optString("message")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $code"
}
