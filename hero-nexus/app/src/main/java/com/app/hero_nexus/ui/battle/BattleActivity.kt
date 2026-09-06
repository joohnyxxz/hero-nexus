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
        val all = app.characterRepository.getAllCached()
        // Ordem de dificuldade fixa (não aleatória): ordenamos os vilões pelo poder geral (Comic
        // Vine) e sempre desafiamos o próximo ainda não desbloqueado, do mais fraco pro mais forte.
        val villains = all.filter { it.category == CharacterCategory.VILAO.name }
            .map { it.toDomain(states[it.comicVineId] ?: UserCharacterState()) }
            .sortedBy { it.stats.overallPower }
        if (villains.isEmpty()) return null

        val nextToUnlock = villains.firstOrNull { !it.unlocked }
        if (nextToUnlock != null) return nextToUnlock

        // Todos os vilões já foram desbloqueados: as revanches também seguem uma ordem fixa,
        // avançando com o nível médio do time (em vez de sorteio) -- assim o mesmo vilão nunca
        // fica "preso" como oponente fácil pra sempre.
        val avgTeamLevel = teamIds.map { states[it]?.level ?: 1 }.average().takeIf { !it.isNaN() } ?: 1.0
        val idx = (avgTeamLevel.toInt() - 1).coerceAtLeast(0) % villains.size
        return villains[idx]
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
            // Primeira vez que o motor realmente começou a rodar: dispensa a tela preta de
            // carregamento com um fade suave em vez de sumir na hora (seção "transição nostálgica").
            dismissLoadingOverlay()
            if (engine.state == BattleState.RUNNING) {
                binding.progressPlayerHealth.progress = (engine.player.healthRatio * 100).toInt()
                binding.textPlayerName.text = engine.player.character.name
                binding.textCoins.text = engine.coinsCollected.toString()

                val bossEnemy = engine.enemies.firstOrNull { it.isBoss }
                binding.layoutBossHealth.visibleIf(bossEnemy != null)
                bossEnemy?.let {
                    binding.progressBossHealth.progress = (it.healthRatio * 100).toInt()
                }

                updateSpecialHud()
                updateFoodHud()
            }
            hudHandler.postDelayed(this, 120L)
        }
    }

    /** Barrinha do especial (a carga "corrente") + 2 pips indicando quantos usos já estão
     * prontos (seção "especial", feedback 31/08: "uma barrinha que carrega conforme o dano...
     * no máximo 2 especial"). */
    private fun updateSpecialHud() {
        val player = engine.player
        binding.progressSpecialCharge.progress = (player.currentChargeProgress * 100).toInt()
        binding.pipSpecial1.alpha = if (player.storedSpecials >= 1) 1f else 0.28f
        binding.pipSpecial2.alpha = if (player.storedSpecials >= 2) 1f else 0.28f
        binding.buttonSpecial.isEnabled = player.storedSpecials >= 1
        binding.buttonSpecial.alpha = if (player.storedSpecials >= 1) 1f else 0.55f
    }

    /** Botão de comida só aparece com algo guardado no slot (seção "loot v2", feedback 31/08:
     * "não necessariamente eu peguei comida que eu quero comer na hora, deveria ter slot").
     * Rodada 13 (feedback 01/09, "o slot para cura seria de 3 comidinhas"): agora é uma pilha
     * de até 3 -- o selo mostra quantas cargas tem guardada. */
    private fun updateFoodHud() {
        val charges = engine.player.foodCharges
        binding.buttonFood.visibleIf(charges > 0)
        binding.textFoodCount.visibleIf(charges > 0)
        binding.textFoodCount.text = charges.toString()
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

    // -------------------------------------------------------------------------- BattleListener

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

    /** Miniboss caiu -- flash de tela preta rápido, a luta segue por baixo (seção "transição
     * nostálgica", feedback 01/09: referência Golden Axe). */
    override fun onMinibossDefeated(name: String) {
        runOnUiThread { flashMinibossDefeated(name) }
    }

    override fun onCratesOrCoins() = Unit // HUD já é atualizado pelo polling

    override fun onWaveCleared(wave: Int, totalWaves: Int) {
        runOnUiThread { showBanner(getString(R.string.wave_cleared)) }
    }

    override fun onWaveAdvanced(wave: Int, totalWaves: Int) {
        runOnUiThread { showBanner(getString(R.string.wave_advanced, wave, totalWaves)) }
    }

    override fun onFoodPickup(amount: Int) {
        runOnUiThread { showBanner(getString(R.string.food_pickup, amount)) }
    }

    override fun onFoodConsumed(amount: Int) {
        runOnUiThread { showBanner(getString(R.string.food_consumed, amount)) }
    }

    override fun onBonusCharacterPickup() {
        runOnUiThread { showBanner(getString(R.string.bonus_character_pickup)) }
    }

    override fun onSpecialUsed() {
        runOnUiThread { showBanner(getString(R.string.special_used)) }
    }

    override fun onBattleEnded(result: BattleResult) {
        hudRunning = false
        runOnUiThread {
            lifecycleScope.launch {
                val coinsError = persistResult(result)
                // Rodada 10 (01/09): antes uma falha ao salvar moedas/XP só ia pro Logcat --
                // invisível sem acesso a adb. A tela de resultado seguinte mostra um valor
                // "otimista" calculado localmente, então sem esse aviso o jogador via "+X moedas"
                // e nunca entendia por que o saldo real não mudava. Agora avisa na hora.
                if (coinsError != null) {
                    Toast.makeText(
                        this@BattleActivity,
                        "Não consegui salvar suas moedas/XP no servidor ($coinsError). " +
                            "Verifique sua internet e as regras do Firestore -- tente de novo mais tarde.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                startActivity(BattleResultActivity.newIntent(this@BattleActivity, result, teamIds))
                // Transição nostálgica de tela escura em vez de corte seco (feedback 31/08:
                // "pra sair também" -- mesma ideia de fade usada ao entrar na batalha).
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            }
        }
    }

    /**
     * Salva os efeitos da batalha (moedas/XP, desbloqueio de vilão, baú, missões).
     *
     * IMPORTANTE: cada efeito roda no seu próprio try/catch (via [step]). Antes, tudo ficava
     * dentro de um único runCatching -- se a PRIMEIRA chamada (moedas/XP) falhasse por qualquer
     * motivo, TODAS as outras eram puladas silenciosamente. Agora um falha sem derrubar os outros.
     *
     * Rodada 10 (01/09): retorna a mensagem de erro do passo de moedas/XP (ou null se deu certo),
     * pra [onBattleEnded] poder avisar o jogador na hora -- antes essa falha só ia pro Logcat.
     */
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
            step("mission:kill_20_enemies") {
                app.userRepository.incrementMissionProgress(uid, "kill_20_enemies", result.enemiesDefeated)
            }
        }
        if (result.cratesBroken > 0) {
            step("mission:break_10_crates") {
                app.userRepository.incrementMissionProgress(uid, "break_10_crates", result.cratesBroken)
            }
        }
        if (result.victory) {
            step("mission:complete_1_stage") {
                app.userRepository.incrementMissionProgress(uid, "complete_1_stage", 1)
            }
        }
        if (result.bossName != null) {
            step("mission:defeat_1_boss") {
                app.userRepository.incrementMissionProgress(uid, "defeat_1_boss", 1)
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

    private fun showBanner(text: String) {
        binding.textBanner.text = text
        binding.textBanner.visibleIf(true)
        hudHandler.postDelayed({ binding.textBanner.visibleIf(false) }, 1800L)
    }

    /** Tela preta com um fade em degradê em vez do spinner+texto de antes (feedback 31/08:
     * "esse loading tá muito feio, acho que é mais nostálgico só ir pra tela preta com uma
     * transição, tipo de gradient"). O layout do overlay já é só um fundo em degradê (sem
     * ProgressBar/texto); aqui só cross-fadeia ele pra fora quando a arena já está pronta. */
    private fun dismissLoadingOverlay() {
        if (loadingDismissed) return
        loadingDismissed = true
        binding.loadingOverlay.animate()
            .alpha(0f)
            .setDuration(420L)
            .withEndAction { binding.loadingOverlay.visibleIf(false) }
            .start()
    }

    /** Runnable pendente do fade-out do flash de miniboss -- guardado à parte pra poder ser
     * cancelado de verdade (removeCallbacks) se um segundo miniboss cair enquanto o primeiro
     * flash ainda tá segurando (revisão pós-validação rodada 13: animate().cancel() sozinho não
     * cancelava esse postDelayed, só a ViewPropertyAnimator). */
    private var minibossFadeOutRunnable: Runnable? = null

    /** Flash de tela preta rápido ao cair um miniboss (feedback 01/09: "sabe em golden axe?
     * vc mata um vilão, aparece uma tela preta mas ainda continua na batalha"). Diferente do
     * loadingOverlay, esse não é clickable/focusable -- a luta continua rodando por baixo o
     * tempo inteiro. Revisão pós-validação rodada 13: a primeira versão segurava a tela preta
     * OPACA por quase 1s (90ms entrada + 480ms segurando) -- tempo demais pra um "flash" numa
     * luta que continua correndo por baixo, o jogador levava dano sem ver nada. Encurtado pra
     * ~490ms no total (entrada 90ms + segura só 140ms + saída 260ms), suficiente pra ler o nome
     * sem virar uma pausa cega. */
    private fun flashMinibossDefeated(name: String) {
        binding.textMinibossDefeated.text = getString(R.string.miniboss_defeated, name.uppercase())
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
        fun newIntent(context: Context, teamIds: IntArray): Intent =
            Intent(context, BattleActivity::class.java).putExtra(EXTRA_TEAM_IDS, teamIds)
    }
}
