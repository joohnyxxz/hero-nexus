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
        findNextVillainToUnlock(states)?.let { return it }
        findNextHeroToUnlock(states)?.let { return it }

        // Rodada 15, parte 42 (04/10/2026): antes, se NENHUM vilao do cache atual estivesse
        // elegivel (todos ja desbloqueados), o jogo desistia direto e caia pra revanche ciclica
        // -- mesmo que o catalogo de verdade da Comic Vine (bem maior que o que ja foi baixado)
        // ainda tivesse viloes Marvel nunca vistos. Pedido explicito do usuario: "nao deveria de
        // parar de carregar novos viloes pq desbloqueou todos do seu cache, nao tem logica...
        // so a parte da listagem que e por cache". Ou seja: o CACHE e so o que ja foi baixado
        // até agora (correto ele ser limitado -- e assim que a Colecao evita puxar o catalogo
        // inteiro de uma vez), mas a escolha do chefao nao devia desistir so porque esgotou o
        // que calhou de ja estar baixado -- tenta buscar mais algumas levas da Comic Vine
        // (mesmo cursor de paginacao usado pelo "carregar mais" da Colecao, `loadMore()`) antes
        // de cair pra revanche. Limitado a MAX_BOSS_SEARCH_BATCHES tentativas -- sem limite,
        // isso podia deixar TODA batalha seguinte (a partir do momento em que acabam os vilaos
        // novos pra desbloquear) com uma tela de carregamento bem mais longa pra sempre, ja que
        // boa parte do catalogo global da Comic Vine nao e Marvel nem vilao. Ressalva honesta:
        // mesmo com o limite, isso significa que QUALQUER batalha, a partir desse ponto, paga
        // esse custo extra de rede antes de cair pra revanche -- vale sentir ao vivo se o
        // carregamento ficou perceptivelmente mais lento nessa fase do jogo.
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

        // Catalogo de fato esgotado (ou as tentativas acima nao acharam ninguem novo): as
        // revanches seguem uma ordem fixa, avancando com o nivel medio do time (em vez de
        // sorteio) -- assim o mesmo vilao nunca fica "preso" como oponente facil pra sempre.
        val avgTeamLevel = teamIds.map { states[it]?.level ?: 1 }.average().takeIf { !it.isNaN() } ?: 1.0
        val idx = (avgTeamLevel.toInt() - 1).coerceAtLeast(0) % villains.size
        return villains[idx]
    }

    /** Acha o proximo vilao a desafiar dentro do que JA esta em cache agora -- separado de
     * [pickBoss] pra poder ser chamado de novo depois de cada tentativa de buscar mais vilaos
     * (ver loop em pickBoss()), sem duplicar a logica de prioridade (Carnificina/proximo mais
     * fraco). Ordem de dificuldade fixa (nao aleatoria): ordena os vilaos pelo poder geral
     * (Comic Vine) e sempre desafia o proximo ainda nao desbloqueado, do mais fraco pro mais
     * forte -- exceto a prioridade explicita do Carnificina, ver comentario abaixo. */
    private suspend fun findNextVillainToUnlock(states: Map<Int, UserCharacterState>): Character? {
        val villains = app.characterRepository.getAllCached()
            .filter { it.category == CharacterCategory.VILAO.name }
            .map { it.toDomain(states[it.comicVineId] ?: UserCharacterState()) }
            .sortedBy { it.stats.overallPower }
        if (villains.isEmpty()) return null

        // Rodada 15, parte 41 (04/10/2026): pedido explícito do usuário -- Carnificina
        // (Carnage) como o "chefão" de referência, furando a fila normal de poder (comentário
        // acima) assim que ele estiver no catálogo (cache) e ainda não desbloqueado. É uma
        // exceção deliberada à regra "sempre o próximo mais fraco" só pra esse personagem --
        // como o poder dele na Comic Vine não é necessariamente baixo, isso pode deixar a
        // revanche bem mais dura do que a progressão normal entregaria nesse ponto. Avisar se
        // sentir um pico de dificuldade fora de hora.
        val carnagePriority = villains.firstOrNull {
            !it.unlocked &&
                (it.name.contains("carnage", ignoreCase = true) ||
                    it.name.contains("carnificina", ignoreCase = true))
        }
        if (carnagePriority != null) return carnagePriority

        return villains.firstOrNull { !it.unlocked }
    }

    /** Rodada 15, parte 63 (05/10/2026): pedido explicito do usuario -- quando o catalogo de
     * vilao (cache + paginas novas da Comic Vine, ver loop em pickBoss()) se esgota de vez,
     * o chefao parava de desbloquear gente nova pra sempre e virava revanche ciclica eterna
     * entre os mesmos vilaos (e como o Carnificina tem prioridade, era nele que quase sempre
     * "travava" -- ver relato do usuario). Agora, esgotados os vilaos, o proximo chefao pode
     * ser um HEROI ou ANTI_HEROI ainda nao desbloqueado -- de proposito um dos MENOS famosos
     * (ver FAMOUS_HERO_BLOCKLIST), sorteado aleatoriamente em vez de seguir ordem de poder,
     * senao ia sempre cair num dos principais (Homem-Aranha, Capitao America, Wolverine,
     * Homem de Ferro, Deadpool) que ja sao o elenco fixo/inicial do jogo. */
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
        // setOnTouchListener consome o evento direto -- o View nunca entra sozinho em
        // state_pressed (isso só acontece via performClick/onTouchEvent padrão), então o
        // selector do fundo (bg_attack_button) não acendia ao segurar. Setando isPressed na mão
        // aqui pra o hover realmente aparecer.
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

    // -------------------------------------------------------------------------- BattleListener

    // Rodada 15, parte 33 (03/10/2026): virou no-op -- pedido explícito do usuário pra tirar o
    // nome dos personagens da tela de batalha, incluindo os banners "fulano derrotado"/"fulano
    // entrou na batalha" (não precisa mais sinalizar troca de personagem por texto). Strings
    // battle_defeated_switch/battle_next_enters ficaram órfãs em strings.xml, igual outros
    // recursos não usados do projeto -- não removidas, só paradas de usar.
    override fun onCharacterDefeated(name: String) = Unit

    override fun onNextCharacterEnters(name: String) = Unit

    // Rodada 15, parte 34 (03/10/2026): todos os banners de texto abaixo viraram no-op --
    // pedido explícito do usuário, estendendo o que já tinha sido pedido pra
    // onCharacterDefeated/onNextCharacterEnters (parte 33): "quando falei pra tirar o fulano de
    // tal entra na batalha falei sobre as outras coisas tbm, como o de sinalizar comida, vilao
    // derrotado, nao conversa com os jogos que estamos se espelhando" -- Golden Axe/Streets of
    // Rage não interrompem a ação com frases na tela pra sinalizar esse tipo de evento. O HUD
    // persistente (nome do chefe, barra de vida dele, contadores) continua funcionando
    // normalmente -- só o texto transitório (`showBanner`) saiu. `onMinibossDefeated` não muda
    // (não usa texto, é só o flash de tela preta, já era assim desde a rodada 13). As strings
    // boss_incoming/boss_defeated/wave_cleared/wave_advanced/food_pickup/food_consumed/
    // bonus_character_pickup/special_used ficaram órfãs em strings.xml, igual outros recursos não
    // usados no projeto -- não removidas, só paradas de usar.
    // Rodada 15, parte 53 (04/10/2026): nome do chefe no header removido -- pedido explicito
    // do usuario ("na tela do vilao nao precisa colocar o nome dele no header, so pega espaco a
    // toa e fica feio"). `layoutBossHealth`/`textBossName` (layout XML) ficam orfaos, sem uso,
    // igual outros recursos ja removidos de rotacao no projeto -- continuam "gone" por padrao.
    override fun onBossIncoming(name: String) = Unit

    override fun onBossDefeated(name: String) = Unit

    /** Miniboss caiu -- flash de tela preta rápido, a luta segue por baixo (seção "transição
     * nostálgica", feedback 01/09: referência Golden Axe). */
    // Rodada 15, parte 54 (04/10/2026): removido o flash preto ao cair um miniboss -- pedido
    // explicito do usuario ("nao precisa dar um pisque na tela a cada vez que mata um
    // brutamontes, pelo contrario, deixa corrido"). flashMinibossDefeated() fica orfa, sem uso,
    // igual outros recursos ja removidos de rotacao no projeto.
    override fun onMinibossDefeated(name: String) = Unit

    override fun onCratesOrCoins() = Unit // HUD já é atualizado pelo polling

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

    // Rodada 15, parte 34 (03/10/2026): showBanner() removida -- ficou sem nenhum chamador depois
    // que todos os banners de texto foram desligados (ver comentário acima de onBossIncoming).
    // `binding.textBanner` (layout XML) continua existindo, só órfã, igual outros recursos não
    // usados no projeto.

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
     * ~490ms no total (entrada 90ms + segura só 140ms + saída 260ms).
     * Rodada 15, parte 35 (03/10/2026): parou de escrever o nome do miniboss em cima do flash
     * (`textMinibossDefeated`) -- passou batido na limpeza de banners da parte 34 (eu tinha
     * assumido, sem reler a função inteira, que era só o flash preto sem texto nenhum; o usuário
     * confirmou que ainda tinha sinalização sobrando na tela). Golden Axe só faz o flash preto
     * mesmo, sem nome escrito -- o `textMinibossDefeated` no layout ficou órfão (sem texto
     * setado, não aparece nada), igual outros recursos não usados do projeto. */
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
        // Rodada 15, parte 42 (04/10/2026): quantas levas extras (loadMore()) pickBoss() tenta
        // buscar da Comic Vine antes de desistir e cair pra revanche ciclica, quando nao ha
        // vilao novo no cache pra desbloquear -- ver comentario grande em pickBoss().
        private const val MAX_BOSS_SEARCH_BATCHES = 2
        // Rodada 15, parte 63 (05/10/2026): nomes a NAO sortear como "chefao surpresa" depois
        // que os viloes acabam -- esses ja sao o elenco de vitrine do jogo (tem sprite propria,
        // ja aparecem em destaque etc.), entao desbloquear eles por essa via ficaria redundante
        // e ainda ia contra o pedido do usuario de ser "gente aleatoria", nao os mais conhecidos.
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
