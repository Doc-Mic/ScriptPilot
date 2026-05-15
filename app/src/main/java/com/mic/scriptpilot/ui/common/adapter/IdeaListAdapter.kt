package com.mic.scriptpilot.ui.common.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mic.scriptpilot.databinding.ItemIdeaRowBinding
import com.mic.scriptpilot.domain.model.IdeaItem
import com.mic.scriptpilot.ui.common.playCardPressAnimation

class IdeaListAdapter(
    private val onIdeaClick: (IdeaItem) -> Unit,
) : ListAdapter<IdeaItem, IdeaListAdapter.Vh>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemIdeaRowBinding.inflate(inflater, parent, false)
        return Vh(binding, onIdeaClick)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(getItem(position))
    }

    class Vh(
        private val binding: ItemIdeaRowBinding,
        private val onIdeaClick: (IdeaItem) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: IdeaItem) {
            binding.textTitle.text = item.title
            binding.textAngle.text = item.angle
            binding.root.setOnClickListener { view ->
                view.playCardPressAnimation()
                onIdeaClick(item)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<IdeaItem>() {
        override fun areItemsTheSame(oldItem: IdeaItem, newItem: IdeaItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: IdeaItem, newItem: IdeaItem): Boolean = oldItem == newItem
    }
}
