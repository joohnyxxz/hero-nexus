package com.app.hero_nexus.ui.battle

import com.app.hero_nexus.data.model.Character
import kotlin.math.hypot

/**
 * Combatente genérico dentro da arena de batalha (seção 12 - inspirado em Golden Axe).
 * As posições x/y são em pixels de tela, dentro dos limites definidos pela BattleView.
 */
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

    val isAlive: Boolean get() = health > 0

    fun takeDamage(amount: Int) {
        health = (health - amount).coerceAtLeast(0)
    }

    fun canAttack(nowMs: Long): Boolean = nowMs - lastAttackAtMs >= attackCooldownMs

    fun distanceTo(other: Fighter): Float = hypot((x - other.x).toDouble(), (y - other.y).toDouble()).toFloat()

    val healthRatio: Float get() = health.toFloat() / maxHealth.toFloat()
}

/** O personagem controlado pelo jogador no momento (seção 13 - troca automática de personagem). */
class PlayerFighter(
    val character: Character,
    startX: Float,
    startY: Float
) : Fighter(
    x = startX,
    y = startY,
    maxHealth = 80 + character.stats.durability * 2,
    attackDamage = 6 + (character.stats.strength + character.stats.combat) / 12,
    moveSpeed = 5f + character.stats.speed / 25f,
    attackRange = 130f,
    attackCooldownMs = (750 - character.stats.speed * 3).coerceAtLeast(280).toLong()
)

/** Inimigo comum ou chefe (vilão) — seções 14 e 16. */
class EnemyFighter(
    val displayName: String,
    startX: Float,
    startY: Float,
    val isBoss: Boolean = false,
    val bossCharacterId: Int? = null
) : Fighter(
    x = startX,
    y = startY,
    maxHealth = if (isBoss) 260 else 34,
    attackDamage = if (isBoss) 9 else 5,
    moveSpeed = if (isBoss) 2.6f else 2.2f,
    attackRange = if (isBoss) 140f else 90f,
    attackCooldownMs = if (isBoss) 900L else 1100L
) {
    /** Recompensas ao derrotar (seção 14). */
    val rewardXp: Int get() = if (isBoss) com.app.hero_nexus.util.Constants.XP_PER_BOSS else com.app.hero_nexus.util.Constants.XP_PER_ENEMY
    val rewardCoins: Int get() = if (isBoss) com.app.hero_nexus.util.Constants.COINS_PER_BOSS else com.app.hero_nexus.util.Constants.COINS_PER_ENEMY
}

/** Caixas/barris destrutíveis do cenário (seção 15). */
class DestructibleObject(
    val x: Float,
    val y: Float,
    val kind: Kind
) {
    enum class Kind { CAIXA, BARRIL }

    var hp: Int = 15
    var broken: Boolean = false
    val rewardCoins: Int get() = com.app.hero_nexus.util.Constants.COINS_PER_CRATE

    fun hit(damage: Int) {
        if (broken) return
        hp -= damage
        if (hp <= 0) broken = true
    }
}
