package com.leslie.teachtrack.data.workspace

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject

interface WorkspaceStorage {
    fun read(name: String): String?
    fun write(name: String, content: String)
    fun writeBytes(name: String, content: ByteArray)
    fun append(relativePath: String, content: String)
    fun exists(name: String): Boolean
    fun list(directory: String): List<String>
}

class AndroidSafWorkspaceStorage(private val context: Context, private val rootUri: Uri) : WorkspaceStorage {
    private val resolver get() = context.contentResolver
    private fun root(): DocumentFile = DocumentFile.fromTreeUri(context, rootUri)
        ?: error("Workspace directory is not accessible")

    private fun file(relativePath: String, create: Boolean): DocumentFile? {
        val parts = relativePath.trim('/').split('/').filter { it.isNotBlank() }
        var current = root()
        parts.dropLast(1).forEach { part ->
            current = current.findFile(part) ?: if (create) current.createDirectory(part)!! else return null
        }
        val name = parts.lastOrNull() ?: return null
        return current.findFile(name) ?: if (create) current.createFile("application/octet-stream", name) else null
    }

    override fun read(name: String): String? = file(name, false)?.let { resolver.openInputStream(it.uri)?.bufferedReader()?.use { r -> r.readText() } }
    override fun write(name: String, content: String) {
        val target = file(name, true) ?: error("Cannot create workspace file: $name")
        resolver.openOutputStream(target.uri, "wt")?.bufferedWriter()?.use { it.write(content) }
            ?: error("Cannot write workspace file: $name")
    }
    override fun writeBytes(name: String, content: ByteArray) {
        val target = file(name, true) ?: error("Cannot create workspace file: $name")
        resolver.openOutputStream(target.uri, "wt")?.use { it.write(content) } ?: error("Cannot write workspace file: $name")
    }
    override fun append(relativePath: String, content: String) {
        val target = file(relativePath, true) ?: error("Cannot create workspace file: $relativePath")
        resolver.openOutputStream(target.uri, "wa")?.bufferedWriter()?.use { it.write(content) }
            ?: error("Cannot append workspace file: $relativePath")
    }
    override fun exists(name: String): Boolean = file(name, false) != null
    override fun list(directory: String): List<String> {
        val parts = directory.trim('/').split('/').filter { it.isNotBlank() }
        var current = root()
        parts.forEach { current = current.findFile(it) ?: return emptyList() }
        return current.listFiles().mapNotNull { it.name }.map { if (directory.isBlank()) it else "$directory/$it" }
    }
}

fun JSONObject.putNullable(key: String, value: Any?): JSONObject = if (value == null) put(key, JSONObject.NULL) else put(key, value)
