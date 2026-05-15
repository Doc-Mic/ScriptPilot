package com.mic.scriptpilot.ui.util

import android.content.Context
import com.google.gson.JsonSyntaxException
import com.mic.scriptpilot.R
import com.mic.scriptpilot.data.remote.ScriptPilotApiException
import retrofit2.HttpException

/**
 * Maps technical failures to copy suitable for UI (no raw [IllegalStateException] / Gson internals).
 */
fun Throwable.toUserFacingMessage(context: Context): String {
    val unexpected = context.getString(R.string.error_unexpected_ai_response)
    return when (this) {
        is JsonSyntaxException -> unexpected
        is HttpException -> {
            when (code()) {
                in 500..599 -> unexpected
                404 -> unexpected
                else -> unexpected
            }
        }
        is ScriptPilotApiException -> {
            val m = message.orEmpty()
            if (m.isTechnicalFailure()) unexpected else m.ifBlank { unexpected }
        }
        else -> {
            val m = message.orEmpty()
            when {
                m.isTechnicalFailure() -> unexpected
                m.isNotBlank() -> m
                else -> unexpected
            }
        }
    }
}

private fun String.isTechnicalFailure(): Boolean {
    val lower = lowercase()
    return lower.contains("begin_object") ||
        lower.contains("begin_array") ||
        lower.contains("illegalstateexception") ||
        lower.contains("jsonsyntax") ||
        lower.contains("scriptpilotapiexception") ||
        lower.contains("unexpected script payload")
}
