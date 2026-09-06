package com.app.hero_nexus.ui.battle

import com.app.hero_nexus.data.model.BattleResult
import com.app.hero_nexus.data.model.ChestType
import com.app.hero_nexus.data.model.Character
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

enum class BattleState { RUNNING, VICTORY, DEFEAT }

interface BattleListener {
    fun onCharacterDefeated(name: String)
    fun onNextCharacterEnters(name: String)
    fun onBossIncoming(name: String)
    fun onBossDefeated(name: String)
    /** Miniboss (capanga nomeado, mais forte) derrotado no meio de uma leva -- dispara um flash
     * de tela preta rápido (seção "transição nostálgica", feedback 01/09: "sabe em golden axe?
     * vc mata um vilão, aparece uma tela preta mas ainda continua na batalha"), mas a luta NÃO
     * termina, diferente de onBossDefeated. */
    fun onMinibossDefeated(name: String)
    fun onCratesOrCoins() // sinaliza para o HUD atualizar contadores
    /** Comida quebrada de uma caixa/barril -- agora só GUARDA no slot (não cura na hora, seção
     * "loot v2": "não necessariamente eu peguei comida que eu quero comer na hora"). */
    fun onFoodPickup(amount: Int)
    /** O jogador decidiu comer o que tava guardado no slot -- aí sim cura. */
    fun onFoodConsumed(amount: Int)
    /** O drop raríssimo (0,2%) -- personagem bônus, desbloqueado de verdade no fim da batalha. */
    fun onBonusCharacterPickup()
    /** Trecho da arena limpo (todos os inimigos daquela leva derrotados) -- mostra a seta de avançar
     * (mas não bloqueia o jogador de já seguir andando, seção "fluxo contínuo"). */
    fun onWaveCleared(wave: Int, totalWaves: Int)
    /** Jogador cruzou pro próximo trecho -- some a seta, mostra um banner de progresso. */
    fun onWaveAdvanced(wave: Int, totalWaves: Int)
    /** Especial usado -- pra tocar um efeito/banner na tela. */
    fun onSpecialUsed()
    fun onBattleEnded(result: BattleResult)
}

/**
 * Lógica pura da batalha (sem depender de Android View), estilo beat 'em up (seção 12):
 * o jogador controla um personagem por vez, inimigos avançam, objetos do cenário podem ser
 * quebrados, e quando o personagem atual morre o próximo do time entra (seção 13).
 *
 * Seção "fluxo contínuo" (feedback do usuário 31/08 -- "igual Golden Axe e Streets of Rage, você
 * anda e a câmera segue, não é pra spawnar de novo na tela só que em outra seção"): a arena inteira
 * agora é um MUNDO largo e contínuo (`worldWidth`), maior que a tela. `x` de todo mundo (jogador,
 * inimigos, caixas) é coordenada de MUNDO. `cameraX` é a janela de visão que acompanha o jogador;
 * a BattleView é quem subtrai `cameraX` na hora de desenhar pra saber a posição na TELA. Trocar de
 * personagem (time acabou de vida) não teleporta pra outro lugar -- o próximo entra bem onde o
 * anterior caiu, o fluxo nunca "recomeça" visualmente.
 */
