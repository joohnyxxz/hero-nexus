package com.app.hero_nexus.data.model

/** Alinhamento do personagem no universo (seção 8 - filtros da coleção). */
enum class CharacterCategory {
    HEROI, ANTI_HEROI, VILAO
}

/** Raridade exibida no card (seção 34 - design dos cards). */
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

/** Atributos de combate (seção 9 - ficha do personagem / seção 4.3 - uso em batalha). */
data class BattleStats(
    val strength: Int,
    val speed: Int,
    val intelligence: Int,
    val durability: Int,
    val power: Int,
    val combat: Int
) {
    /** "Poder geral" mostrado no card e usado para ordenar/filtrar a coleção. */
    val overallPower: Int
        get() = ((strength + speed + intelligence + durability + power + combat) / 6.0).toInt()
}

/**
 * Modelo de domínio que une:
 *  - dados vindos da Comic Vine (via cache local em Room) — "Homem-Aranha existe"
 *  - estado do jogador vindo do Firestore — "o jogador desbloqueou Homem-Aranha"
 * (ver seção 5 do documento: Arquitetura de dados).
 */
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
    // Estado do jogador (Firestore); valores padrão = personagem ainda não visto/desbloqueado.
    val unlocked: Boolean = false,
    val level: Int = 1,
    val xp: Int = 0,
    val equippedSkinId: String? = null
) {
    val rarity: Rarity get() = Rarity.fromPower(stats.overallPower)
}

/** Estado do jogador para UM personagem, como guardado no Firestore (seção 25 - user_characters). */
data class UserCharacterState(
    val unlocked: Boolean = false,
    val level: Int = 1,
    val xp: Int = 0,
    val equippedSkinId: String? = null
)
