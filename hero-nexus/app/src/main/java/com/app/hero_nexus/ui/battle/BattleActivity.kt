package com.app.hero_nexus.ui.battle

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.app.hero_nexus.HeroNexusApp
import com.app.hero_nexus.R
import com.app.hero_nexus.data.local.toDomain
import com.app.hero_nexus.data.model.BattleResult
import com.app.hero_nexus.data.model.Character
import com.app.hero_nexus.data.model.CharacterCategory
import com.app.hero_nexus.data.model.UserCharacterState
import com.app.hero_nexus.databinding.ActivityBattleBinding
import com.app.hero_nexus.ui.result.BattleResultActivity
import com.app.hero_nexus.util.visibleIf
import kotlinx.coroutines.launch
import kotlin.math.hypot

class BattleActivity : AppCompatActivity(), BattleListener {

    private lateinit var binding: ActivityBattleBinding
    private val app get() = application as HeroNexusApp
    private lateinit var engine: BattleEngine

    private val hudHandler = Handler(Looper.getMainLooper())
    private var hudRunning = false
    private var teamIds: IntArray = intArrayOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBattleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonExit.setOnClickListener { finish() }
        setupJoystick()
        setupAttackButton()

        val ids = intent.getIntArrayExtra(EXTRA_TEAM_IDS)?.toList().orEmpty()
        teamIds = ids.toIntArray()
        lifecycleScope.launch {
            val uid = app.userRepository.currentUid
            val states = uid?.let { runCatching { app.userRepository.getCharacterStates(it) }.getOrDefault(emptyMap()) }
                ?: emptyMap()
            val team = ids.mapNotNull { id ->
                app.characterRepository.getCached(id)?.toDomain(states[id] ?: UserCharacterState())
            }
            if (team.isEmpty()) {
                finish()
                return@launch
            }
            val boss = pickBoss(states)
            engine = binding.battleView.setup(team, boss, this@BattleActivity)
            binding.textPlayerName.text = team.first().name
            startHudLoop()
        }
    }

    private suspend fun pickBoss(states: Map<Int, UserCharacterState>): Character? {
        val all = app.characterRepository.getAllCached()
        val villains = all.filter { it.category == CharacterCategory.VILAO.name }
        if (villains.isEmpty()) return null
        val notUnlocked = villains.filter { states[it.comicVineId]?.unlocked != true }
        val chosen = (notUnlocked.ifEmpty { villains }).random()
        return chosen.toDomain(states[chosen.comicVineId] ?: UserCharacterState())
    }

    // ------------------------------------------------------------------------------ HUD polling

    private fun startHudLoop() {
        hudRunning = true
        hudHandler.post(hudTick)
    }

    private val hudTick = object : Runnable {
        override fun run() {
            if (!hudRunning || !::engine.isInitialized || !engine.started) {
                if (hudRunning) hudHandler.postDelayed(this, 120L)
                return
            }
            if (engine.state == BattleState.RUNNING) {
                binding.progressPlayerHealth.progress = (engine.player.healthRatio * 100).toInt()
                binding.textPlayerName.text = engine.player.character.name
                binding.textEnemiesDefeated.text = "Inimigos: ${engine.enemiesDefeated}"
                binding.textCoins.text = engine.coinsCollected.toString()

                val bossEnemy = engine.enemies.firstOrNull { it.isBoss }
                binding.layoutBossHealth.visibleIf(bossEnemy != null)
                bossEnemy?.let {
                    binding.progressBossHealth.progress = (it.healthRatio * 100).toInt()
                }
            }
            hudHandler.postDelayed(this, 120L)
        }
    }

    // ------------------------------------------------------------------------------- Controles

    private fun setupJoystick() {
        val area = binding.joystickArea
        val knob = binding.joystickKnob
        val maxRadius = 40f

        area.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val centerX = area.width / 2f
                    val centerY = area.height / 2f
                    var dx = event.x - centerX
                    var dy = event.y - centerY
                    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (dist > maxRadius) {
                        dx = dx / dist * maxRadius
                        dy = dy / dist * maxRadius
                    }
                    knob.translationX = dx
                    knob.translationY = dy
                    val normX = (dx / maxRadius).coerceIn(-1f, 1f)
                    val normY = (dy / maxRadius).coerceIn(-1f, 1f)
                    if (::engine.isInitialized) binding.battleView.setMove(normX, normY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    knob.translationX = 0f
                    knob.translationY = 0f
                    if (::engine.isInitialized) binding.battleView.setMove(0f, 0f)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupAttackButton() {
        binding.buttonAttack.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.battleView.setAttackPressed(true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.battleView.setAttackPressed(false)
                    true
                }
                else -> false
            }
        }
    }

    // --------------------------------------------------------------------------- BattleListener

    override fun onCharacterDefeated(name: String) {
        runOnUiThread { showBanner(getString(R.string.battle_defeated_switch, name.uppercase())) }
    }

    override fun onNextCharacterEnters(name: String) {
        runOnUiThread { showBanner(getString(R.string.battle_next_enters, name)) }
    }

    override fun onBossIncoming(name: String) {
        runOnUiThread {
            binding.textBossName.text = name.uppercase()
            binding.layoutBossHealth.visibleIf(true)
            showBanner(getString(R.string.boss_incoming))
        }
    }

    override fun onBossDefeated(name: String) {
        runOnUiThread { showBanner(getString(R.string.boss_defeated)) }
    }

    override fun onCratesOrCoins() = Unit // HUD já é atualizado pelo polling

    override fun onBattleEnded(result: BattleResult) {
        hudRunning = false
        runOnUiThread {
            lifecycleScope.launch {
                persistResult(result)
                startActivity(BattleResultActivity.newIntent(this@BattleActivity, result, teamIds))
                finish()
            }
        }
    }

    private suspend fun persistResult(result: BattleResult) {
        val uid = app.userRepository.currentUid ?: return
        runCatching {
            app.userRepository.addXpAndCoins(uid, result.xpGained, result.coinsGained)
            app.userRepository.recordBattleResult(uid, result)
            if (result.bossCharacterId != null) {
                app.userRepository.unlockCharacter(uid, result.bossCharacterId)
            }
            result.chestAwarded?.let { app.userRepository.awardChest(uid, it) }
            if (result.enemiesDefeated > 0) {
                app.userRepository.incrementMissionProgress(uid, "kill_20_enemies", result.enemiesDefeated)
            }
            if (result.cratesBroken > 0) {
                app.userRepository.incrementMissionProgress(uid, "break_10_crates", result.cratesBroken)
            }
            if (result.victory) {
                app.userRepository.incrementMissionProgress(uid, "complete_1_stage", 1)
            }
            if (result.bossName != null) {
                app.userRepository.incrementMissionProgress(uid, "defeat_1_boss", 1)
            }
        }
    }

    private fun showBanner(text: String) {
        binding.textBanner.text = text
        binding.textBanner.visibleIf(true)
        hudHandler.postDelayed({ binding.textBanner.visibleIf(false) }, 1800L)
    }

    override fun onPause() {
        super.onPause()
        if (::engine.isInitialized) binding.battleView.pauseLoop()
    }

    override fun onResume() {
        super.onResume()
        if (::engine.isInitialized) binding.battleView.resumeLoop()
    }

    override fun onDestroy() {
        super.onDestroy()
        hudRunning = false
    }

    companion object {
        private const val EXTRA_TEAM_IDS = "extra_team_ids"
        fun newIntent(context: Context, teamIds: IntArray): Intent =
            Intent(context, BattleActivity::class.java).putExtra(EXTRA_TEAM_IDS, teamIds)
    }
}
