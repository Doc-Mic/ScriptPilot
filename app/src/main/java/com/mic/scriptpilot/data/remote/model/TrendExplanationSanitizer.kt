package com.mic.scriptpilot.data.remote.model

import org.json.JSONObject

/**
 * Strips accidental JSON objects from trend summary / reason lines (e.g. `{"trend":"..."}`).
 */
fun String?.sanitizeTrendExplanation(): String {
    val s = this?.trim().orEmpty()
    if (s.isEmpty()) return ""
    if (!s.startsWith("{") || !s.contains("}")) return squishWs(s)
    return try {
        val o = JSONObject(s)
        val keys =
            listOf(
                "trend",
                "summary",
                "text",
                "sentence",
                "reason",
                "explanation",
                "description",
                "message",
                "insight",
            )
        for (k in keys) {
            if (o.has(k)) {
                val v = o.optString(k).trim()
                if (v.isNotEmpty()) return squishWs(v)
            }
        }
        val it = o.keys()
        while (it.hasNext()) {
            val v = o.opt(it.next())
            if (v is String && v.isNotBlank()) return squishWs(v.trim())
        }
        squishWs(s)
    } catch (_: Exception) {
        squishWs(s)
    }
}

private fun squishWs(t: String): String = t.replace(Regex("\\s+"), " ").trim()
