package com.mic.scriptpilot.ui.common.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mic.scriptpilot.databinding.ItemSeoResultBinding
import com.mic.scriptpilot.domain.model.SeoResultLine
import com.mic.scriptpilot.ui.common.playCardPressAnimation

class SeoResultAdapter(
    private val onCopy: (String) -> Unit,
) : ListAdapter<SeoResultLine, SeoResultAdapter.Vh>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemSeoResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding, onCopy)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(getItem(position))
    }

    class Vh(
        private val binding: ItemSeoResultBinding,
        private val onCopy: (String) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SeoResultLine) {
            binding.textBody.text = item.text
            binding.buttonCopy.setOnClickListener { view ->
                view.playCardPressAnimation()
                onCopy(item.text)
            }
        }
    }

}

private object Diff : DiffUtil.ItemCallback<SeoResultLine>() {
    override fun areItemsTheSame(oldItem: SeoResultLine, newItem: SeoResultLine): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: SeoResultLine, newItem: SeoResultLine): Boolean =
        oldItem == newItem
}
