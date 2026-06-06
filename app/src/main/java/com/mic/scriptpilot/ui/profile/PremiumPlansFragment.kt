package com.mic.scriptpilot.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentPremiumPlansBinding
import com.mic.scriptpilot.databinding.ItemPremiumPlanBinding
import com.mic.scriptpilot.ui.common.playCardPressAnimation
import com.mic.scriptpilot.ui.common.setupScreenHeader
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PremiumPlansFragment : Fragment() {
    private var _binding: FragmentPremiumPlansBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    private val adapter = PremiumPlanAdapter { plan ->
        // TODO: Connect this action to Google Play Billing when subscriptions are enabled.
        Snackbar.make(
            binding.root,
            getString(R.string.premium_billing_coming_soon, plan.name),
            Snackbar.LENGTH_SHORT,
        ).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPremiumPlansBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.setupScreenHeader(R.string.profile_menu_premium, showBack = true) {
            findNavController().navigateUp()
        }
        binding.recyclerPlans.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPlans.adapter = adapter
        adapter.submit(viewModel.premiumPlans)
    }

    override fun onDestroyView() {
        binding.recyclerPlans.adapter = null
        super.onDestroyView()
        _binding = null
    }
}

private class PremiumPlanAdapter(
    private val onPlanClick: (PremiumPlan) -> Unit,
) : RecyclerView.Adapter<PremiumPlanAdapter.PlanViewHolder>() {
    private val plans = mutableListOf<PremiumPlan>()

    fun submit(items: List<PremiumPlan>) {
        plans.clear()
        plans.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val binding = ItemPremiumPlanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlanViewHolder(binding, onPlanClick)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        holder.bind(plans[position])
    }

    override fun getItemCount(): Int = plans.size

    class PlanViewHolder(
        private val binding: ItemPremiumPlanBinding,
        private val onPlanClick: (PremiumPlan) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(plan: PremiumPlan) {
            binding.textPlanName.text = plan.name
            binding.textPlanPrice.text = plan.price
            binding.textPlanSubtitle.text = plan.subtitle
            binding.textPlanBadge.isVisible = plan.badge != null
            binding.textPlanBadge.text = plan.badge.orEmpty()
            binding.buttonPlanCta.text =
                when {
                    plan.isCurrent -> binding.root.context.getString(R.string.premium_current_plan)
                    plan.id == PremiumPlansCatalog.FREE_PLAN_ID -> binding.root.context.getString(R.string.premium_switch_free)
                    plan.name.contains("Unlimited") -> binding.root.context.getString(R.string.premium_go_unlimited)
                    else -> binding.root.context.getString(R.string.premium_upgrade_creator_pro)
                }
            binding.buttonPlanCta.isEnabled = !plan.isCurrent
            binding.root.strokeWidth =
                if (plan.badge != null) {
                    binding.root.resources.getDimensionPixelSize(R.dimen.premium_featured_stroke)
                } else {
                    1
                }

            binding.layoutFeatures.removeAllViews()
            plan.features.forEach { feature ->
                binding.layoutFeatures.addView(featureRow(feature))
            }

            binding.buttonPlanCta.setOnClickListener { view ->
                view.playCardPressAnimation()
                onPlanClick(plan)
            }
        }

        private fun featureRow(text: String): TextView =
            TextView(binding.root.context).apply {
                setText(text)
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_small, 0, 0, 0)
                compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.spacing_sm)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, resources.getDimensionPixelSize(R.dimen.spacing_xs), 0, resources.getDimensionPixelSize(R.dimen.spacing_xs))
            }
    }
}
