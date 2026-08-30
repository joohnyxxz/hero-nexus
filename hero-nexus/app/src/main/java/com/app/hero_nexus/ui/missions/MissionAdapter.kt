package com.app.hero_nexus.ui.missions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.hero_nexus.R
import com.app.hero_nexus.data.model.Mission
import com.app.hero_nexus.databinding.ItemMissionBinding
import com.app.hero_nexus.util.visibleIf

class MissionAdapter(
    private val onClaim: (Mission) -> Unit
) : ListAdapter<Mission, MissionAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMissionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemMissionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(mission: Mission) {
            val ctx = binding.root.context
            binding.textMissionTitle.text = mission.definition.title
            binding.textMissionReward.text = ctx.getString(
                R.string.mission_reward, mission.definition.rewardXp, mission.definition.rewardCoins
            )
            binding.progressMission.max = mission.definition.target
            binding.progressMission.progress = mission.progress.progress
            binding.textMissionProgress.text = ctx.getString(
                R.string.mission_progress, mission.progress.progress, mission.definition.target
            )
            val claimable = mission.progress.completed && !mission.progress.claimed
            binding.buttonClaim.visibleIf(claimable)
            binding.buttonClaim.setOnClickListener { onClaim(mission) }
            binding.root.alpha = if (mission.progress.claimed) 0.55f else 1f
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Mission>() {
            override fun areItemsTheSame(oldItem: Mission, newItem: Mission) =
                oldItem.definition.id == newItem.definition.id
            override fun areContentsTheSame(oldItem: Mission, newItem: Mission) = oldItem == newItem
        }
    }
}
