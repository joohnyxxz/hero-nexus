package com.app.hero_nexus.data.model

/** Time/deck do jogador (seção 11): até 3 personagens, em ordem — o 1º entra primeiro na batalha. */
data class Team(
    val characterIds: List<Int> = emptyList()
) {
    val isFull: Boolean get() = characterIds.size >= 3
    val isEmpty: Boolean get() = characterIds.isEmpty()
}
