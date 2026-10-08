package com.app.hero_nexus.ui.store

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.hero_nexus.R
import com.app.hero_nexus.data.model.SkinCatalog
import com.app.hero_nexus.databinding.ActivityStoreBinding
import com.app.hero_nexus.ui.common.MainNavActivity
import com.app.hero_nexus.util.visibleIf
import kotlinx.coroutines.launch

class SkinStoreActivity : MainNavActivity() {

    private lateinit var binding: ActivityStoreBinding
    private lateinit var adapter: SkinAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar(
            binding.topBar.textScreenTitle,
            binding.topBar.textCoins,
            binding.topBar.textLevel,
            binding.topBar.buttonLogout,
            R.string.store_title
        )
        setupBottomNav(binding.bottomNav, R.id.nav_store)

        adapter = SkinAdapter(onBuy = { item -> buySkin(item) }, onEquip = { item -> equipSkin(item) })
        binding.recyclerSkins.layoutManager = LinearLayoutManager(this)
        binding.recyclerSkins.adapter = adapter

        loadSkins()
    }

    private fun loadSkins() {
        val uid = app.userRepository.currentUid ?: return
        lifecycleScope.launch {
            val all = app.characterRepository.getAllCached()
            val states = runCatching { app.userRepository.getCharacterStates(uid) }.getOrDefault(emptyMap())
            val owned = runCatching { app.userRepository.getOwnedSkinIds(uid) }.getOrDefault(emptySet())

            val unlocked = all.filter { states[it.comicVineId]?.unlocked == true }
            val items = unlocked.flatMap { character ->

                val equippedId = states[character.comicVineId]?.equippedSkinId
                    ?: "${character.comicVineId}_classic"
                SkinCatalog.forCharacter(character.comicVineId, character.name).map { skin ->
                    StoreSkinItem(
                        skinId = skin.id,
                        characterId = character.comicVineId,
                        skinName = skin.name,
                        priceCoins = skin.priceCoins,
                        characterName = character.name,
                        characterImageUrl = character.imageUrl,
                        owned = owned.contains(skin.id) || skin.isFree,
                        equipped = skin.id == equippedId
                    )
                }
            }
            adapter.submitList(items)
            binding.textEmptyStore.visibleIf(items.isEmpty())
        }
    }

    private fun buySkin(item: StoreSkinItem) {
        val uid = app.userRepository.currentUid ?: return
        lifecycleScope.launch {
            val success = runCatching {
                app.userRepository.purchaseSkin(uid, item.skinId, item.priceCoins)
            }.getOrDefault(false)
            if (success) {
                refreshProfileHeader(binding.topBar.textCoins, binding.topBar.textLevel)
                loadSkins()
            } else {
                Toast.makeText(this@SkinStoreActivity, R.string.skin_not_enough_coins, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun equipSkin(item: StoreSkinItem) {
        val uid = app.userRepository.currentUid ?: return
        lifecycleScope.launch {
            runCatching { app.userRepository.equipSkin(uid, item.characterId, item.skinId) }
                .onFailure {
                    Toast.makeText(this@SkinStoreActivity, R.string.error_generic, Toast.LENGTH_SHORT).show()
                }
            loadSkins()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshProfileHeader(binding.topBar.textCoins, binding.topBar.textLevel)
    }
}
