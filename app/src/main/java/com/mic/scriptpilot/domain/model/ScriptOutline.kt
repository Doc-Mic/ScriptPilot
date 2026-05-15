package com.mic.scriptpilot.domain.model

data class ScriptOutline(
    val hook: String,
    val intro: String,
    val body: String,
    val outro: String,
) {
    fun asFullScript(): String = buildString {
        appendLine("## Hook")
        appendLine(hook)
        appendLine()
        appendLine("## Intro")
        appendLine(intro)
        appendLine()
        appendLine("## Body")
        appendLine(body)
        appendLine()
        appendLine("## Outro")
        appendLine(outro)
    }
}
