package com.mic.scriptpilot.ui.common.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mic.scriptpilot.databinding.ItemSeoResultBinding
import com.mic.scriptpilot.domain.model.SeoResultLine

class SeoResultAdapter : ListAdapter<SeoResultLine, SeoResultAdapter.Vh>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemSeoResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(getItem(position))
    }

    class Vh(
        private val binding: ItemSeoResultBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SeoResultLine) {
            binding.textBody.text = item.text
            binding.textBody.setSingleLine(false)
            binding.textBody.setHorizontallyScrolling(false)
            binding.root.requestLayout()
        }
    }

}

private object Diff : DiffUtil.ItemCallback<SeoResultLine>() {
    override fun areItemsTheSame(oldItem: SeoResultLine, newItem: SeoResultLine): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: SeoResultLine, newItem: SeoResultLine): Boolean =
        oldItem == newItem
}
