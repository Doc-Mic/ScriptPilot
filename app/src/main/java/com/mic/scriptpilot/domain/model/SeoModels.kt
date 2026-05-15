package com.mic.scriptpilot.domain.model

enum class SeoResultKind {
    TITLE,
    DESCRIPTION,
    TAG,
}

data class SeoResultLine(
    val id: String,
    val kind: SeoResultKind,
    val text: String,
)

data class SeoGeneration(
    val titles: List<SeoResultLine>,
    val descriptions: List<SeoResultLine>,
    val tags: List<SeoResultLine>,
)
