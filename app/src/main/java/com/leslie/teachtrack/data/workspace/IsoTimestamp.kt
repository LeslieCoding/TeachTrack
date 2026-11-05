package com.leslie.teachtrack.data.workspace

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** API-24 compatible ISO-8601 UTC timestamps for portable Workspace data. */
internal object IsoTimestamp {
    fun now(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())
}
