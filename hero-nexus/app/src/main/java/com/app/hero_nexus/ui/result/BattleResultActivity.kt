package com.app.hero_nexus.ui.result

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.app.hero_nexus.R
import com.app.hero_nexus.data.model.BattleResult
import com.app.hero_nexus.data.model.ChestType
import com.app.hero_nexus.databinding.ActivityBattleResultBinding
import com.app.hero_nexus.ui.battle.BattleActivity
import com.app.hero_nexus.ui.collection.CollectionActivity
import com.app.hero_nexus.util.visibleIf

/** Tela de resultado — seção 23 do documento (Vitória / Derrota). */
class BattleResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBattleResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBattleResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val victory = intent.getBooleanExtra(EXTRA_VICTORY, false)
        val enemies = intent.getIntExtra(EXTRA_ENEMIES, 0)
        val bossName = intent.getStringExtra(EXTRA_BOSS_NAME)
        val xp = intent.getIntExtra(EXTRA_XP, 0)
        val coins = intent.getIntExtra(EXTRA_COINS, 0)
        val chest = intent.getStringExtra(EXTRA_CHEST)
        val teamIds = intent.getIntArrayExtra(EXTRA_TEAM_IDS) ?: intArrayOf()

        binding.textResultTitle.text = getString(if (victory) R.string.battle_victory else R.string.battle_defeat)
        binding.textResultTitle.setTextColor(
            getColor(if (victory) R.color.reward_gold else R.color.error_red)
        )

        binding.textEnemiesDefeated.text = getString(R.string.result_enemies_defeated, enemies)
        binding.textXpGained.text = getString(R.string.result_xp_gained, xp)
        binding.textCoinsGained.text = getString(R.string.result_coins_gained, coins)

        binding.textBossDefeated.visibleIf(bossName != null)
        bossName?.let { binding.textBossDefeated.text = getString(R.string.result_boss_defeated, it) }

        binding.textChestAwarded.visibleIf(chest != null)
        chest?.let {
            val chestNameRes = if (it == ChestType.ESPECIAL.name) R.string.chest_special else R.string.chest_hero
            binding.textChestAwarded.text = getString(R.string.result_chest_awarded, getString(chestNameRes))
        }

        binding.buttonPlayAgain.setText(if (victory) R.string.result_play_again else R.string.result_try_again)
        binding.buttonPlayAgain.setOnClickListener {
            startActivity(BattleActivity.newIntent(this, teamIds))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }
        binding.buttonBackToCollection.setOnClickListener {
            startActivity(
                Intent(this, CollectionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }
    }

    companion object {
        private const val EXTRA_VICTORY = "extra_victory"
        private const val EXTRA_ENEMIES = "extra_enemies"
        private const val EXTRA_BOSS_NAME = "extra_boss_name"
        private const val EXTRA_XP = "extra_xp"
        private const val EXTRA_COINS = "extra_coins"
        private const val EXTRA_CHEST = "extra_chest"
        private const val EXTRA_TEAM_IDS = "extra_team_ids"

        fun newIntent(context: Context, result: BattleResult, teamIds: IntArray): Intent =
            Intent(context, BattleResultActivity::class.java)
                .putExtra(EXTRA_VICTORY, result.victory)
                .putExtra(EXTRA_ENEMIES, result.enemiesDefeated)
                .putExtra(EXTRA_BOSS_NAME, result.bossName)
                .putExtra(EXTRA_XP, result.xpGained)
                .putExtra(EXTRA_COINS, result.coinsGained)
                .putExtra(EXTRA_CHEST, result.chestAwarded?.name)
                .putExtra(EXTRA_TEAM_IDS, teamIds)
    }
}
