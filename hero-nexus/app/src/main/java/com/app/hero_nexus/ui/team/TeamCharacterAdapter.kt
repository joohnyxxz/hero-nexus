package com.app.hero_nexus.ui.team

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.hero_nexus.R
import com.app.hero_nexus.data.model.Character
import com.app.hero_nexus.data.model.Rarity
import com.app.hero_nexus.databinding.ItemCharacterCardBinding
import com.app.hero_nexus.util.loadCharacterImage
import com.app.hero_nexus.util.visibleIf
import com.google.android.material.card.MaterialCardView

/** Grade de personagens JÁ DESBLOQUEADOS para montar o time (seção 11). */
class TeamCharacterAdapter(
    private val isSelected: (Character) -> Boolean,
    private val onClick: (Character) -> Unit
) : ListAdapter<Character, TeamCharacterAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCharacterCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemCharacterCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(character: Character) {
            binding.textName.text = character.name
            binding.imageCharacter.loadCharacterImage(character.imageUrl)
            binding.imageLock.visibleIf(false)
            binding.textLevel.text = binding.root.context.getString(R.string.level_short, character.level)
            binding.textPower.text = character.stats.overallPower.toString()
            binding.progressXp.progress = (character.xp % 1000) / 10
            binding.textRarity.text = character.rarity.name

            // Rodada 12 (01/09): mesmo header colorido por raridade do card da Colecao
            // (CharacterAdapter.kt) -- esse card e literalmente o mesmo layout, ficava
            // inconsistente (header branco sem cor) se so um dos dois adapters tintasse.
            val ctx = binding.root.context
            val rarityColorRes = when (character.rarity) {
                Rarity.COMUM -> R.color.rarity_common
                Rarity.RARO -> R.color.rarity_rare
                Rarity.EPICO -> R.color.rarity_epic
                Rarity.LENDARIO -> R.color.rarity_legendary
            }
            val rarityColor = ctx.getColor(rarityColorRes)
            binding.headerBar.backgroundTintList = android.content.res.ColorStateList.valueOf(rarityColor)

            val card = binding.root as MaterialCardView
            val selected = isSelected(character)
            card.strokeWidth = if (selected) 4 else 0
            card.strokeColor = if (selected) ctx.getColor(R.color.reward_gold) else rarityColor
            binding.root.alpha = if (selected) 1f else 0.9f

            binding.root.setOnClickListener { onClick(character) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Character>() {
            override fun areItemsTheSame(oldItem: Character, newItem: Character) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Character, newItem: Character) = oldItem == newItem
        }
    }
}
