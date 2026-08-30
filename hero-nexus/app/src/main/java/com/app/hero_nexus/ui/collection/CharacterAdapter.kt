package com.app.hero_nexus.ui.collection

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.hero_nexus.R
import com.app.hero_nexus.data.model.Character
import com.app.hero_nexus.data.model.Rarity
import com.app.hero_nexus.databinding.ItemCharacterCardBinding
import com.app.hero_nexus.util.loadCharacterImage
import com.app.hero_nexus.util.visibleIf

class CharacterAdapter(
    private val onClick: (Character) -> Unit
) : ListAdapter<Character, CharacterAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCharacterCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemCharacterCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(character: Character) {
            val ctx = binding.root.context
            binding.textName.text = character.name
            binding.imageCharacter.loadCharacterImage(character.imageUrl)
            binding.imageLock.visibleIf(!character.unlocked)
            binding.textLevel.visibleIf(character.unlocked)
            binding.progressXp.visibleIf(character.unlocked)

            if (character.unlocked) {
                binding.imageCharacter.colorFilter = null
                binding.textLevel.text = ctx.getString(R.string.level_short, character.level)
                binding.textPower.text = "⚔ ${character.stats.overallPower}"
                binding.progressXp.progress = (character.xp % 1000) / 10
            } else {
                val grayscale = ColorMatrix().apply { setSaturation(0f) }
                binding.imageCharacter.colorFilter = ColorMatrixColorFilter(grayscale)
                binding.textPower.text = ctx.getString(R.string.locked_hint)
            }

            binding.textRarity.text = character.rarity.name
            val rarityColor = ContextCompat.getColor(ctx, rarityColorRes(character.rarity))
            binding.textRarity.setTextColor(rarityColor)
            ImageViewCompat.setImageTintList(
                binding.layoutRarity.getChildAt(0) as android.widget.ImageView,
                android.content.res.ColorStateList.valueOf(rarityColor)
            )

            binding.root.setOnClickListener { onClick(character) }
        }

        private fun rarityColorRes(rarity: Rarity): Int = when (rarity) {
            Rarity.COMUM -> R.color.rarity_common
            Rarity.RARO -> R.color.rarity_rare
            Rarity.EPICO -> R.color.rarity_epic
            Rarity.LENDARIO -> R.color.rarity_legendary
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Character>() {
            override fun areItemsTheSame(oldItem: Character, newItem: Character) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Character, newItem: Character) = oldItem == newItem
        }
    }
}
