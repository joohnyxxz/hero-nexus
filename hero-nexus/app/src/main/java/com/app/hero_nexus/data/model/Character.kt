package com.app.hero_nexus.data.model

enum class CharacterCategory {
    HEROI, ANTI_HEROI, VILAO
}

enum class Rarity {
    COMUM, RARO, EPICO, LENDARIO;

    companion object {
        fun fromPower(power: Int): Rarity = when {
            power >= 90 -> LENDARIO
            power >= 78 -> EPICO
            power >= 65 -> RARO
            else -> COMUM
        }
    }
}

data class BattleStats(
    val strength: Int,
    val speed: Int,
    val intelligence: Int,
    val durability: Int,
    val power: Int,
    val combat: Int
) {

    val overallPower: Int
        get() = ((strength + speed + intelligence + durability + power + combat) / 6.0).toInt()
}

data class Character(
    val id: Int,
    val name: String,
    val realName: String?,
    val imageUrl: String?,
    val deck: String?,
    val description: String?,
    val powers: List<String>,
    val publisherName: String?,
    val category: CharacterCategory,
    val isBossCandidate: Boolean,
    val stats: BattleStats,
    val siteDetailUrl: String?,

    val isTopRanked: Boolean = false,

    val unlocked: Boolean = false,
    val level: Int = 1,
    val xp: Int = 0,
    val equippedSkinId: String? = null
) {

    val rarity: Rarity
        get() = Rarity.fromPower(stats.overallPower)
}

data class UserCharacterState(
    val unlocked: Boolean = false,
    val level: Int = 1,
    val xp: Int = 0,
    val equippedSkinId: String? = null
)
