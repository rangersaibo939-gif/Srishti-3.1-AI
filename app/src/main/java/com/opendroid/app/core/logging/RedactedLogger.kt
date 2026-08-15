package com.opendroid.app.core.logging

import android.util.Log
import java.util.regex.Pattern

/**
 * Security-hardened Logger that scrubs PII, Bearer tokens, and secrets
 * before writing to Android Logcat or Room storage.
 */
object RedactedLogger {

    private val SENSITIVE_PATTERNS = listOf(
        // API Keys / Secrets
        Pattern.compile("(?i)(api[_-]?key|secret|token|password|auth|bearer)\\s*[:=]\\s*[\"']?([^\"'\\s,]+)[\"']?"),
        // Bearer tokens in headers
        Pattern.compile("(?i)Bearer\\s+([A-Za-z0-9_\\-\\.]+)"),
        // Email addresses
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"),
        // Phone numbers (US/Intl format)
        Pattern.compile("(\\+\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}")
    )

    fun redact(message: String): String {
        var clean = message
        for (pattern in SENSITIVE_PATTERNS) {
            val matcher = pattern.matcher(clean)
            clean = matcher.replaceAll("[REDACTED_SECRET]")
        }
        return clean
    }

    fun d(tag: String, message: String) {
        Log.d(tag, redact(message))
    }

    fun i(tag: String, message: String) {
        Log.i(tag, redact(message))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, redact(message), throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, redact(message), throwable)
    }
}
