package com.app.hero_nexus.data.model

/** Tipos de baú (seção 18 do documento). */
enum class ChestType {
    HEROI, ESPECIAL
}

/** Resultado de abrir um baú, mostrado no diálogo de recompensa. */
data class ChestReward(
    val newCharacterId: Int? = null,
    val coins: Int = 0,
    val xp: Int = 0
)
