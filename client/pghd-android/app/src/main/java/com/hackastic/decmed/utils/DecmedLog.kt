package com.hackastic.decmed.utils

import android.util.Log

object DecmedLog {
    private const val CHUNK_SIZE = 3_500

    fun d(tag: String, message: String) = chunk(tag, message, Log::d)
    fun i(tag: String, message: String) = chunk(tag, message, Log::i)
    fun w(tag: String, message: String) = chunk(tag, message, Log::w)

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        chunk(tag, message, Log::e)
        if (throwable != null) {
            chunk(tag, Log.getStackTraceString(throwable), Log::e)
        }
    }

    private fun chunk(tag: String, message: String, logger: (String, String) -> Int) {
        if (message.isEmpty()) {
            logger(tag, "")
            return
        }
        var start = 0
        var part = 1
        val total = ((message.length - 1) / CHUNK_SIZE) + 1
        while (start < message.length) {
            val end = (start + CHUNK_SIZE).coerceAtMost(message.length)
            logger(tag, "[$part/$total] ${message.substring(start, end)}")
            start = end
            part += 1
        }
    }
}
