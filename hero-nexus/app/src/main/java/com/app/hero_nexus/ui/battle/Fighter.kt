package com.app.hero_nexus.ui.battle

import com.app.hero_nexus.data.model.Character
import kotlin.math.hypot

abstract class Fighter(
    var x: Float,
    var y: Float,
    val maxHealth: Int,
    val attackDamage: Int,
    val moveSpeed: Float,
    val attackRange: Float = 110f,
    val attackCooldownMs: Long = 700L
) {
    var health: Int = maxHealth
        private set

    var lastAttackAtMs: Long = 0L
    var facingRight: Boolean = true

    var lastHurtAtMs: Long = 0L
    var deathAnimStartMs: Long = 0L

    val isAlive: Boolean get() = health > 0

    fun takeDamage(amount: Int, nowMs: Long = 0L) {
        val wasAlive = isAlive
        health = (health - amount).coerceAtLeast(0)
        if (amount > 0) lastHurtAtMs = nowMs
        if (wasAlive && !isAlive) deathAnimStartMs = nowMs
    }

    fun heal(amount: Int) {
        health = (health + amount).coerceAtMost(maxHealth)
    }

    fun canAttack(nowMs: Long): Boolean = nowMs - lastAttackAtMs >= attackCooldownMs

    fun distanceTo(other: Fighter): Float = hypot((x - other.x).toDouble(), (y - other.y).toDouble()).toFloat()

    val healthRatio: Float get() = health.toFloat() / maxHealth.toFloat()
}

class PlayerFighter(
    val character: Character,
    startX: Float,
    startY: Float
) : Fighter(
    x = startX,
    y = startY,
    maxHealth = 80 + character.stats.durability * 2,

    attackDamage = 5 + (character.stats.strength + character.stats.combat) / 14,
    moveSpeed = 5f + character.stats.speed / 25f,
    attackRange = 130f,
    attackCooldownMs = (750 - character.stats.speed * 3).coerceAtLeast(280).toLong()
) {

    var specialCharge: Float = 2f
        private set

    fun addSpecialCharge(amount: Float) {
        specialCharge = (specialCharge + amount).coerceAtMost(MAX_SPECIAL_CHARGES)
    }

    fun consumeSpecialCharge(): Boolean {
        if (specialCharge < 1f) return false
        specialCharge -= 1f
        return true
    }

    val currentChargeProgress: Float get() = (specialCharge - storedSpecials).coerceIn(0f, 1f)

    val storedSpecials: Int get() = specialCharge.toInt().coerceIn(0, MAX_SPECIAL_CHARGES.toInt())

    var foodCharges: Int = 0
        private set

    fun storeFood(): Boolean {
        if (foodCharges >= MAX_FOOD_CHARGES) return false
        foodCharges++
        return true
    }

    fun consumeFood(healAmount: Int): Int {
        if (foodCharges <= 0) return 0
        foodCharges--
        heal(healAmount)
        return healAmount
    }

    companion object {
        const val MAX_SPECIAL_CHARGES = 2f
        const val MAX_FOOD_CHARGES = 3
    }
}

class EnemyFighter(
    val displayName: String,
    startX: Float,
    startY: Float,
    val isBoss: Boolean = false,
    val isMiniboss: Boolean = false,
    val bossCharacterId: Int? = null,

    bossPower: Int = 50,
    playerLevel: Int = 1
) : Fighter(
    x = startX,
    y = startY,
    maxHealth = when {
        isBoss -> 240 + bossPower * 3 + playerLevel * 22
        isMiniboss -> 95 + playerLevel * 9
        else -> 46
    },
    attackDamage = when {
        isBoss -> 7 + bossPower / 9 + playerLevel / 2
        isMiniboss -> 9 + playerLevel / 3
        else -> 6
    },
    moveSpeed = when {
        isBoss -> (2.6f + playerLevel * 0.03f).coerceAtMost(3.6f)
        isMiniboss -> 2.6f
        else -> 2.2f
    },
    attackRange = when {
        isBoss -> 140f
        isMiniboss -> 110f
        else -> 90f
    },
    attackCooldownMs = when {
        isBoss -> (900L - playerLevel * 8L).coerceAtLeast(560L)
        isMiniboss -> 950L
        else -> 1100L
    }
) {

    var isEngaging: Boolean = false

    var waitSlot: Int = 0

    val rewardXp: Int get() = when {
        isBoss -> com.app.hero_nexus.util.Constants.XP_PER_BOSS
        isMiniboss -> com.app.hero_nexus.util.Constants.XP_PER_ENEMY * 2
        else -> com.app.hero_nexus.util.Constants.XP_PER_ENEMY
    }
    val rewardCoins: Int get() = when {
        isBoss -> com.app.hero_nexus.util.Constants.COINS_PER_BOSS
        isMiniboss -> com.app.hero_nexus.util.Constants.COINS_PER_ENEMY * 2
        else -> com.app.hero_nexus.util.Constants.COINS_PER_ENEMY
    }
}

class DestructibleObject(
    val x: Float,
    val y: Float,
    val kind: Kind
) {
    enum class Kind { CAIXA, BARRIL }

    enum class RewardKind { COIN, FOOD, CHARACTER }

    var hp: Int = 15
    var broken: Boolean = false
        private set
    val rewardCoins: Int get() = com.app.hero_nexus.util.Constants.COINS_PER_CRATE

    var rewardKind: RewardKind = RewardKind.COIN
        private set

    var brokenAtMs: Long = 0L
        private set

    fun hit(damage: Int, rnd: kotlin.random.Random = kotlin.random.Random.Default, nowMs: Long = System.currentTimeMillis()) {
        if (broken) return
        hp -= damage
        if (hp <= 0) {
            broken = true
            brokenAtMs = nowMs
            rewardKind = rollReward(rnd)
        }
    }

    val shards: List<ShardSpec> by lazy {
        val seed = (x.toInt() * 73856093) xor (y.toInt() * 19349663) xor kind.ordinal
        val rnd = kotlin.random.Random(seed)
        List(SHARD_COUNT) {
            ShardSpec(
                angle = rnd.nextFloat() * 360f,
                speed = 60f + rnd.nextFloat() * 90f,
                size = 4f + rnd.nextFloat() * 6f,
                spin = (rnd.nextFloat() - 0.5f) * 720f
            )
        }
    }

    private fun rollReward(rnd: kotlin.random.Random): RewardKind {
        val roll = rnd.nextFloat() * 100f
        return when {

            roll < 0.7f -> RewardKind.CHARACTER
            roll < 28.0f -> RewardKind.FOOD
            else -> RewardKind.COIN
        }
    }

    companion object {
        private const val SHARD_COUNT = 7
    }
}

data class ShardSpec(val angle: Float, val speed: Float, val size: Float, val spin: Float)
