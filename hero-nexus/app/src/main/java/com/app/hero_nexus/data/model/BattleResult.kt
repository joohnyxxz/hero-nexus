package com.app.hero_nexus.data.model

/** Resumo de uma batalha (seção 23 - tela de resultado). */
data class BattleResult(
    val victory: Boolean,
    val enemiesDefeated: Int,
    val cratesBroken: Int,
    val bossName: String? = null,
    val bossCharacterId: Int? = null,
    val xpGained: Int,
    val coinsGained: Int,
    val chestAwarded: ChestType? = null
)
