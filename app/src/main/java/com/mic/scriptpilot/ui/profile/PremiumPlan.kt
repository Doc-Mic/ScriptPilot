package com.mic.scriptpilot.ui.profile

data class PremiumPlan(
    val id: String,
    val name: String,
    val price: String,
    val subtitle: String,
    val features: List<String>,
    val badge: String? = null,
    val isCurrent: Boolean = false,
)

object PremiumPlansCatalog {
    const val FREE_PLAN_ID = "free"
    private const val CREATOR_PRO_PLAN_ID = "creator_pro"
    private const val STUDIO_UNLIMITED_PLAN_ID = "studio_unlimited"

    fun plans(currentPlanId: String = FREE_PLAN_ID): List<PremiumPlan> =
        listOf(
            PremiumPlan(
                id = FREE_PLAN_ID,
                name = "Free Plan",
                price = "$0",
                subtitle = "Start creating with basic AI tools",
                features =
                    listOf(
                        "Limited daily generations",
                        "Basic trend discovery",
                        "Basic video ideas",
                        "Basic script generation",
                        "No advanced SEO",
                    ),
                isCurrent = currentPlanId == FREE_PLAN_ID,
            ),
            PremiumPlan(
                id = CREATOR_PRO_PLAN_ID,
                name = "Creator Pro",
                price = "$4.99 / month",
                subtitle = "For creators who publish consistently",
                features =
                    listOf(
                        "Create faster with more daily generations",
                        "Plan better videos with advanced idea generation",
                        "Write longer scripts with stronger pacing",
                        "Turn ideas into Shorts scripts",
                        "Publish with confidence using SEO assistant",
                        "Save more creator projects",
                        "Priority generation for busy publishing weeks",
                    ),
            ),
            PremiumPlan(
                id = STUDIO_UNLIMITED_PLAN_ID,
                name = "Studio Unlimited",
                price = "$14.99 / month",
                subtitle = "Unlimited power for serious creators",
                features =
                    listOf(
                        "Remove creative limits with unlimited generations",
                        "Use stronger AI models for deeper work",
                        "Grow reach with advanced SEO suggestions",
                        "Move faster with premium script templates",
                        "Build complete long-form script workflows",
                        "Keep unlimited saved projects",
                        "Get faster priority responses",
                        "Try new creator tools early",
                    ),
                badge = "Most Powerful",
            ),
        )
}
