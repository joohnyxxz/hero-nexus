package com.app.hero_nexus.data.model

data class BattleResult(
    val victory: Boolean,
    val enemiesDefeated: Int,
    val cratesBroken: Int,
    val bossName: String? = null,
    val bossCharacterId: Int? = null,
    val xpGained: Int,
    val coinsGained: Int,
    val chestAwarded: ChestType? = null,

    val bonusCharacterDrops: Int = 0
)
