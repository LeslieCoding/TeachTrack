package com.leslie.teachtrack.sync

import com.leslie.teachtrack.core.model.SyncOutboxEntity
import java.io.IOException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudSyncClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun pushParsesAppliedAndConflicts() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "applied": [
                    {"operationId":"op-1","status":"APPLIED","entityType":"student","entityId":"s-1","version":2}
                  ],
                  "conflicts": [
                    {"operationId":"op-2","entityType":"student","entityId":"s-2",
                     "baseVersion":1,"currentVersion":3,"remote":{"displayName":"remote"},
                     "choices":["KEEP_LOCAL","KEEP_REMOTE","MANUAL_MERGE"]}
                  ]
                }
                """.trimIndent()
            )
        )
        val client = CloudSyncClient(server.url("/").toString(), "token", "device")
        val result = client.push(
            listOf(
                SyncOutboxEntity(
                    operationId = "op-1",
                    entityType = "student",
                    entityId = "s-1",
                    baseVersion = 1,
                    payload = """{"displayName":"local"}"""
                ),
                SyncOutboxEntity(
                    operationId = "op-2",
                    entityType = "student",
                    entityId = "s-2",
                    baseVersion = 1,
                    payload = """{"displayName":"local"}"""
                )
            )
        )
        assertEquals(1, result.applied.size)
        assertEquals(2L, result.applied.first().version)
        assertEquals(1, result.conflicts.size)
        assertEquals("op-2", result.conflicts.first().operationId)
        assertEquals(3L, result.conflicts.first().currentVersion)
        assertTrue(result.conflicts.first().remotePayload.contains("remote"))

        val request = server.takeRequest()
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("op-1"))
    }

    @Test fun changesParsesCursor() {
        server.enqueue(
            MockResponse().setBody(
                """
                {"changes":[{"seq":11,"entityType":"student","entityId":"s-1",
                  "operation":"UPSERT","version":4}],"nextCursor":11,"hasMore":false}
                """.trimIndent()
            )
        )
        val client = CloudSyncClient(server.url("/").toString(), "token", "device")
        val changes = client.changes(10)
        assertEquals(1, changes.items.size)
        assertEquals(11L, changes.items.first().seq)
        assertEquals(11L, changes.nextCursor)
    }

    @Test fun serverErrorBecomesIoException() {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":{"code":"CONFLICT","message":"version"}}"""))
        val client = CloudSyncClient(server.url("/").toString(), "token", "device")
        try {
            client.changes(0)
            throw AssertionError("expected IOException")
        } catch (expected: IOException) {
            assertEquals("version", expected.message)
        }
    }
}
