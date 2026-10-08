package com.app.hero_nexus.data.model

enum class ChestType {
    HEROI, ESPECIAL
}

data class ChestReward(
    val newCharacterId: Int? = null,
    val coins: Int = 0,
    val xp: Int = 0
)
