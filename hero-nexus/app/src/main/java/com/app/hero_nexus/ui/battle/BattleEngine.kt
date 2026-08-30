package com.app.hero_nexus.ui.battle

import com.app.hero_nexus.data.model.BattleResult
import com.app.hero_nexus.data.model.ChestType
import com.app.hero_nexus.data.model.Character
import kotlin.math.max
import kotlin.random.Random

enum class BattleState { RUNNING, VICTORY, DEFEAT }

interface BattleListener {
    fun onCharacterDefeated(name: String)
    fun onNextCharacterEnters(name: String)
    fun onBossIncoming(name: String)
    fun onBossDefeated(name: String)
    fun onCratesOrCoins() // sinaliza para o HUD atualizar contadores
    fun onBattleEnded(result: BattleResult)
}

/**
 * Lógica pura da batalha (sem depender de Android View), estilo beat 'em up (seção 12):
 * o jogador controla um personagem por vez, inimigos avançam, objetos do cenário podem ser
 * quebrados, e quando o personagem atual morre o próximo do time entra (seção 13).
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

    private var bossSpawned = false
    private var bossDefeated = false
    private var lastSpawnAtMs = 0L
    private var spawnedRegularCount = 0

    // Input do jogador (joystick virtual + botão de ataque), atualizado pela BattleView.
    var moveX: Float = 0f
    var moveY: Float = 0f
    var attackRequested: Boolean = false

    fun start(nowMs: Long) {
        spawnPlayer()
        spawnCrates()
        lastSpawnAtMs = nowMs
        started = true
    }

    private fun spawnPlayer() {
        player = PlayerFighter(team[teamIndex], arenaWidth * 0.2f, arenaHeight * 0.5f)
    }

    private fun spawnCrates() {
        destructibles.clear()
        val rnd = Random(System.currentTimeMillis())
        repeat(6) { i ->
            val kind = if (i % 2 == 0) DestructibleObject.Kind.CAIXA else DestructibleObject.Kind.BARRIL
            val x = arenaWidth * (0.35f + rnd.nextFloat() * 0.55f)
            val y = arenaHeight * (0.2f + rnd.nextFloat() * 0.6f)
            destructibles += DestructibleObject(x, y, kind)
        }
    }

    fun update(nowMs: Long, deltaSeconds: Float) {
        if (state != BattleState.RUNNING) return

        movePlayer(deltaSeconds)
        handlePlayerAttack(nowMs)
        updateEnemies(nowMs, deltaSeconds)
        trySpawnWave(nowMs)
        checkPlayerDeath(nowMs)
    }

    private fun movePlayer(deltaSeconds: Float) {
        val speed = player.moveSpeed * 60f * deltaSeconds
        if (moveX != 0f || moveY != 0f) {
            player.facingRight = moveX >= 0f
        }
        player.x = (player.x + moveX * speed).coerceIn(40f, arenaWidth - 40f)
        player.y = (player.y + moveY * speed).coerceIn(80f, arenaHeight - 40f)
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
            if (!enemy.isAlive) onEnemyDefeated(enemy)
        }

        destructibles.filter { !it.broken && distance(player.x, player.y, it.x, it.y) <= player.attackRange }
            .forEach { crate ->
                crate.hit(player.attackDamage)
                if (crate.broken) {
                    cratesBroken++
                    coinsCollected += crate.rewardCoins
                    listener.onCratesOrCoins()
                }
            }
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
                enemy.y += ny * speed
                enemy.facingRight = dx >= 0f
            } else if (enemy.canAttack(nowMs)) {
                enemy.lastAttackAtMs = nowMs
                player.takeDamage(enemy.attackDamage)
            }
        }
    }

    private fun onEnemyDefeated(enemy: EnemyFighter) {
        if (enemy.isBoss) {
            bossDefeated = true
            enemiesDefeated++
            xpCollected += enemy.rewardXp
            coinsCollected += enemy.rewardCoins
            listener.onBossDefeated(enemy.displayName)
            listener.onCratesOrCoins()
            endBattle(victory = true)
        } else {
            enemiesDefeated++
            xpCollected += enemy.rewardXp
            coinsCollected += enemy.rewardCoins
            listener.onCratesOrCoins()
        }
    }

    private fun trySpawnWave(nowMs: Long) {
        if (bossSpawned) return

        if (spawnedRegularCount < TOTAL_REGULAR_ENEMIES) {
            val aliveCount = enemies.count { it.isAlive }
            if (aliveCount < MAX_CONCURRENT_ENEMIES && nowMs - lastSpawnAtMs > SPAWN_INTERVAL_MS) {
                spawnRegularEnemy()
                lastSpawnAtMs = nowMs
            }
        } else if (enemies.none { it.isAlive } && boss != null) {
            spawnBoss()
        }
    }

    private fun spawnRegularEnemy() {
        spawnedRegularCount++
        val rnd = Random(System.nanoTime())
        val y = arenaHeight * (0.25f + rnd.nextFloat() * 0.5f)
        enemies += EnemyFighter("Capanga", arenaWidth - 20f, y)
    }

    private fun spawnBoss() {
        bossSpawned = true
        val bossChar = boss ?: return
        listener.onBossIncoming(bossChar.name)
        enemies += EnemyFighter(
            displayName = bossChar.name,
            startX = arenaWidth - 40f,
            startY = arenaHeight * 0.5f,
            isBoss = true,
            bossCharacterId = bossChar.id
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
            chestAwarded = if (victory) (if (bossDefeated) ChestType.ESPECIAL else ChestType.HEROI) else null
        )
        listener.onBattleEnded(result)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    companion object {
        private const val TOTAL_REGULAR_ENEMIES = 10
        private const val MAX_CONCURRENT_ENEMIES = 3
        private const val SPAWN_INTERVAL_MS = 2200L
    }
}
