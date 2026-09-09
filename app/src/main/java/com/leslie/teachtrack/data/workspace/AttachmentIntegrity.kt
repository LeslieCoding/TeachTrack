package com.leslie.teachtrack.data.workspace

import android.content.ContentResolver
import android.net.Uri
import java.security.MessageDigest

data class AttachmentIntegrity(val size: Long, val sha256: String)

object AttachmentIntegrityCalculator {
    fun calculate(resolver: ContentResolver, uri: Uri): AttachmentIntegrity {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                size += read
            }
        } ?: error("附件无法读取")
        return AttachmentIntegrity(size, digest.digest().joinToString("") { "%02x".format(it) })
    }
}
