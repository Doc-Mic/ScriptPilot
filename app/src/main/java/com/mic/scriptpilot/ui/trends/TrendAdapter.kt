package com.mic.scriptpilot.ui.trends

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.ItemTrendCardBinding
import com.mic.scriptpilot.ui.common.playCardPressAnimation
import java.text.NumberFormat

class TrendAdapter(
    private val onTrendClick: (TrendUiModel) -> Unit,
) : ListAdapter<TrendUiModel, TrendAdapter.TrendViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendViewHolder {
        val binding = ItemTrendCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrendViewHolder(binding, onTrendClick)
    }

    override fun onBindViewHolder(holder: TrendViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TrendViewHolder(
        private val binding: ItemTrendCardBinding,
        private val onTrendClick: (TrendUiModel) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(model: TrendUiModel) {
            binding.textTitle.text = model.title
            binding.chipMomentum.text = model.momentumLabel
            binding.progressVirality.max = 100
            binding.progressOpportunity.max = 100
            binding.progressVirality.progress = model.virality
            binding.progressOpportunity.progress = model.opportunity
            val numberFormat = NumberFormat.getIntegerInstance()
            binding.textViralityValue.text = numberFormat.format(model.virality)
            binding.textOpportunityValue.text = numberFormat.format(model.opportunity)
            binding.textExplanation.text = model.explanation
            binding.chipCompetition.text = model.competitionLabel
            styleCompetitionChip(binding.root.context, model.competitionLabel)

            binding.chipGroupSources.removeAllViews()
            val chipContext = binding.root.context
            model.sources.forEach { label ->
                val chip =
                    Chip(chipContext).apply {
                        text = label
                        isClickable = false
                        isCheckable = false
                        isFocusable = false
                        chipStrokeWidth = 0f
                        setChipBackgroundColorResource(R.color.label_pill_bg)
                    }
                binding.chipGroupSources.addView(chip)
            }

            binding.root.setOnClickListener { v ->
                v.playCardPressAnimation()
                onTrendClick(model)
            }
        }

        private fun styleCompetitionChip(context: Context, label: String) {
            val (backgroundRes, textColorRes) =
                when (label.trim().lowercase()) {
                    "low" -> R.color.trend_competition_low to R.color.trend_competition_low_text
                    "high" -> R.color.trend_competition_high to R.color.trend_competition_high_text
                    else -> R.color.trend_competition_medium to R.color.trend_competition_medium_text
                }
            binding.chipCompetition.chipBackgroundColor =
                ColorStateList.valueOf(ContextCompat.getColor(context, backgroundRes))
            binding.chipCompetition.setTextColor(ContextCompat.getColor(context, textColorRes))
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TrendUiModel>() {
        override fun areItemsTheSame(oldItem: TrendUiModel, newItem: TrendUiModel): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TrendUiModel, newItem: TrendUiModel): Boolean =
            oldItem == newItem
    }
}
