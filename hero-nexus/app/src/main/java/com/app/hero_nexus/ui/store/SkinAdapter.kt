package com.app.hero_nexus.ui.store

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.app.hero_nexus.R
import com.app.hero_nexus.databinding.ItemSkinBinding
import com.app.hero_nexus.util.loadCharacterImage

data class StoreSkinItem(
    val skinId: String,
    val characterId: Int,
    val skinName: String,
    val priceCoins: Int,
    val characterName: String,
    val characterImageUrl: String?,
    val owned: Boolean,

    val equipped: Boolean
)

class SkinAdapter(
    private val onBuy: (StoreSkinItem) -> Unit,
    private val onEquip: (StoreSkinItem) -> Unit
) : RecyclerView.Adapter<SkinAdapter.VH>() {

    private var items: List<StoreSkinItem> = emptyList()

    fun submitList(list: List<StoreSkinItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSkinBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val binding: ItemSkinBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: StoreSkinItem) {
            val ctx = binding.root.context
            binding.imageSkinCharacter.loadCharacterImage(item.characterImageUrl)
            binding.textSkinCharacterName.text = item.characterName
            binding.textSkinName.text = item.skinName

            when {
                item.owned && item.equipped -> {
                    binding.buttonSkinAction.text = ctx.getString(R.string.skin_equipped)
                    binding.buttonSkinAction.isEnabled = false
                }
                item.owned -> {

                    binding.buttonSkinAction.text = ctx.getString(R.string.skin_equip)
                    binding.buttonSkinAction.isEnabled = true
                }
                item.priceCoins == 0 -> {
                    binding.buttonSkinAction.text = ctx.getString(R.string.skin_free)
                    binding.buttonSkinAction.isEnabled = true
                }
                else -> {
                    binding.buttonSkinAction.text = ctx.getString(R.string.skin_price_coins, item.priceCoins)
                    binding.buttonSkinAction.isEnabled = true
                }
            }
            binding.buttonSkinAction.setOnClickListener {
                if (item.owned) {
                    if (!item.equipped) onEquip(item)
                } else {
                    onBuy(item)
                }
            }
        }
    }
}
