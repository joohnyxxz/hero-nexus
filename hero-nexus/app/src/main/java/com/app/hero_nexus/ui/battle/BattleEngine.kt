package com.app.hero_nexus.ui.battle

import com.app.hero_nexus.data.model.BattleResult
import com.app.hero_nexus.data.model.ChestType
import com.app.hero_nexus.data.model.Character
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.random.Random

enum class BattleState { RUNNING, VICTORY, DEFEAT }

enum class DeathTransitionPhase { NONE, DYING, BLINKING, DROPPING_IN }

interface BattleListener {
    fun onCharacterDefeated(name: String)
    fun onNextCharacterEnters(name: String)
    fun onBossIncoming(name: String)
    fun onBossDefeated(name: String)

    fun onMinibossDefeated(name: String)
    fun onCratesOrCoins()

    fun onFoodPickup(amount: Int)

    fun onFoodConsumed(amount: Int)

    fun onBonusCharacterPickup()

    fun onWaveCleared(wave: Int, totalWaves: Int)

    fun onWaveAdvanced(wave: Int, totalWaves: Int)

    fun onSpecialUsed()
    fun onBattleEnded(result: BattleResult)
}

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

    data class SceneryProp(val x: Float, val y: Float, val kind: Int)
    val sceneryProps = mutableListOf<SceneryProp>()

    var state: BattleState = BattleState.RUNNING
        private set

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
    private var spawnedInWave = 0

    private var bossPending = false
    private var bossTriggerAtMs = 0L
    var bossIntroActive = false

    var playerLocked = false
        private set

    private var bossAwaitingProximity = false
    private var bossProximityThresholdX = 0f
    private var bossIntroStartAtMs = 0L
    private var bossIntroStartX = 0f
    private var bossTargetX = 0f

    var currentWave = 1
        private set
    val totalWaves = TOTAL_WAVES

    var worldWidth: Float = 0f
        private set

    var cameraX: Float = 0f
        private set

    private var sceneryVariant: Int = 0

    var showAdvanceHint: Boolean = false
        private set
    private var waveClearedAtMs = 0L

    var moveX: Float = 0f
    var moveY: Float = 0f
    var attackRequested: Boolean = false

    private var lastMovedAtMs: Long = 0L

    var specialRequested: Boolean = false
    var useFoodRequested: Boolean = false

    var specialEffectAtMs: Long = 0L
        private set

    var playerHurtAtMs: Long = 0L
        private set

    var deathTransitionPhase: DeathTransitionPhase = DeathTransitionPhase.NONE
        private set
    val deathTransitionActive: Boolean get() = deathTransitionPhase != DeathTransitionPhase.NONE
    var deathPhaseStartAtMs: Long = 0L
        private set

    var dropInProgress: Float = 0f
        private set
    private var deathHasNextCharacter: Boolean = false

    private var lastKnownPlayerX: Float = 0f

    private var wave1SpawnPending: Boolean = false
    private var wave1SpawnAtMs: Long = 0L

    fun start(nowMs: Long) {
        worldWidth = arenaWidth * (totalWaves + 1)
        sceneryVariant = Random.nextInt(SCENERY_VARIANT_COUNT)
        spawnPlayer()
        spawnCratesForSection(currentWave)

        wave1SpawnPending = true
        wave1SpawnAtMs = nowMs + WAVE1_SPAWN_DELAY_MS
        spawnSceneryProps()
        lastMovedAtMs = nowMs
        started = true
    }

    private fun sectionStart(section: Int): Float = (section - 1) * arenaWidth

    fun currentVariant(): Int = sceneryVariant

    private fun floorTopY(): Float = arenaHeight * FLOOR_TOP_RATIOS[currentVariant()] - PLAYER_VISUAL_RADIUS

    private fun floorBottomY(): Float = arenaHeight * FLOOR_BOTTOM_RATIOS[currentVariant()]

    private fun floorMidY(): Float = (floorTopY() + floorBottomY()) / 2f

    private fun randomFloorY(rnd: Random): Float {
        val top = floorTopY()
        val bottom = floorBottomY()
        return top + rnd.nextFloat() * (bottom - top).coerceAtLeast(1f)
    }

    private fun spawnPlayer() {

        val startX = if (!started) sectionStart(1) + arenaWidth * 0.15f else lastKnownPlayerX
        lastKnownPlayerX = startX
        player = PlayerFighter(team[teamIndex], startX, floorMidY())
    }

    private fun spawnCratesForSection(section: Int) {
        val rnd = Random(System.currentTimeMillis())
        val base = sectionStart(section)
        val zoneStart = 0.30f
        val zoneEnd = 0.92f
        val sliceWidth = (zoneEnd - zoneStart) / CRATES_PER_SECTION
        repeat(CRATES_PER_SECTION) { i ->
            val kind = if (i % 2 == 0) DestructibleObject.Kind.CAIXA else DestructibleObject.Kind.BARRIL
            val sliceStart = zoneStart + sliceWidth * i
            val x = base + arenaWidth * (sliceStart + rnd.nextFloat() * sliceWidth)
            val y = randomFloorY(rnd)
            destructibles += DestructibleObject(x, y, kind)
        }
    }

    private fun spawnSceneryProps() {
        val rnd = Random(System.currentTimeMillis() xor 0x5EEDL)
        sceneryProps.clear()
        var x = SCENERY_PROP_SPACING * 0.5f
        while (x < worldWidth) {
            val jitterX = x + (rnd.nextFloat() - 0.5f) * SCENERY_PROP_SPACING * 0.6f
            val y = randomFloorY(rnd)

            val kind = rnd.nextInt(SCENERY_PROP_KIND_COUNT)
            val prop = SceneryProp(jitterX.coerceIn(0f, worldWidth), y, kind)
            sceneryProps += prop
            x += SCENERY_PROP_SPACING
        }
    }

    fun update(nowMs: Long, deltaSeconds: Float) {
        if (state != BattleState.RUNNING) return

        if (deathTransitionActive) {
            updateDeathTransition(nowMs)
            updateCamera(deltaSeconds)
            return
        }

        if (wave1SpawnPending && nowMs >= wave1SpawnAtMs) {
            wave1SpawnPending = false
            spawnWaveEnemies()
        }

        if (bossAwaitingProximity && player.x >= bossProximityThresholdX) {
            bossAwaitingProximity = false
            playerLocked = true
            bossPending = true
            bossTriggerAtMs = nowMs
        }

        if (moveX != 0f || moveY != 0f) lastMovedAtMs = nowMs
        movePlayer(deltaSeconds)
        updateCamera(deltaSeconds)
        handlePlayerAttack(nowMs)
        handleSpecialAttack(nowMs)
        handleFoodUse()
        updateEnemies(nowMs, deltaSeconds)
        updateBossIntro(nowMs)
        updateWaveFlow(nowMs)
        checkPlayerDeath(nowMs)
    }

    private fun isWaveLocked(): Boolean = enemies.any { it.isAlive } && !bossSpawned

    private fun updateCamera(deltaSeconds: Float) {
        val maxCamera = (worldWidth - arenaWidth).coerceAtLeast(0f)

        val camCap = if (isWaveLocked()) sectionStart(currentWave).coerceIn(0f, maxCamera) else maxCamera
        val targetCameraX = (player.x - arenaWidth / 2f).coerceIn(0f, camCap)

        val smoothing = (CAMERA_FOLLOW_SPEED * deltaSeconds).coerceIn(0f, 1f)
        cameraX += (targetCameraX - cameraX) * smoothing
    }

    private fun updateWaveFlow(nowMs: Long) {
        if (bossSpawned) return

        if (spawnedInWave == 0) return

        if (enemies.none { it.isAlive }) {
            if (waveClearedAtMs == 0L) {
                waveClearedAtMs = nowMs
                listener.onWaveCleared(currentWave, totalWaves)
            }

            showAdvanceHint = nowMs - lastMovedAtMs >= PLAYER_IDLE_HINT_MS
            val triggerX = sectionStart(currentWave) + arenaWidth * ADVANCE_TRIGGER_X_RATIO
            if (player.x >= triggerX) {
                advanceWave(nowMs)
            }
        }
    }

    private fun advanceWave(nowMs: Long) {
        showAdvanceHint = false
        waveClearedAtMs = 0L
        if (currentWave >= totalWaves) {
            if (boss != null) {

                bossSpawned = true

                bossAwaitingProximity = true
                bossProximityThresholdX = sectionStart(totalWaves + 1) + arenaWidth * 0.25f
            } else {

                endBattle(victory = true)
            }
        } else {
            currentWave++
            spawnedInWave = 0
            spawnCratesForSection(currentWave)
            spawnWaveEnemies()
            listener.onWaveAdvanced(currentWave, totalWaves)
        }
    }

    private fun movePlayer(deltaSeconds: Float) {

        if (playerLocked) return
        val speed = player.moveSpeed * 60f * deltaSeconds
        if (moveX != 0f || moveY != 0f) {
            player.facingRight = moveX >= 0f
        }

        val rawNextX = (player.x + moveX * speed).coerceIn(PLAYER_MARGIN_X, worldWidth - PLAYER_MARGIN_X)

        val nextX = if (isWaveLocked()) {
            rawNextX.coerceAtMost(sectionStart(currentWave) + arenaWidth - PLAYER_MARGIN_X)
        } else {
            rawNextX
        }

        val nextY = (player.y + moveY * speed).coerceIn(floorTopY(), floorBottomY())

        player.x = nextX
        player.y = nextY

        lastKnownPlayerX = player.x
    }

    private fun handlePlayerAttack(nowMs: Long) {
        if (!attackRequested) return
        if (!player.canAttack(nowMs)) return
        player.lastAttackAtMs = nowMs

        val targets = enemies.filter { it.isAlive && !(it.isBoss && bossIntroActive) && player.distanceTo(it) <= player.attackRange }
            .sortedBy { player.distanceTo(it) }
            .take(2)
        targets.forEach { enemy ->
            enemy.takeDamage(player.attackDamage, nowMs)

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

    private fun handleSpecialAttack(nowMs: Long) {
        if (!specialRequested) return
        specialRequested = false
        if (!player.consumeSpecialCharge()) return

        val targets = enemies.filter { it.isAlive && !(it.isBoss && bossIntroActive) && player.distanceTo(it) <= SPECIAL_RANGE }
        targets.forEach { enemy ->
            enemy.takeDamage(player.attackDamage * SPECIAL_DAMAGE_MULTIPLIER, nowMs)
            if (!enemy.isAlive) onEnemyDefeated(enemy)
        }
        specialEffectAtMs = nowMs
        listener.onSpecialUsed()
    }

    private fun handleFoodUse() {
        if (!useFoodRequested) return
        useFoodRequested = false
        val amount = player.consumeFood(FOOD_HEAL_AMOUNT)
        if (amount > 0) listener.onFoodConsumed(amount)
    }

    private fun updateEnemies(nowMs: Long, deltaSeconds: Float) {
        val aliveEnemies = enemies.filter { it.isAlive && !(it.isBoss && bossIntroActive) }
        promoteEngagers(aliveEnemies)
        aliveEnemies.forEach { enemy ->
            val dx = player.x - enemy.x
            val dy = player.y - enemy.y
            val dist = distance(enemy.x, enemy.y, player.x, player.y)
            if (enemy.isEngaging) {
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

                    playerHurtAtMs = nowMs
                }
            } else {

                val waitDistance = enemy.attackRange * 2.4f
                val angle = (enemy.waitSlot.toFloat() / ENEMIES_PER_WAVE) * (2f * PI.toFloat())
                val slotX = player.x + cos(angle) * waitDistance
                val slotY = (player.y + sin(angle) * waitDistance * 0.4f).coerceIn(floorTopY(), floorBottomY())
                val toSlotX = slotX - enemy.x
                val toSlotY = slotY - enemy.y
                val slotDist = distance(enemy.x, enemy.y, slotX, slotY)
                if (slotDist > 12f) {
                    val speed = enemy.moveSpeed * 60f * deltaSeconds
                    val nx = toSlotX / max(slotDist, 0.001f)
                    val ny = toSlotY / max(slotDist, 0.001f)
                    enemy.x += nx * speed
                    enemy.y = (enemy.y + ny * speed).coerceIn(floorTopY(), floorBottomY())
                } else {
                    val phase = nowMs * 0.0025f + enemy.x * 0.05f
                    val shuffleSpeed = enemy.moveSpeed * 60f * deltaSeconds * 0.35f
                    val px = -dy / max(dist, 0.001f)
                    val py = dx / max(dist, 0.001f)
                    val dir = sin(phase)
                    enemy.x += px * dir * shuffleSpeed
                    enemy.y = (enemy.y + py * dir * shuffleSpeed).coerceIn(floorTopY(), floorBottomY())
                }
                enemy.facingRight = dx >= 0f
            }
        }
    }

    private fun promoteEngagers(aliveEnemies: List<EnemyFighter>) {
        var engagingCount = aliveEnemies.count { it.isEngaging }
        while (engagingCount < MAX_ENGAGED_ENEMIES) {
            val candidates = aliveEnemies.filter { !it.isEngaging }
            if (candidates.isEmpty()) break
            val engagedFromLeft = aliveEnemies.any { it.isEngaging && it.x < player.x }
            val engagedFromRight = aliveEnemies.any { it.isEngaging && it.x >= player.x }
            val wantSide = when {
                !engagedFromLeft && engagedFromRight -> candidates.filter { it.x < player.x }
                !engagedFromRight && engagedFromLeft -> candidates.filter { it.x >= player.x }
                else -> candidates
            }
            val pick = (if (wantSide.isNotEmpty()) wantSide else candidates)
                .minByOrNull { distance(it.x, it.y, player.x, player.y) } ?: break
            pick.isEngaging = true
            engagingCount++
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

                listener.onMinibossDefeated(enemy.displayName)
            }
            else -> listener.onCratesOrCoins()
        }
    }

    private fun spawnWaveEnemies() {
        val rnd = Random(System.nanoTime())

        val roomRight = sectionStart(currentWave) + arenaWidth
        val edgeMargin = arenaWidth * 0.1f
        for (i in 0 until ENEMIES_PER_WAVE) {
            val jitter = rnd.nextFloat() * arenaWidth * 0.15f
            val x = (roomRight + edgeMargin + jitter).coerceAtMost(worldWidth - PLAYER_MARGIN_X)
            val y = randomFloorY(rnd)

            if (currentWave in MINIBOSS_WAVES && i == ENEMIES_PER_WAVE - 1) {
                spawnMiniboss(x, y, i)
            } else {
                val regularName = REGULAR_ENEMY_NAMES[(currentWave + i) % REGULAR_ENEMY_NAMES.size]
                enemies += EnemyFighter(regularName, x, y).also { it.waitSlot = i }
            }
        }
        spawnedInWave = ENEMIES_PER_WAVE
    }

    private fun spawnMiniboss(x: Float, y: Float, waitSlot: Int = 0) {
        val avgPlayerLevel = team.map { it.level }.average().takeIf { !it.isNaN() }?.toInt() ?: 1

        val offset = abs(boss?.id ?: 0)
        val name = MINIBOSS_NAMES[(offset + minibossIndex) % MINIBOSS_NAMES.size]
        minibossIndex++
        enemies += EnemyFighter(
            displayName = name,
            startX = x,
            startY = y,
            isMiniboss = true,
            playerLevel = avgPlayerLevel.coerceAtLeast(1)
        ).also { it.waitSlot = waitSlot }
    }

    private fun spawnBossEntity(nowMs: Long) {
        val bossChar = boss ?: return
        listener.onBossIncoming(bossChar.name)

        val avgPlayerLevel = team.map { it.level }.average().takeIf { !it.isNaN() }?.toInt() ?: 1
        bossTargetX = sectionStart(totalWaves + 1) + arenaWidth * 0.65f
        bossIntroStartX = bossTargetX + arenaWidth * 0.4f
        bossIntroStartAtMs = nowMs
        bossIntroActive = true
        val bossFighter = EnemyFighter(
            displayName = bossChar.name,
            startX = bossIntroStartX,
            startY = floorMidY(),
            isBoss = true,
            bossCharacterId = bossChar.id,
            bossPower = bossChar.stats.overallPower,
            playerLevel = avgPlayerLevel.coerceAtLeast(1)
        )

        bossFighter.facingRight = false
        enemies += bossFighter
    }

    private fun updateBossIntro(nowMs: Long) {
        if (bossPending) {
            if (nowMs - bossTriggerAtMs >= BOSS_INTRO_DELAY_MS) {
                bossPending = false
                spawnBossEntity(nowMs)
            }
            return
        }
        if (!bossIntroActive) return
        val bossFighter = enemies.lastOrNull { it.isBoss }
        if (bossFighter == null) {
            bossIntroActive = false
            playerLocked = false
            return
        }
        val t = ((nowMs - bossIntroStartAtMs).toFloat() / BOSS_INTRO_SLIDE_MS).coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t)
        bossFighter.x = bossIntroStartX + (bossTargetX - bossIntroStartX) * eased
        if (t >= 1f) {
            bossIntroActive = false
            playerLocked = false
        }
    }

    private fun checkPlayerDeath(nowMs: Long) {
        if (player.isAlive) return
        listener.onCharacterDefeated(player.character.name)
        deathHasNextCharacter = teamIndex < team.size - 1
        deathTransitionPhase = DeathTransitionPhase.DYING
        deathPhaseStartAtMs = nowMs
    }

    private fun updateDeathTransition(nowMs: Long) {
        val elapsed = nowMs - deathPhaseStartAtMs
        when (deathTransitionPhase) {
            DeathTransitionPhase.DYING -> if (elapsed >= DEATH_LIE_MS) {
                deathTransitionPhase = DeathTransitionPhase.BLINKING
                deathPhaseStartAtMs = nowMs
            }
            DeathTransitionPhase.BLINKING -> if (elapsed >= DEATH_BLINK_MS) {
                if (deathHasNextCharacter) {
                    teamIndex++
                    spawnPlayer()
                    listener.onNextCharacterEnters(player.character.name)
                    dropInProgress = 0f
                    deathTransitionPhase = DeathTransitionPhase.DROPPING_IN
                    deathPhaseStartAtMs = nowMs
                } else {
                    deathTransitionPhase = DeathTransitionPhase.NONE
                    endBattle(victory = false)
                }
            }
            DeathTransitionPhase.DROPPING_IN -> {
                val t = (elapsed.toFloat() / DROP_IN_MS).coerceIn(0f, 1f)
                dropInProgress = t
                if (t >= 1f) {
                    deathTransitionPhase = DeathTransitionPhase.NONE
                }
            }
            DeathTransitionPhase.NONE -> Unit
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

        private const val TOTAL_WAVES = 6
        private const val ENEMIES_PER_WAVE = 5

        private const val MAX_ENGAGED_ENEMIES = 2

        private const val CAMERA_FOLLOW_SPEED = 3f

        private val WAVE_SPAWN_FRACTIONS = floatArrayOf(0.26f, 0.42f, 0.58f, 0.74f, 0.90f)

        private const val WAVE1_SPAWN_DELAY_MS = 3000L

        const val FOOD_HEAL_AMOUNT = 22

        private val MINIBOSS_WAVES = setOf(2, 4)

        private val MINIBOSS_NAMES = listOf("Bulldozer")

        private val REGULAR_ENEMY_NAMES = listOf("Wrecker", "Piledriver", "Thunderball")

        private const val ADVANCE_TRIGGER_X_RATIO = 0.86f

        private const val PLAYER_IDLE_HINT_MS = 4500L

        private const val BOSS_INTRO_DELAY_MS = 900L
        private const val BOSS_INTRO_SLIDE_MS = 3200L

        private const val DEATH_LIE_MS = 900L
        private const val DEATH_BLINK_MS = 700L
        private const val DROP_IN_MS = 550L

        private const val SPECIAL_CHARGE_PER_DAMAGE = 1f / 80f
        private const val SPECIAL_RANGE = 240f
        private const val SPECIAL_DAMAGE_MULTIPLIER = 3

        const val SCENERY_VARIANT_COUNT = 11

        private val FLOOR_TOP_RATIOS = floatArrayOf(
            0.80f, 0.82f, 0.80f, 0.84f,
            0.42f, 0.50f, 0.60f, 0.46f,
            0.72f, 0.81f, 0.78f
        )

        private val FLOOR_BOTTOM_RATIOS = floatArrayOf(
            0.97f, 0.97f, 0.97f, 0.97f,
            0.96f, 0.97f, 0.97f, 0.97f,
            0.97f, 0.97f, 0.97f
        )
        private const val PLAYER_MARGIN_X = 40f
        private const val PLAYER_VISUAL_RADIUS = 32f

        private const val CRATES_PER_SECTION = 4

        private const val SCENERY_PROP_SPACING = 900f

        const val SCENERY_PROP_KIND_COUNT = 5

    }
}
