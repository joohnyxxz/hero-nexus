package com.app.hero_nexus.ui.collection

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.google.android.material.card.MaterialCardView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
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

            binding.progressXp.visibility = if (character.unlocked) android.view.View.VISIBLE else android.view.View.INVISIBLE

            binding.iconPower.visibility = if (character.unlocked) android.view.View.VISIBLE else android.view.View.INVISIBLE
            binding.viewLockedScrim.visibleIf(!character.unlocked)

            if (character.unlocked) {
                binding.imageCharacter.colorFilter = null
                binding.textLevel.text = ctx.getString(R.string.level_short, character.level)
                binding.textPower.text = character.stats.overallPower.toString()
                binding.textPower.setTextColor(ContextCompat.getColor(ctx, R.color.reward_gold))
                binding.progressXp.progress = (character.xp % 1000) / 10
            } else {
                val grayscale = ColorMatrix().apply { setSaturation(0f) }
                binding.imageCharacter.colorFilter = ColorMatrixColorFilter(grayscale)
                binding.textPower.text = ctx.getString(R.string.locked_hint)
                binding.textPower.setTextColor(ContextCompat.getColor(ctx, R.color.text_muted))
            }

            binding.textRarity.text = if (character.unlocked) character.rarity.name else "???"

            val trueRarityColor = ContextCompat.getColor(ctx, rarityColorRes(character.rarity))
            val rarityColor = if (character.unlocked) {
                trueRarityColor
            } else {
                ColorUtils.blendARGB(
                    ContextCompat.getColor(ctx, R.color.rarity_locked),
                    trueRarityColor,
                    LOCKED_RARITY_HINT_RATIO
                )
            }

            binding.headerBar.backgroundTintList = android.content.res.ColorStateList.valueOf(rarityColor)

            val card = binding.root as MaterialCardView
            card.strokeColor = rarityColor

            card.strokeWidth = ctx.resources.getDimensionPixelSize(
                if (character.unlocked && character.rarity == Rarity.LENDARIO) {
                    R.dimen.card_stroke_width_legendary
                } else {
                    R.dimen.card_stroke_width_normal
                }
            )

            binding.dividerStats.setBackgroundColor(rarityColor)

            binding.imageRarityGem.backgroundTintList = android.content.res.ColorStateList.valueOf(rarityColor)

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

        private const val LOCKED_RARITY_HINT_RATIO = 0.32f

        private val DIFF = object : DiffUtil.ItemCallback<Character>() {
            override fun areItemsTheSame(oldItem: Character, newItem: Character) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Character, newItem: Character) = oldItem == newItem
        }
    }
}
