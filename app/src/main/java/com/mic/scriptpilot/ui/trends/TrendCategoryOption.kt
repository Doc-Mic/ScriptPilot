package com.mic.scriptpilot.ui.trends

data class TrendCategoryOption(
    val id: Int,
    val label: String,
)

object TrendCategoryCatalog {
    val categories: List<TrendCategoryOption> =
        listOf(
            TrendCategoryOption(0, "All"),
            TrendCategoryOption(10, "Music"),
            TrendCategoryOption(15, "Pets & Animals"),
            TrendCategoryOption(17, "Sports"),
            TrendCategoryOption(20, "Gaming"),
            TrendCategoryOption(22, "People & Blogs"),
            TrendCategoryOption(23, "Comedy"),
            TrendCategoryOption(24, "Entertainment"),
            TrendCategoryOption(25, "News & Politics"),
            TrendCategoryOption(26, "Howto & Style"),
            TrendCategoryOption(27, "Education"),
            TrendCategoryOption(28, "Science & Technology"),
        )

    fun labelFor(id: Int): String = categories.firstOrNull { it.id == id }?.label ?: "All"
}
