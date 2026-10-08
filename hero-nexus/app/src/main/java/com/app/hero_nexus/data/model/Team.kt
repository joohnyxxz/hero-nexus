package com.app.hero_nexus.data.model

data class Team(
    val characterIds: List<Int> = emptyList()
) {
    val isFull: Boolean get() = characterIds.size >= 3
    val isEmpty: Boolean get() = characterIds.isEmpty()
}
