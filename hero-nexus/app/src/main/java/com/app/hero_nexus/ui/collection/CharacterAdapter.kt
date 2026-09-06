package com.app.hero_nexus.ui.collection

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.google.android.material.card.MaterialCardView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
                binding.textPower.text = character.stats.overallPower.toString()
                binding.progressXp.progress = (character.xp % 1000) / 10
            } else {
                val grayscale = ColorMatrix().apply { setSaturation(0f) }
                binding.imageCharacter.colorFilter = ColorMatrixColorFilter(grayscale)
                binding.textPower.text = ctx.getString(R.string.locked_hint)
            }

            binding.textRarity.text = character.rarity.name
            val rarityColor = ContextCompat.getColor(ctx, rarityColorRes(character.rarity))

            // Rodada 12 (01/09): a raridade e o nivel viraram uma unica faixa colorida no topo
            // do card (bg_card_header, tintada com a cor da raridade) em vez de dois chips soltos
            // nos cantos -- texto escuro fixo (bg_deep_blue, ja definido no XML) pra ficar legivel
            // em cima de qualquer uma das 4 cores de raridade.
            binding.headerBar.backgroundTintList = android.content.res.ColorStateList.valueOf(rarityColor)

            // Rodada 10 (01/09): antes a borda do card era sempre a mesma cor neutra
            // (card_stroke) pra todo mundo, a raridade so aparecia no textinho pequeno no
            // canto. Agora a moldura inteira do card usa a cor da raridade, o que da muito
            // mais variedade visual pra grade inteira (feedback: "a visualizacao dos card
            // poderia ser melhor").
            (binding.root as MaterialCardView).strokeColor = rarityColor

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
