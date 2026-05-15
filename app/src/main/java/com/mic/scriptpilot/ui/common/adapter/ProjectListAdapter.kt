package com.mic.scriptpilot.ui.common.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.ItemProjectCompactBinding
import com.mic.scriptpilot.databinding.ItemProjectRowBinding
import com.mic.scriptpilot.domain.model.Project
import com.mic.scriptpilot.domain.model.ProjectType
import com.mic.scriptpilot.ui.common.formatProjectTimestamp
import com.mic.scriptpilot.ui.common.playCardPressAnimation

enum class ProjectCardStyle {
    COMPACT,
    ROW,
}

class ProjectListAdapter(
    private val style: ProjectCardStyle,
    private val onProjectClick: (Project) -> Unit,
) : ListAdapter<Project, RecyclerView.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (style) {
            ProjectCardStyle.COMPACT -> {
                val binding = ItemProjectCompactBinding.inflate(inflater, parent, false)
                CompactVh(binding, onProjectClick)
            }
            ProjectCardStyle.ROW -> {
                val binding = ItemProjectRowBinding.inflate(inflater, parent, false)
                RowVh(binding, onProjectClick)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is CompactVh -> holder.bind(item)
            is RowVh -> holder.bind(item)
        }
    }

    class CompactVh(
        private val binding: ItemProjectCompactBinding,
        private val onProjectClick: (Project) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Project) {
            binding.textTitle.text = item.title
            binding.textPreview.text = item.script.lines().firstOrNull().orEmpty()
            binding.chipType.text = chipLabel(binding.root.context, item.type)
            binding.root.setOnClickListener { view ->
                view.playCardPressAnimation()
                onProjectClick(item)
            }
        }
    }

    class RowVh(
        private val binding: ItemProjectRowBinding,
        private val onProjectClick: (Project) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Project) {
            binding.textTitle.text = item.title
            binding.textPreview.text = item.script.lines().take(3).joinToString("\n")
            binding.textDate.text = formatProjectTimestamp(item.timestamp)
            binding.chipType.text = chipLabel(binding.root.context, item.type)
            binding.root.setOnClickListener { view ->
                view.playCardPressAnimation()
                onProjectClick(item)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Project>() {
        override fun areItemsTheSame(oldItem: Project, newItem: Project): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Project, newItem: Project): Boolean = oldItem == newItem
    }
}

private fun chipLabel(context: android.content.Context, type: ProjectType): String {
    val res = when (type) {
        ProjectType.IDEA -> R.string.project_type_idea
        ProjectType.SCRIPT -> R.string.project_type_script
        ProjectType.SHORT -> R.string.project_type_short
    }
    return context.getString(res)
}
