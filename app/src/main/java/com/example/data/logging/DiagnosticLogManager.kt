package com.example.data.logging

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque

enum class LogCategory {
    HTTP_NETWORK,
    SCRAPER,
    RECOVERY,
    ALBUM_QUEUE,
    GENERAL
}

typealias DiagnosticLogCategory = LogCategory

enum class LogSeverity {
    INFO,
    WARN,
    ERROR
}

typealias DiagnosticLogLevel = LogSeverity

data class DiagnosticLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val category: LogCategory,
    val severity: LogSeverity,
    val tag: String,
    val message: String,
    val url: String? = null,
    val requestHeaders: Map<String, String>? = null,
    val responseCode: Int? = null,
    val responseHeaders: Map<String, String>? = null,
    val stackTrace: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))

    fun toFormattedText(): String = buildString {
        append("[$formattedTime] [${severity.name}] [$tag] $message\n")
        if (!url.isNullOrBlank()) {
            append("  URL: $url\n")
        }
        if (responseCode != null) {
            append("  HTTP Status: $responseCode\n")
        }
        if (!requestHeaders.isNullOrEmpty()) {
            append("  Request Headers:\n")
            requestHeaders.forEach { (k, v) -> append("    $k: $v\n") }
        }
        if (!responseHeaders.isNullOrEmpty()) {
            append("  Response Headers:\n")
            responseHeaders.forEach { (k, v) -> append("    $k: $v\n") }
        }
        if (!stackTrace.isNullOrBlank()) {
            append("  Stack Trace:\n")
            stackTrace.lineSequence().take(15).forEach { append("    $it\n") }
        }
    }
}

object DiagnosticLogManager {
    private const val MAX_LOGS = 500
    private val logDeque = ConcurrentLinkedDeque<DiagnosticLogEntry>()
    private val _logsFlow = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<DiagnosticLogEntry>> = _logsFlow.asStateFlow()
    val logs: StateFlow<List<DiagnosticLogEntry>> get() = logsFlow
    fun getFormattedLogs(): String = getAllLogsFormatted()

    fun log(
        category: LogCategory,
        severity: LogSeverity,
        tag: String,
        message: String,
        url: String? = null,
        requestHeaders: Map<String, String>? = null,
        responseCode: Int? = null,
        responseHeaders: Map<String, String>? = null,
        throwable: Throwable? = null
    ) {
        val entry = DiagnosticLogEntry(
            category = category,
            severity = severity,
            tag = tag,
            message = message,
            url = url,
            requestHeaders = requestHeaders,
            responseCode = responseCode,
            responseHeaders = responseHeaders,
            stackTrace = throwable?.stackTraceToString()
        )
        logDeque.addFirst(entry)
        while (logDeque.size > MAX_LOGS) {
            logDeque.pollLast()
        }
        _logsFlow.value = logDeque.toList()
    }

    fun info(tag: String, message: String, category: LogCategory = LogCategory.GENERAL, url: String? = null) {
        log(category, LogSeverity.INFO, tag, message, url)
    }

    fun warn(tag: String, message: String, category: LogCategory = LogCategory.GENERAL, url: String? = null) {
        log(category, LogSeverity.WARN, tag, message, url)
    }

    fun error(
        tag: String,
        message: String,
        category: LogCategory = LogCategory.GENERAL,
        url: String? = null,
        throwable: Throwable? = null,
        requestHeaders: Map<String, String>? = null,
        responseCode: Int? = null,
        responseHeaders: Map<String, String>? = null
    ) {
        log(category, LogSeverity.ERROR, tag, message, url, requestHeaders, responseCode, responseHeaders, throwable)
    }

    fun clear() {
        logDeque.clear()
        _logsFlow.value = emptyList()
    }

    fun getAllLogsFormatted(): String = buildString {
        append("=== PulseGrab Diagnostic Logs Export ===\n")
        append("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        append("Total Entries: ${logDeque.size}\n\n")
        logDeque.reversed().forEach { append(it.toFormattedText()).append("\n") }
    }

    fun shareLogs(context: Context) {
        val text = getAllLogsFormatted()
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "PulseGrab Download Diagnostic Logs")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Diagnostic Logs")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }
}