class BattleEngine(
    private val team: List<Character>,
    private val boss: Character?,
    private val listener: BattleListener
) {
    var arenaWidth: Float = 1000f
    var arenaHeight: Float = 600f

    private var teamIndex = 0
    lateinit var player: PlayerFighter
        private set

    val enemies = mutableListOf<EnemyFighter>()
    val destructibles = mutableListOf<DestructibleObject>()

    var state: BattleState = BattleState.RUNNING
        private set

    /** false até start() rodar (a BattleView só chama start() depois de conhecer o tamanho da tela). */
    var started: Boolean = false
        private set

    var enemiesDefeated = 0
        private set
    var cratesBroken = 0
        private set
    var coinsCollected = 0
        private set
    var xpCollected = 0
        private set
    var bonusCharacterDrops = 0
        private set

    private val lootRandom = Random(System.nanoTime())

    private var bossSpawned = false
    private var bossDefeated = false
    private var minibossIndex = 0
    private var lastSpawnAtMs = 0L
    private var spawnedInWave = 0

    /** Trecho atual da arena (1-based). */
    var currentWave = 1
        private set
    val totalWaves = TOTAL_WAVES

    /** Largura total do "mundo" da batalha -- um trecho por leva + 1 extra reservado pro chefe. */
    var worldWidth: Float = 0f
        private set

    /** Janela de visão da câmera (canto esquerdo, em coordenada de mundo) -- acompanha o jogador
     * dentro dos limites do mundo, como em Golden Axe/Streets of Rage. */
    var cameraX: Float = 0f
        private set

    /** Variante de cenário desta batalha (0=castelo, 1=templo/deserto, 2=floresta/vila) --
     * sorteada uma vez por vilão (fixa dentro da mesma luta), pra dar variedade entre batalhas
     * sem perder a consistência dentro de uma mesma luta (feedback 31/08: "só existe um fundo
     * de batalha"). */
    var sceneryVariant: Int = 0
        private set

    /** Cosmético só: aparece um tempinho depois da leva limpa pra indicar o caminho, mas NÃO
     * trava o jogador -- ele já pode ter cruzado a marca de avanço antes disso (feedback 31/08:
     * "a seta esperar 5 segundos, o usuário pode ir antes, igual Street of Rage"). */
    var showAdvanceHint: Boolean = false
        private set
    private var waveClearedAtMs = 0L

    // Input do jogador (joystick virtual + botões), atualizado pela BattleView.
    var moveX: Float = 0f
    var moveY: Float = 0f
    var attackRequested: Boolean = false

    /** Pulso de 1 tick -- setado true num toque de botão, consumido (e voltado a false) no
     * próximo update(), pra não repetir enquanto o dedo fica em cima do botão. */
    var specialRequested: Boolean = false
    var useFoodRequested: Boolean = false

    /** Instante (ms) do último especial usado -- a BattleView usa isso pra desenhar o efeito
     * visual (anel se expandindo) por um tempinho depois do golpe. */
    var specialEffectAtMs: Long = 0L
        private set

    private var lastKnownPlayerX: Float = 0f

    fun start(nowMs: Long) {
        worldWidth = arenaWidth * (totalWaves + 1)
        sceneryVariant = abs(boss?.id ?: 0) % SCENERY_VARIANT_COUNT
        spawnPlayer()
        spawnCratesForSection(currentWave)
        lastSpawnAtMs = nowMs
        started = true
    }

    /** Início (coordenada de mundo) do trecho N -- cada trecho tem exatamente `arenaWidth`. */
    private fun sectionStart(section: Int): Float = (section - 1) * arenaWidth

    /** Topo da faixa andável (chão) -- alinhado com a linha do horizonte que a BattleView desenha
     * (mesma proporção `HORIZON_Y_RATIO`, pra não ter dois números mágicos desencontrados em dois
     * arquivos). O jogador só anda na faixa de chão, nunca no céu/skyline lá em cima. */
    private fun floorTopY(): Float = arenaHeight * HORIZON_Y_RATIO + FLOOR_TOP_MARGIN

    private fun floorBottomY(): Float = arenaHeight - PLAYER_MARGIN_BOTTOM

    private fun floorMidY(): Float = (floorTopY() + floorBottomY()) / 2f

    private fun randomFloorY(rnd: Random): Float {
        val top = floorTopY()
        val bottom = floorBottomY()
        return top + rnd.nextFloat() * (bottom - top).coerceAtLeast(1f)
    }

    private fun spawnPlayer() {
        // Primeiro personagem da luta entra no início do trecho 1. Troca de personagem (o anterior
        // morreu, seção 13) NÃO reseta a posição -- o próximo entra exatamente onde o time estava,
        // pra manter o fluxo contínuo (não "recomeça" o cenário).
        val startX = if (!started) sectionStart(1) + arenaWidth * 0.15f else lastKnownPlayerX
        player = PlayerFighter(team[teamIndex], startX, floorMidY())
        lastKnownPlayerX = startX
    }

    private fun spawnCratesForSection(section: Int) {
        val rnd = Random(System.currentTimeMillis())
        val base = sectionStart(section)
        repeat(6) { i ->
            val kind = if (i % 2 == 0) DestructibleObject.Kind.CAIXA else DestructibleObject.Kind.BARRIL
            val x = base + arenaWidth * (0.35f + rnd.nextFloat() * 0.55f)
            val y = randomFloorY(rnd)
            destructibles += DestructibleObject(x, y, kind)
        }
    }

    fun update(nowMs: Long, deltaSeconds: Float) {
        if (state != BattleState.RUNNING) return

        movePlayer(deltaSeconds)
        updateCamera()
        handlePlayerAttack(nowMs)
        handleSpecialAttack(nowMs)
        handleFoodUse()
        updateEnemies(nowMs, deltaSeconds)
        updateWaveFlow(nowMs)
        checkPlayerDeath(nowMs)
    }

    private fun updateCamera() {
        val maxCamera = (worldWidth - arenaWidth).coerceAtLeast(0f)
        cameraX = (player.x - arenaWidth / 2f).coerceIn(0f, maxCamera)
    }

    /** Spawna a leva atual, e uma vez limpa, libera a passagem pro próximo trecho -- sem travar
     * o jogador esperando (seção "fluxo contínuo", feedback 31/08). A seta (`showAdvanceHint`) é
     * só um indicativo visual que aparece um pouco depois; o cruzamento de fato já é permitido
     * desde o instante em que a leva foi limpa. */
    private fun updateWaveFlow(nowMs: Long) {
        if (bossSpawned) return

        if (spawnedInWave < ENEMIES_PER_WAVE) {
            val aliveCount = enemies.count { it.isAlive }
            if (aliveCount < MAX_CONCURRENT_ENEMIES && nowMs - lastSpawnAtMs > SPAWN_INTERVAL_MS) {
                spawnRegularEnemy()
                lastSpawnAtMs = nowMs
            }
            return
        }

        if (enemies.none { it.isAlive }) {
            if (waveClearedAtMs == 0L) {
                waveClearedAtMs = nowMs
                listener.onWaveCleared(currentWave, totalWaves)
            }
            if (!showAdvanceHint && nowMs - waveClearedAtMs >= ADVANCE_HINT_DELAY_MS) {
                showAdvanceHint = true
            }
            val triggerX = sectionStart(currentWave) + arenaWidth * ADVANCE_TRIGGER_X_RATIO
            if (player.x >= triggerX) {
                advanceWave()
            }
        }
    }

    private fun advanceWave() {
        showAdvanceHint = false
        waveClearedAtMs = 0L
        if (currentWave >= totalWaves) {
            if (boss != null) {
                spawnBoss()
            } else {
                // Sem vilão pra desafiar (edge case raro) -- não trava o jogador esperando pra sempre.
                endBattle(victory = true)
            }
        } else {
            currentWave++
            spawnedInWave = 0
            spawnCratesForSection(currentWave)
            listener.onWaveAdvanced(currentWave, totalWaves)
        }
    }

    private fun movePlayer(deltaSeconds: Float) {
        val speed = player.moveSpeed * 60f * deltaSeconds
        if (moveX != 0f || moveY != 0f) {
            player.facingRight = moveX >= 0f
        }
        // Limite agora é o mundo inteiro (não mais a tela) -- o jogador pode andar por todos os
        // trechos já liberados, a câmera que acompanha.
        player.x = (player.x + moveX * speed).coerceIn(PLAYER_MARGIN_X, worldWidth - PLAYER_MARGIN_X)
        // Só anda na faixa de chão -- não entra no céu/skyline lá em cima.
        player.y = (player.y + moveY * speed).coerceIn(floorTopY(), floorBottomY())
        lastKnownPlayerX = player.x
    }

    private fun handlePlayerAttack(nowMs: Long) {
        if (!attackRequested) return
        if (!player.canAttack(nowMs)) return
        player.lastAttackAtMs = nowMs

        // Golpe em área curta na frente do jogador: acerta até 2 inimigos e caixas próximas.
        val targets = enemies.filter { it.isAlive && player.distanceTo(it) <= player.attackRange }
            .sortedBy { player.distanceTo(it) }
            .take(2)
        targets.forEach { enemy ->
            enemy.takeDamage(player.attackDamage)
            // Barrinha de especial carrega conforme o dano causado (feedback 31/08: "um botão de
            // especial, que tem que ter uma barrinha que carrega conforme o dano").
            player.addSpecialCharge(player.attackDamage * SPECIAL_CHARGE_PER_DAMAGE)
            if (!enemy.isAlive) onEnemyDefeated(enemy)
        }

        destructibles.filter { !it.broken && distance(player.x, player.y, it.x, it.y) <= player.attackRange }
            .forEach { crate ->
                val wasBroken = crate.broken
                crate.hit(player.attackDamage, lootRandom, nowMs)
                if (crate.broken && !wasBroken) {
                    cratesBroken++
                    when (crate.rewardKind) {
                        DestructibleObject.RewardKind.COIN -> {
                            coinsCollected += crate.rewardCoins
                            listener.onCratesOrCoins()
                        }
                        DestructibleObject.RewardKind.FOOD -> {
                            // Guarda no slot em vez de curar na hora (feedback 31/08: "não
                            // necessariamente eu peguei comida que eu quero comer na hora,
                            // deveria ter slot"). Rodada 13: slot virou pilha de 3 (feedback
                            // 01/09) -- se já tava cheio, essa vira moedas bônus em vez de se
                            // perder.
                            if (player.storeFood()) {
                                listener.onFoodPickup(FOOD_HEAL_AMOUNT)
                            } else {
                                coinsCollected += crate.rewardCoins
                                listener.onCratesOrCoins()
                            }
                        }
                        DestructibleObject.RewardKind.CHARACTER -> {
                            bonusCharacterDrops++
                            listener.onBonusCharacterPickup()
                        }
                    }
                }
            }
    }

    /** Especial (seção "especial", feedback 31/08): consome 1 carga cheia e acerta em área todos
     * os inimigos perto do jogador com dano multiplicado. Híbrido Golden Axe/Streets of Rage --
     * carrega com dano (Golden Axe) mas guarda até 2 usos prontos pra disparar na hora (Streets
     * of Rage), decidido aqui porque combina melhor com o ritmo de "levas" da arena. */
    private fun handleSpecialAttack(nowMs: Long) {
        if (!specialRequested) return
        specialRequested = false
        if (!player.consumeSpecialCharge()) return

        val targets = enemies.filter { it.isAlive && player.distanceTo(it) <= SPECIAL_RANGE }
        targets.forEach { enemy ->
            enemy.takeDamage(player.attackDamage * SPECIAL_DAMAGE_MULTIPLIER)
            if (!enemy.isAlive) onEnemyDefeated(enemy)
        }
        specialEffectAtMs = nowMs
        listener.onSpecialUsed()
    }

    /** Come o que tá guardado no slot, se houver (o jogador decide a hora, seção "loot v2"). */
    private fun handleFoodUse() {
        if (!useFoodRequested) return
        useFoodRequested = false
        val amount = player.consumeFood(FOOD_HEAL_AMOUNT)
        if (amount > 0) listener.onFoodConsumed(amount)
    }

    private fun updateEnemies(nowMs: Long, deltaSeconds: Float) {
        enemies.filter { it.isAlive }.forEach { enemy ->
            val dx = player.x - enemy.x
            val dy = player.y - enemy.y
            val dist = distance(enemy.x, enemy.y, player.x, player.y)
            if (dist > enemy.attackRange * 0.8f) {
                val speed = enemy.moveSpeed * 60f * deltaSeconds
                val nx = dx / max(dist, 0.001f)
                val ny = dy / max(dist, 0.001f)
                enemy.x += nx * speed
                enemy.y = (enemy.y + ny * speed).coerceIn(floorTopY(), floorBottomY())
                enemy.facingRight = dx >= 0f
            } else if (enemy.canAttack(nowMs)) {
                enemy.lastAttackAtMs = nowMs
                player.takeDamage(enemy.attackDamage)
            }
        }
    }

    private fun onEnemyDefeated(enemy: EnemyFighter) {
        enemiesDefeated++
        xpCollected += enemy.rewardXp
        coinsCollected += enemy.rewardCoins
        when {
            enemy.isBoss -> {
                bossDefeated = true
                listener.onBossDefeated(enemy.displayName)
                listener.onCratesOrCoins()
                endBattle(victory = true)
            }
            enemy.isMiniboss -> {
                listener.onCratesOrCoins()
                // Só o flash -- a luta segue rolando por baixo, ninguém trava esperando (mesma
                // filosofia de "fluxo contínuo" do resto da arena).
                listener.onMinibossDefeated(enemy.displayName)
            }
            else -> listener.onCratesOrCoins()
        }
    }

    private fun spawnRegularEnemy() {
        spawnedInWave++
        val rnd = Random(System.nanoTime())
        val y = randomFloorY(rnd)
        val x = sectionStart(currentWave) + arenaWidth - 20f
        // Rodada 13 (feedback 01/09, referência Golden Axe): o último capanga de certas levas
        // vira um MINIBOSS nomeado -- mais forte, dá tela preta rápida ao cair, mas não acaba a
        // luta. Só 2 minibosses numa batalha de 6 levas -- desafio extra sem virar rotina.
        if (currentWave in MINIBOSS_WAVES && spawnedInWave == ENEMIES_PER_WAVE) {
            spawnMiniboss(x, y)
        } else {
            enemies += EnemyFighter("Capanga", x, y)
        }
    }

    private fun spawnMiniboss(x: Float, y: Float) {
        val avgPlayerLevel = team.map { it.level }.average().takeIf { !it.isNaN() }?.toInt() ?: 1
        // Correção pós-validação rodada 13: como cada batalha só spawna 2 minibosses (WAVES 2 e
        // 4) e uma BattleEngine nova nasce a cada luta, um índice começando sempre em 0 nunca
        // alcançava o 3º nome do banco (sempre [0] depois [1], eternamente). Desloca o ponto de
        // partida pelo id do vilão da luta (mesma ideia do sceneryVariant logo abaixo), então
        // batalhas diferentes começam em nomes diferentes e o banco todo circula com o tempo.
        val offset = abs(boss?.id ?: 0)
        val name = MINIBOSS_NAMES[(offset + minibossIndex) % MINIBOSS_NAMES.size]
        minibossIndex++
        enemies += EnemyFighter(
            displayName = name,
            startX = x,
            startY = y,
            isMiniboss = true,
            playerLevel = avgPlayerLevel.coerceAtLeast(1)
        )
    }

    private fun spawnBoss() {
        bossSpawned = true
        val bossChar = boss ?: return
        listener.onBossIncoming(bossChar.name)
        // Nível médio do time no momento da luta -- usado pra escalar o chefe (seção
        // "balanceamento": revanches contra o mesmo vilão não podem ficar fáceis pra sempre).
        val avgPlayerLevel = team.map { it.level }.average().takeIf { !it.isNaN() }?.toInt() ?: 1
        val startX = sectionStart(totalWaves + 1) + arenaWidth * 0.65f
        enemies += EnemyFighter(
            displayName = bossChar.name,
            startX = startX,
            startY = floorMidY(),
            isBoss = true,
            bossCharacterId = bossChar.id,
            bossPower = bossChar.stats.overallPower,
            playerLevel = avgPlayerLevel.coerceAtLeast(1)
        )
    }

    private fun checkPlayerDeath(nowMs: Long) {
        if (player.isAlive) return
        listener.onCharacterDefeated(player.character.name)
        if (teamIndex < team.size - 1) {
            teamIndex++
            spawnPlayer()
            listener.onNextCharacterEnters(player.character.name)
        } else {
            endBattle(victory = false)
        }
    }

    private fun endBattle(victory: Boolean) {
        state = if (victory) BattleState.VICTORY else BattleState.DEFEAT
        val result = BattleResult(
            victory = victory,
            enemiesDefeated = enemiesDefeated,
            cratesBroken = cratesBroken,
            bossName = if (bossDefeated) boss?.name else null,
            bossCharacterId = if (bossDefeated) boss?.id else null,
            xpGained = xpCollected,
            coinsGained = coinsCollected,
            chestAwarded = if (victory) (if (bossDefeated) ChestType.ESPECIAL else ChestType.HEROI) else null,
            bonusCharacterDrops = bonusCharacterDrops
        )
        listener.onBattleEnded(result)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    companion object {
        // Balanceamento rodada 13 (feedback 01/09: "só um vilão e 15 capangas nao desafia
        // tanto... acho que deveria ter mais luta"): 30 capangas no total, 6 levas de 5, spawn
        // um pouco mais rápido -- luta mais longa e mais gente na tela ao mesmo tempo.
        private const val TOTAL_WAVES = 6
        private const val ENEMIES_PER_WAVE = 5
        private const val MAX_CONCURRENT_ENEMIES = 5
        private const val SPAWN_INTERVAL_MS = 1700L

        // Cura fixa por carga de comida consumida (o slot virou pilha de 3, seção "loot v2").
        const val FOOD_HEAL_AMOUNT = 22

        // Minibosses (seção "transição nostálgica", feedback 01/09): em quais levas o último
        // capanga vira um miniboss nomeado, e o banco de nomes usado (cíclico).
        private val MINIBOSS_WAVES = setOf(2, 4)
        private val MINIBOSS_NAMES = listOf("Chefe da Gangue", "Brutamontes Blindado", "Capanga de Elite")
        // Fração do trecho atual que o jogador precisa alcançar, com a leva limpa, pra liberar o
        // próximo -- pode cruzar isso ANTES da seta aparecer (seção "fluxo contínuo").
        private const val ADVANCE_TRIGGER_X_RATIO = 0.86f
        // Só cosmético: quanto tempo depois da leva limpa a seta aparece (não bloqueia mais nada,
        // feedback 31/08: "a seta esperar 5 segundos, o usuário pode ir antes").
        private const val ADVANCE_HINT_DELAY_MS = 1400L

        // Especial: quanto a barra carrega por ponto de dano causado, e o quanto multiplica o
        // dano normal quando disparado em área.
        private const val SPECIAL_CHARGE_PER_DAMAGE = 1f / 80f
        private const val SPECIAL_RANGE = 240f
        private const val SPECIAL_DAMAGE_MULTIPLIER = 3

        // Quantas variantes de cenário existem pra sortear por vilão.
        const val SCENERY_VARIANT_COUNT = 3

        // Linha do horizonte como fração da altura da arena -- ÚNICA fonte da verdade,
        // compartilhada com a BattleView (que usa o mesmo valor pra desenhar o cenário).
        const val HORIZON_Y_RATIO = 0.32f
        private const val FLOOR_TOP_MARGIN = 26f
        private const val PLAYER_MARGIN_BOTTOM = 40f
        private const val PLAYER_MARGIN_X = 40f
    }
}
