package com.app.hero_nexus.ui.battle

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import android.util.Log
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
import com.app.hero_nexus.util.applyStatusBarTopInset
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
    private var loadingDismissed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBattleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.layoutHudTop.applyStatusBarTopInset()

        binding.buttonExit.setOnClickListener { exitBattle() }
        setupJoystick()
        setupAttackButton()
        setupSpecialButton()
        setupFoodButton()

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
        findNextVillainToUnlock(states)?.let { return it }
        findNextHeroToUnlock(states)?.let { return it }

        var attempts = 0
        while (app.characterRepository.hasMore && attempts < MAX_BOSS_SEARCH_BATCHES) {
            app.characterRepository.loadMore()
            attempts++
            findNextVillainToUnlock(states)?.let { return it }
            findNextHeroToUnlock(states)?.let { return it }
        }

        val villains = app.characterRepository.getAllCached()
            .filter { it.category == CharacterCategory.VILAO.name }
            .map { it.toDomain(states[it.comicVineId] ?: UserCharacterState()) }
            .sortedBy { it.stats.overallPower }
        if (villains.isEmpty()) return null

        val avgTeamLevel = teamIds.map { states[it]?.level ?: 1 }.average().takeIf { !it.isNaN() } ?: 1.0
        val idx = (avgTeamLevel.toInt() - 1).coerceAtLeast(0) % villains.size
        return villains[idx]
    }

    private suspend fun findNextVillainToUnlock(states: Map<Int, UserCharacterState>): Character? {
        val villains = app.characterRepository.getAllCached()
            .filter { it.category == CharacterCategory.VILAO.name }
            .map { it.toDomain(states[it.comicVineId] ?: UserCharacterState()) }
            .sortedBy { it.stats.overallPower }
        if (villains.isEmpty()) return null

        val prioritizedNames = listOf("lizard", "lagarto", "carnage", "carnificina", "doom", "destino")
        prioritizedNames.forEach { pName ->
            villains.firstOrNull {
                !it.unlocked && it.name.contains(pName, ignoreCase = true)
            }?.let { return it }
        }

        return villains.firstOrNull { !it.unlocked }
    }

    private suspend fun findNextHeroToUnlock(states: Map<Int, UserCharacterState>): Character? {
        val heroes = app.characterRepository.getAllCached()
            .filter {
                (it.category == CharacterCategory.HEROI.name || it.category == CharacterCategory.ANTI_HEROI.name) &&
                    states[it.comicVineId]?.unlocked != true
            }
            .map { it.toDomain(states[it.comicVineId] ?: UserCharacterState()) }
            .filter { hero -> FAMOUS_HERO_BLOCKLIST.none { hero.name.contains(it, ignoreCase = true) } }
        if (heroes.isEmpty()) return null
        return heroes.random()
    }

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

            dismissLoadingOverlay()
            if (engine.state == BattleState.RUNNING) {
                binding.progressPlayerHealth.progress = (engine.player.healthRatio * 100).toInt()
                binding.textPlayerName.text = engine.player.character.name
                binding.textCoins.text = engine.coinsCollected.toString()

                val bossEnemy = engine.enemies.firstOrNull { it.isBoss }

                updateSpecialHud()
                updateFoodHud()
            }
            hudHandler.postDelayed(this, 120L)
        }
    }

    private fun updateSpecialHud() {
        val player = engine.player
        binding.pipSpecial1.alpha = if (player.storedSpecials >= 1) 1f else 0.28f
        binding.pipSpecial2.alpha = if (player.storedSpecials >= 2) 1f else 0.28f
        binding.buttonSpecial.isEnabled = player.storedSpecials >= 1
        binding.buttonSpecial.alpha = if (player.storedSpecials >= 1) 1f else 0.55f
    }

    private fun updateFoodHud() {
        val charges = engine.player.foodCharges
        binding.buttonFood.visibleIf(charges > 0)
        binding.textFoodCount.visibleIf(charges > 0)
        binding.textFoodCount.text = charges.toString()
    }

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

        binding.buttonAttack.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    binding.battleView.setAttackPressed(true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    binding.battleView.setAttackPressed(false)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSpecialButton() {
        binding.buttonSpecial.setOnClickListener {
            if (::engine.isInitialized) binding.battleView.triggerSpecial()
        }
    }

    private fun setupFoodButton() {
        binding.buttonFood.setOnClickListener {
            if (::engine.isInitialized) binding.battleView.triggerUseFood()
        }
    }

    override fun onCharacterDefeated(name: String) = Unit

    override fun onNextCharacterEnters(name: String) = Unit

    override fun onBossIncoming(name: String) = Unit

    override fun onBossDefeated(name: String) = Unit

    override fun onMinibossDefeated(name: String) = Unit

    override fun onCratesOrCoins() = Unit

    override fun onWaveCleared(wave: Int, totalWaves: Int) = Unit

    override fun onWaveAdvanced(wave: Int, totalWaves: Int) = Unit

    override fun onFoodPickup(amount: Int) = Unit

    override fun onFoodConsumed(amount: Int) = Unit

    override fun onBonusCharacterPickup() = Unit

    override fun onSpecialUsed() = Unit

    override fun onBattleEnded(result: BattleResult) {
        hudRunning = false
        runOnUiThread {
            lifecycleScope.launch {
                val coinsError = persistResult(result)

                if (coinsError != null) {
                    Toast.makeText(
                        this@BattleActivity,
                        "Não consegui salvar suas moedas/XP no servidor ($coinsError). " +
                            "Verifique sua internet e as regras do Firestore -- tente de novo mais tarde.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                startActivity(BattleResultActivity.newIntent(this@BattleActivity, result, teamIds))

                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            }
        }
    }

    private suspend fun persistResult(result: BattleResult): String? {
        val uid = app.userRepository.currentUid ?: return "sessão expirada"

        var coinsError: String? = null

        suspend fun step(label: String, critical: Boolean = false, block: suspend () -> Unit) {
            runCatching { block() }.onFailure {
                Log.e(TAG, "Falha ao salvar resultado da batalha ($label)", it)
                if (critical) coinsError = it.message ?: label
            }
        }

        step("addXpAndCoins", critical = true) {
            app.userRepository.addXpAndCoins(uid, result.xpGained, result.coinsGained)
        }
        step("recordBattleResult") { app.userRepository.recordBattleResult(uid, result) }
        if (result.bossCharacterId != null) {
            step("unlockCharacter") { app.userRepository.unlockCharacter(uid, result.bossCharacterId) }
        }
        result.chestAwarded?.let { chest ->
            step("awardChest") { app.userRepository.awardChest(uid, chest) }
        }
        if (result.enemiesDefeated > 0) {
            listOf("daily_kill_enemies_t1", "daily_kill_enemies_t2", "weekly_mass_slayer_t1", "weekly_mass_slayer_t2").forEach { missionId ->
                step("mission:$missionId") {
                    app.userRepository.incrementMissionProgress(uid, missionId, result.enemiesDefeated)
                }
            }
        }
        if (result.cratesBroken > 0) {
            listOf("daily_break_crates_t1", "daily_break_crates_t2", "weekly_treasure_seeker_t1").forEach { missionId ->
                step("mission:$missionId") {
                    app.userRepository.incrementMissionProgress(uid, missionId, result.cratesBroken)
                }
            }
        }
        if (result.victory) {
            step("mission:daily_complete_stage_t1") {
                app.userRepository.incrementMissionProgress(uid, "daily_complete_stage_t1", 1)
            }
        }
        if (result.bossName != null) {
            listOf("weekly_boss_hunter_t1", "weekly_boss_hunter_t2").forEach { missionId ->
                step("mission:$missionId") {
                    app.userRepository.incrementMissionProgress(uid, missionId, 1)
                }
            }
        }
        if (result.bonusCharacterDrops > 0) {
            step("bonusCharacterUnlock") {
                val states = app.userRepository.getCharacterStates(uid)
                val locked = app.characterRepository.getAllCached()
                    .filter { states[it.comicVineId]?.unlocked != true }
                if (locked.isNotEmpty()) {
                    val chosen = locked.random()
                    app.userRepository.unlockCharacter(uid, chosen.comicVineId)
                }
            }
        }

        return coinsError
    }

    private fun dismissLoadingOverlay() {
        if (loadingDismissed) return
        loadingDismissed = true
        binding.loadingOverlay.animate()
            .alpha(0f)
            .setDuration(420L)
            .withEndAction { binding.loadingOverlay.visibleIf(false) }
            .start()
    }

    private var minibossFadeOutRunnable: Runnable? = null

    private fun flashMinibossDefeated(name: String) {
        minibossFadeOutRunnable?.let { hudHandler.removeCallbacks(it) }
        binding.minibossFlashOverlay.animate().cancel()
        binding.minibossFlashOverlay.alpha = 0f
        binding.minibossFlashOverlay.visibleIf(true)
        binding.minibossFlashOverlay.animate()
            .alpha(1f)
            .setDuration(90L)
            .withEndAction {
                val fadeOut = Runnable {
                    binding.minibossFlashOverlay.animate()
                        .alpha(0f)
                        .setDuration(260L)
                        .withEndAction { binding.minibossFlashOverlay.visibleIf(false) }
                        .start()
                }
                minibossFadeOutRunnable = fadeOut
                hudHandler.postDelayed(fadeOut, 140L)
            }
            .start()
    }

    private fun exitBattle() {
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
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
        private const val TAG = "BattleActivity"
        private const val EXTRA_TEAM_IDS = "extra_team_ids"

        private const val MAX_BOSS_SEARCH_BATCHES = 2

        private val FAMOUS_HERO_BLOCKLIST = listOf(
            "spider-man", "homem-aranha", "homem aranha",
            "captain america", "capitao america", "capitão américa",
            "wolverine",
            "iron man", "homem de ferro",
            "deadpool"
        )
        fun newIntent(context: Context, teamIds: IntArray): Intent =
            Intent(context, BattleActivity::class.java).putExtra(EXTRA_TEAM_IDS, teamIds)
    }
}
