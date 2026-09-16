package com.leslie.teachtrack

import java.net.ServerSocket
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

/**
 * §89 item 4 / §39 delivery proof: a minimal OpenAI-compatible chat-completions
 * mock that runs INSIDE the instrumented-test process on 127.0.0.1, so the
 * app under test can be pointed at it as a "custom base URL" provider without
 * any real LLM key or network egress.
 *
 * Canned behavior, two turn shapes only:
 *  - a request whose messages contain NO `role:"tool"` entry → one streaming
 *    `shell_execute` tool_call that lists students through the real sandbox
 *    CLI (`teachtrack rpc … student.search`);
 *  - a request that DOES contain a tool result → a final text answer that
 *    quotes the student names parsed out of that tool output. The names can
 *    only be correct if the shell genuinely executed against the device DB
 *    and the result travelled back through the agent loop — the server has
 *    no other way to know them.
 *
 * Every request body is appended to [requestLog] for the evidence artifact.
 */
class MockLlmServer {

    private var server: ServerSocket? = null
    @Volatile private var running = false

    val requestLog = StringBuilder()
    @Volatile var requestCount = 0
        private set

    fun start(): Int {
        val ss = ServerSocket(0)
        server = ss
        running = true
        thread(isDaemon = true, name = "mock-llm-accept") {
            while (running) {
                val sock = try { ss.accept() } catch (_: Exception) { break }
                thread(isDaemon = true, name = "mock-llm-io") { runCatching { handle(sock) } }
            }
        }
        return ss.localPort
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
    }

    private fun handle(sock: java.net.Socket) {
        sock.soTimeout = 30_000
        sock.getOutputStream()
        val reader = sock.getInputStream().bufferedReader(Charsets.UTF_8)
        var contentLength = 0
        var requestLine = reader.readLine()
        val firstLine = requestLine ?: ""
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }
        val bodyChars = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val n = reader.read(bodyChars, read, contentLength - read)
            if (n < 0) break
            read += n
        }
        val body = String(bodyChars, 0, read)
        synchronized(requestLog) {
            requestCount++
            requestLog.append("=== request #").append(requestCount).append(' ').append(firstLine).append('\n')
            requestLog.append(body).append("\n\n")
        }

        val sse = buildResponse(body)
        val out = sock.getOutputStream()
        val head = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(sse.toByteArray(Charsets.UTF_8))
        out.flush()
        runCatching { sock.close() }
    }

    private fun buildResponse(requestBody: String): String {
        val req = runCatching { JSONObject(requestBody) }.getOrNull()
        val messages = req?.optJSONArray("messages") ?: JSONArray()
        val toolOutput = lastToolOutput(messages)
        val sb = StringBuilder()
        if (toolOutput == null) {
            // Turn 1: ask the agent to list students through the real sandbox CLI.
            val args = JSONObject()
                .put("tool_title", "查询在读学生列表")
                .put("command", "teachtrack rpc --json '{\"action\":\"student.search\",\"params\":{\"query\":\"\"}}'")
            val toolCall = JSONObject()
                .put("index", 0)
                .put("id", "call_mock_shell_1")
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", "shell_execute")
                    .put("arguments", args.toString()))
            sb.append(chunk(JSONObject().put("role", "assistant").put("tool_calls", JSONArray().put(toolCall)), null))
            sb.append(chunk(JSONObject(), "tool_calls"))
            sb.append(usageChunk())
            sb.append("data: [DONE]\n\n")
        } else {
            // Turn 2: quote the student names parsed from the REAL tool output.
            val names = Regex("找到 \\d+ 名学生：([^\\n\"，]+)").find(toolOutput)?.groupValues?.get(1)
                ?: Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(toolOutput)?.groupValues?.get(1)
                ?: toolOutput.take(200)
            val answer = "根据刚刚的查询结果，你的在读学生是：$names。这些数据来自本机 TeachTrack 数据库，是通过沙箱里的 teachtrack CLI 实时查到的。"
            // Stream the answer in a few deltas, like a real model would.
            val parts = answer.chunked(12)
            sb.append(chunk(JSONObject().put("role", "assistant").put("content", ""), null))
            for (p in parts) sb.append(chunk(JSONObject().put("content", p), null))
            sb.append(chunk(JSONObject(), "stop"))
            sb.append(usageChunk())
            sb.append("data: [DONE]\n\n")
        }
        return sb.toString()
    }

    private fun lastToolOutput(messages: JSONArray): String? {
        for (i in messages.length() - 1 downTo 0) {
            val m = messages.optJSONObject(i) ?: continue
            if (m.optString("role") == "tool") {
                return when (val c = m.opt("content")) {
                    is String -> c
                    is JSONArray -> (0 until c.length())
                        .mapNotNull { c.optJSONObject(it)?.optString("text") }
                        .joinToString("\n")
                    else -> c?.toString()
                }
            }
        }
        return null
    }

    private fun chunk(delta: JSONObject, finishReason: String?): String {
        val choice = JSONObject().put("index", 0).put("delta", delta)
        if (finishReason != null) choice.put("finish_reason", finishReason) else choice.put("finish_reason", JSONObject.NULL)
        val ev = JSONObject()
            .put("id", "chatcmpl-mock")
            .put("object", "chat.completion.chunk")
            .put("created", System.currentTimeMillis() / 1000)
            .put("model", "mock-model")
            .put("choices", JSONArray().put(choice))
        return "data: ${ev}\n\n"
    }

    private fun usageChunk(): String {
        val ev = JSONObject()
            .put("id", "chatcmpl-mock")
            .put("object", "chat.completion.chunk")
            .put("created", System.currentTimeMillis() / 1000)
            .put("model", "mock-model")
            .put("choices", JSONArray())
            .put("usage", JSONObject()
                .put("prompt_tokens", 100)
                .put("completion_tokens", 40)
                .put("total_tokens", 140))
        return "data: ${ev}\n\n"
    }
}
