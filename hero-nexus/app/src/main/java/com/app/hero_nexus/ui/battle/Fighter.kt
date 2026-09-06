package com.app.hero_nexus.ui.battle

import com.app.hero_nexus.data.model.Character
import kotlin.math.hypot

/**
 * Combatente genérico dentro da arena de batalha (seção 12 - inspirado em Golden Axe).
 * `x` agora é uma coordenada de MUNDO (não mais de tela) -- a arena inteira é mais larga que a
 * tela e a câmera acompanha o jogador (seção "fluxo contínuo", feedback do usuário 31/08: "igual
 * Golden Axe e Streets of Rage, você anda e a câmera segue"). A BattleView é quem subtrai a
 * posição da câmera pra saber onde desenhar cada coisa na tela.
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

    /** Cura (ex: comida guardada no slot -- seção "loot"), sem passar do máximo. */
    fun heal(amount: Int) {
        health = (health + amount).coerceAtMost(maxHealth)
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
    // Dano reduzido um pouco (era /12, seção "balanceamento" — feedback: batalha fácil demais)
    // pra cada inimigo levar mais golpes e a luta durar mais.
    attackDamage = 5 + (character.stats.strength + character.stats.combat) / 14,
    moveSpeed = 5f + character.stats.speed / 25f,
    attackRange = 130f,
    attackCooldownMs = (750 - character.stats.speed * 3).coerceAtLeast(280).toLong()
) {
    // ------------------------------------------------------------------------------ Especial
    // Carrega com o dano causado nos ataques normais (feedback do usuário 31/08: "uma barrinha
    // que carrega conforme o dano"); guarda no máximo 2 usos prontos ("no máximo 2 especial acho"
    // -- o usuário deixou livre entre o estilo Golden Axe/Streets of Rage, decidido aqui como um
    // híbrido: carrega com dano, mas guarda até 2 cargas em vez de gastar assim que enche).
    var specialCharge: Float = 0f
        private set

    fun addSpecialCharge(amount: Float) {
        specialCharge = (specialCharge + amount).coerceAtMost(MAX_SPECIAL_CHARGES)
    }

    /** Consome 1 carga cheia, se houver. Retorna false se ainda não tinha nenhuma pronta. */
    fun consumeSpecialCharge(): Boolean {
        if (specialCharge < 1f) return false
        specialCharge -= 1f
        return true
    }

    /** 0f..1f só da carga "corrente" (a que ainda está enchendo) -- pra desenhar a barrinha. */
    val currentChargeProgress: Float get() = (specialCharge - storedSpecials).coerceIn(0f, 1f)

    /** Quantos usos completos já estão prontos (0, 1 ou 2). */
    val storedSpecials: Int get() = specialCharge.toInt().coerceIn(0, MAX_SPECIAL_CHARGES.toInt())

    // -------------------------------------------------------------------------------- Slot de item
    // Comida quebrada de caixa/barril não cura na hora -- fica guardada num slot até o jogador
    // decidir usar (feedback 31/08: "não necessariamente eu peguei comida que eu quero comer na
    // hora, deveria ter slot"). Rodada 13 (01/09): o slot virou uma PILHA de até 3 comidinhas
    // (feedback: "o slot para cura seria de 3 comidinhas") -- antes era só 1, e uma segunda
    // comida guardada enquanto já tinha uma virava moeda bônus em vez de acumular. Cada carga
    // cura o mesmo tanto fixo (ver BattleEngine.FOOD_HEAL_AMOUNT); ao chegar em 3, novas comidas
    // quebradas viram moeda bônus até o jogador comer alguma e abrir espaço de novo.
    var foodCharges: Int = 0
        private set

    /** Tenta guardar mais uma comida no slot. Retorna false se já estava no máximo (3). */
    fun storeFood(): Boolean {
        if (foodCharges >= MAX_FOOD_CHARGES) return false
        foodCharges++
        return true
    }

    /** Come 1 carga guardada (se houver) e cura [healAmount]. Retorna quanto curou -- 0 se o
     * slot tava vazio. */
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

/** Inimigo comum, miniboss ou chefe (vilão) — seções 14 e 16. Rodada 13 (feedback 01/09,
 * "só um vilão e 15 capangas nao desafia tanto... sabe em golden axe? vc mata um vilão, aparece
 * uma tela preta mas ainda continua na batalha"): [isMiniboss] é um capanga NOMEADO e mais forte
 * que aparece no meio de certas levas -- ao morrer dispara uma transição de tela preta rápida
 * (ver BattleListener.onMinibossDefeated), mas a luta CONTINUA (diferente de [isBoss], que é
 * sempre o vilão final e encerra a batalha). */
class EnemyFighter(
    val displayName: String,
    startX: Float,
    startY: Float,
    val isBoss: Boolean = false,
    val isMiniboss: Boolean = false,
    val bossCharacterId: Int? = null,
    // Poder geral (Comic Vine) do vilão escolhido e nível médio do time do jogador no momento
    // da luta (seção "balanceamento" — feedback 30/08: vilões fixos ficavam fáceis demais depois
    // que o time evoluía, principalmente em revanches). O chefe escala com os dois: vilões mais
    // fortes no ranking já nascem mais duros, e todo chefe fica mais difícil conforme o time do
    // jogador sobe de nível — inclusive em revanches contra o mesmo vilão. Miniboss escala só com
    // o nível do time (não tem um vilão Comic Vine por trás, é só um capanga de elite).
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
    /** Recompensas ao derrotar (seção 14) -- miniboss paga o dobro de um capanga comum. */
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

/** Caixas/barris destrutíveis do cenário (seção 15). */
class DestructibleObject(
    val x: Float,
    val y: Float,
    val kind: Kind
) {
    enum class Kind { CAIXA, BARRIL }

    /** O que sai de dentro ao quebrar (seção "loot" -- feedback do usuário 30/08: "usa % de
     * chance de vir as coisas nisso, tipo 0,2% de um novo personagem, 70% de moeda"). */
    enum class RewardKind { COIN, FOOD, CHARACTER }

    var hp: Int = 15
    var broken: Boolean = false
        private set
    val rewardCoins: Int get() = com.app.hero_nexus.util.Constants.COINS_PER_CRATE

    /** Sorteado só uma vez, no instante em que quebra -- fica fixo depois disso. */
    var rewardKind: RewardKind = RewardKind.COIN
        private set

    /** Instante (ms) em que quebrou -- a BattleView usa isso pra saber há quanto tempo tá na
     * animação de quebra (seção "loot v2" rodada 13, feedback 01/09: "uma animação quando
     * quebrar, tipo tremer e virar estilhaços"). */
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

    /** Estilhaços determinísticos (sempre os mesmos pra ESSE objeto -- calculados uma vez, na
     * primeira leitura) usados só pela BattleView pra desenhar a explosão de quebra. Semeados
     * pela própria posição do objeto em vez de aleatório puro, então a mesma caixa sempre
     * estilhaça do mesmo jeito (útil pra debug e pra não "piscar" diferente a cada onDraw). */
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
            // Rodada 13 (feedback 01/09: "a chance de desbloquear um heroi/anti-heroi nos
            // barrils deveria ser maior, nao que a cada batalha é certeza que desbloqueie
            // alguem"): 0.2% -> 0.7% por caixa/barril quebrado. Com mais levas agora (6 em vez
            // de 4, ~36 caixas por batalha) isso dá uns 20-25% de chance de pelo menos um drop
            // por batalha -- alto o bastante pra sentir, ainda longe de garantido.
            roll < 0.7f -> RewardKind.CHARACTER   // 0.7% -- raro, um personagem bônus
            roll < 28.0f -> RewardKind.FOOD        // 27.3% -- comida, guarda no slot (até 3)
            else -> RewardKind.COIN                // 72% -- o de sempre
        }
    }

    companion object {
        private const val SHARD_COUNT = 7
    }
}

/** Um fragmento da animação de quebra (ângulo/velocidade/tamanho/rotação) -- puramente
 * descritivo, sem nada de Android, quem sabe desenhar é a BattleView. */
data class ShardSpec(val angle: Float, val speed: Float, val size: Float, val spin: Float)
