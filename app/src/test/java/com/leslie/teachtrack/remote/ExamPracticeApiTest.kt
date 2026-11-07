package com.leslie.teachtrack.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ExamPracticeApiTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun api() = ExamPracticeApi(server.url("/").toString(), "token")

    @Test fun submitSendsEveryAnswerAndParsesMastery() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """
            {
              "set":{"id":"p1","title":"压强练习","status":"COMPLETED"},
              "items":[{"id":"i1","stem":"题目","score":5,"sourceKind":"BANK"}],
              "mastery":[{
                "knowledgePointId":"kp1","masteryScore":62.5,"confidence":0.8,
                "practiceCount":1,"correctCount":1,"wrongCount":0,"trend":"0.000"
              }]
            }
            """.trimIndent()
        ))
        val result = api().submitPractice("p1", mapOf("i1" to "A"))
        assertEquals("COMPLETED", result.status)
        assertEquals(62.5, result.mastery.single().masteryScore, 0.001)
        assertEquals(1, result.mastery.single().correctCount)

        val request = server.takeRequest()
        assertEquals("/api/v1/practices/p1/submit", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        val answer = body.getJSONArray("answers").getJSONObject(0)
        assertEquals("i1", answer.getString("itemId"))
        assertEquals("A", answer.getString("studentAnswer"))
    }

    @Test fun practiceGenerationDoesNotEnableAiWithoutCredentials() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"set":{"id":"p1","title":"草稿","status":"DRAFT"},"items":[],"mastery":[]}"""
        ))
        api().generatePractice("student", "science", 5, "BASIC")
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(false, body.getBoolean("allowAi"))
        assertEquals(5, body.getInt("questionCount"))
    }
}
