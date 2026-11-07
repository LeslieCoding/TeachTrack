package com.leslie.teachtrack.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class QuestionApiTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun api() = QuestionApi(server.url("/").toString(), "token")

    private fun detailJson() = """
        {
          "question": {
            "id":"q1","subjectId":"science","grade":"八上","textbookVersion":"浙教版",
            "questionType":"SINGLE_CHOICE","difficulty":3,"stem":"题干",
            "reviewStatus":"DRAFT","sourceType":"SELF_CREATED","sourceName":"周测","version":2
          },
          "options":[{"optionKey":"A","content":"甲"}],
          "answer":{"standardAnswer":"A","analysis":"解析","scoringPoints":"得分点","solutionSteps":"步骤"},
          "knowledge":[{"knowledgePointId":"kp1","importance":"PRIMARY"}],
          "abilityTags":["含图待补"]
        }
    """.trimIndent()

    private fun input(baseVersion: Int? = null) = CloudQuestionInput(
        subjectId = "science", grade = "八上", textbookVersion = "浙教版",
        questionType = "SINGLE_CHOICE", difficulty = 3, stem = "题干",
        sourceType = "SELF_CREATED", sourceName = "周测", reviewStatus = "DRAFT",
        options = listOf("A" to "甲"), standardAnswer = "A", analysis = "解析",
        scoringPoints = "得分点", solutionSteps = "步骤", knowledgePointIds = listOf("kp1"),
        abilityTags = listOf("含图待补"), baseVersion = baseVersion
    )

    @Test fun createSerializesCompleteQuestionAndParsesDetail() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(detailJson()))
        val detail = api().create(input())
        assertEquals("q1", detail.question.id)
        assertEquals("浙教版", detail.question.textbookVersion)
        assertEquals("得分点", detail.scoringPoints)
        assertEquals("kp1", detail.knowledge.single().knowledgePointId)
        assertEquals(listOf("含图待补"), detail.abilityTags)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("PRIVATE_TEACHING", body.getString("usageScope"))
        assertEquals("kp1", body.getJSONArray("knowledge").getJSONObject(0).getString("knowledgePointId"))
        assertEquals("含图待补", body.getJSONArray("abilityTags").getString(0))
    }

    @Test fun updateSendsBaseVersionAndPreservesConflictCode() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody(
            """{"error":{"code":"VERSION_CONFLICT","message":"题目已被修改"}}"""
        ))
        try {
            api().update("q1", input(baseVersion = 2))
            fail("expected conflict")
        } catch (e: QuestionApiException) {
            assertEquals(409, e.status)
            assertEquals("VERSION_CONFLICT", e.code)
        }
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals(2, JSONObject(request.body.readUtf8()).getInt("baseVersion"))
    }

    @Test fun duplicatesParsesExactNearAndSimilar() = runBlocking {
        val question = """{"id":"q1","stem":"题干","questionType":"OTHER","difficulty":3,"version":1}"""
        server.enqueue(MockResponse().setBody("""{"exact":$question,"near":null,"similar":[$question]}"""))
        val result = api().duplicates("题干")
        assertEquals("q1", result.exact?.id)
        assertEquals(null, result.near)
        assertEquals("q1", result.similar.single().id)
        assertTrue(server.takeRequest().path.orEmpty().startsWith("/api/v1/questions/duplicates?stem="))
    }

    @Test fun searchSendsAllFiltersAndBulkResultsAreParsed() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"items":[],"limit":100,"offset":0,"total":0}"""))
        api().search(
            query = "压强", subjectId = "science", grade = "八上",
            questionType = "SINGLE_CHOICE", difficulty = 3, reviewStatus = "APPROVED"
        )
        val searchPath = server.takeRequest().path.orEmpty()
        assertTrue(searchPath.contains("q=%E5%8E%8B%E5%BC%BA"))
        assertTrue(searchPath.contains("subjectId=science"))
        assertTrue(searchPath.contains("grade=%E5%85%AB%E4%B8%8A"))
        assertTrue(searchPath.contains("questionType=SINGLE_CHOICE"))
        assertTrue(searchPath.contains("minDifficulty=3"))
        assertTrue(searchPath.contains("maxDifficulty=3"))
        assertTrue(searchPath.contains("reviewStatus=APPROVED"))

        server.enqueue(MockResponse().setBody(
            """{"requested":2,"succeeded":1,"failed":["q2: missing"]}"""
        ))
        val result = api().bulkReview(listOf("q1", "q2"), "APPROVED")
        assertEquals(2, result.requested)
        assertEquals(1, result.succeeded)
        assertEquals(listOf("q2: missing"), result.failed)
        val bulk = server.takeRequest()
        assertEquals("/api/v1/questions/bulk-review", bulk.path)
        val body = JSONObject(bulk.body.readUtf8())
        assertEquals("q1", body.getJSONArray("ids").getString(0))
        assertEquals("APPROVED", body.getString("status"))
    }

    @Test fun questionImagesUseDirectUploadAndSignedList() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"assetId":"a1","url":"http://localhost/download/a1"}"""
        ))
        api().uploadImage("q1", byteArrayOf(1, 2, 3), "image/png")
        val upload = server.takeRequest()
        assertEquals("/api/v1/questions/q1/image", upload.path)
        assertEquals("image/png", upload.getHeader("Content-Type"))

        server.enqueue(MockResponse().setBody(
            """[{"assetId":"a1","url":"http://localhost/download/a1"}]"""
        ))
        val images = api().images("q1")
        assertEquals("a1", images.single().assetId)
        assertEquals("/api/v1/questions/q1/images", server.takeRequest().path)
    }

    @Test fun metadataEndpointsUseSubjectFilter() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"science","name":"科学"}]"""))
        server.enqueue(MockResponse().setBody("""[{"id":"kp1","name":"液体压强","grade":"八上","chapter":"第三章","section":"第 7 节"}]"""))
        assertEquals("科学", api().subjects().single().name)
        val points = api().knowledgePoints("science", "压强")
        assertEquals("液体压强", points.single().name)
        assertEquals("/api/v1/subjects", server.takeRequest().path)
        assertTrue(server.takeRequest().path.orEmpty().contains("subjectId=science"))
    }

    @Test fun createsKnowledgePointInsideEditor() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"id":"kp2","name":"电功率","grade":"九上","chapter":"第三章","section":"第 6 节"}"""
        ))
        val point = api().createKnowledgePoint(
            subjectId = "science", name = "电功率", grade = "九上",
            chapter = "第三章", section = "第 6 节"
        )
        assertEquals("kp2", point.id)
        assertEquals("电功率", point.name)
        val request = server.takeRequest()
        assertEquals("/api/v1/knowledge-points", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("science", body.getString("subjectId"))
        assertEquals("电功率", body.getString("name"))
        assertEquals("第 6 节", body.getString("section"))
    }
}
