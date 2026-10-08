package com.app.hero_nexus.ui.chests

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.app.hero_nexus.R
import com.app.hero_nexus.data.model.ChestReward
import com.app.hero_nexus.data.model.ChestType
import com.app.hero_nexus.data.model.CharacterCategory
import com.app.hero_nexus.databinding.ActivityChestsBinding
import com.app.hero_nexus.databinding.DialogChestRewardBinding
import com.app.hero_nexus.ui.common.MainNavActivity
import com.app.hero_nexus.util.formatCoins
import com.app.hero_nexus.util.loadCharacterImage
import com.app.hero_nexus.util.visibleIf
import kotlinx.coroutines.launch
import kotlin.random.Random

class ChestsActivity : MainNavActivity() {

    private lateinit var binding: ActivityChestsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar(
            binding.topBar.textScreenTitle,
            binding.topBar.textCoins,
            binding.topBar.textLevel,
            binding.topBar.buttonLogout,
            R.string.chests_title
        )
        setupBottomNav(binding.bottomNav, R.id.nav_chests)

        binding.chestHero.textChestName.setText(R.string.chest_hero)
        binding.chestSpecial.textChestName.setText(R.string.chest_special)
        binding.chestHero.buttonOpenChest.setOnClickListener { openChest(ChestType.HEROI) }
        binding.chestSpecial.buttonOpenChest.setOnClickListener { openChest(ChestType.ESPECIAL) }

        refreshCounts()
    }

    override fun onResume() {
        super.onResume()
        refreshCounts()
        refreshProfileHeader(binding.topBar.textCoins, binding.topBar.textLevel)
    }

    private fun refreshCounts() {
        val uid = app.userRepository.currentUid ?: return
        lifecycleScope.launch {
            val profile = runCatching { app.userRepository.getProfile(uid) }.getOrNull() ?: return@launch
            binding.chestHero.textChestCount.text = "x ${profile.heroChests}"
            binding.chestSpecial.textChestCount.text = "x ${profile.specialChests}"
            binding.chestHero.buttonOpenChest.isEnabled = profile.heroChests > 0
            binding.chestSpecial.buttonOpenChest.isEnabled = profile.specialChests > 0
        }
    }

    private fun openChest(type: ChestType) {
        val uid = app.userRepository.currentUid ?: return
        lifecycleScope.launch {
            val consumed = runCatching { app.userRepository.tryConsumeChest(uid, type) }.getOrDefault(false)
            if (!consumed) {
                Toast.makeText(this@ChestsActivity, "Você não tem esse baú.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val reward = rollReward(uid, type)

            runCatching { applyReward(uid, reward) }.onFailure { e ->
                android.util.Log.e("ChestsActivity", "Falha ao aplicar recompensa do baú", e)
                Toast.makeText(
                    this@ChestsActivity,
                    "Recompensa sorteada mas não salvou no servidor (${e.message ?: "erro"}). Tente abrir outro baú se sobrar.",
                    Toast.LENGTH_LONG
                ).show()
            }
            showRewardDialog(reward)
            refreshCounts()
            refreshProfileHeader(binding.topBar.textCoins, binding.topBar.textLevel)
        }
    }

    private suspend fun rollReward(uid: String, type: ChestType): ChestReward {
        val all = app.characterRepository.getAllCached()
        val states = runCatching { app.userRepository.getCharacterStates(uid) }.getOrDefault(emptyMap())
        val eligible = all.filter {
            (it.category == CharacterCategory.HEROI.name || it.category == CharacterCategory.ANTI_HEROI.name) &&
                states[it.comicVineId]?.unlocked != true
        }
        val characterChance = if (type == ChestType.ESPECIAL) 0.6f else 0.35f
        return if (eligible.isNotEmpty() && Random.nextFloat() < characterChance) {
            ChestReward(newCharacterId = eligible.random().comicVineId)
        } else if (type == ChestType.ESPECIAL) {
            ChestReward(coins = Random.nextInt(150, 400), xp = Random.nextInt(100, 300))
        } else {
            ChestReward(coins = Random.nextInt(50, 150), xp = Random.nextInt(20, 100))
        }
    }

    private suspend fun applyReward(uid: String, reward: ChestReward) {
        if (reward.newCharacterId != null) {
            app.userRepository.unlockCharacter(uid, reward.newCharacterId)
        } else {
            app.userRepository.addXpAndCoins(uid, reward.xp, reward.coins)
        }
    }

    private suspend fun showRewardDialog(reward: ChestReward) {
        val dialogBinding = DialogChestRewardBinding.inflate(layoutInflater)
        if (reward.newCharacterId != null) {
            val entity = app.characterRepository.getCached(reward.newCharacterId)
            dialogBinding.imageReward.visibleIf(true)
            dialogBinding.imageReward.loadCharacterImage(entity?.imageUrl)
            dialogBinding.textRewardTitle.setText(R.string.chest_new_character)
            dialogBinding.textRewardSubtitle.text = entity?.name.orEmpty()
        } else {
            dialogBinding.imageReward.visibleIf(false)
            dialogBinding.textRewardTitle.text = "Recompensas"
            dialogBinding.textRewardSubtitle.text = "+${reward.coins.formatCoins()} moedas, +${reward.xp} XP"
        }
        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
