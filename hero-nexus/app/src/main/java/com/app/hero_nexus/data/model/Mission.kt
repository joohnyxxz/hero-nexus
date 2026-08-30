package com.app.hero_nexus.data.model

/**
 * Definição de uma missão. Vive no Firestore, coleção "missions_catalog" (seção 22 do documento) —
 * é um catálogo global (igual para todo mundo), não um dado por usuário.
 * Precisa de valores padrão em todo campo para o Firestore conseguir desserializar (toObject).
 */
data class MissionDefinition(
    val id: String = "",
    val title: String = "",
    val target: Int = 0,
    val rewardXp: Int = 0,
    val rewardCoins: Int = 0
)

/** Progresso do jogador numa missão, guardado em users/{uid}/missions/{id}. */
data class MissionProgress(
    val id: String = "",
    val progress: Int = 0,
    val completed: Boolean = false,
    val claimed: Boolean = false
)

/** Junção de definição + progresso, pronta para a UI. */
data class Mission(
    val definition: MissionDefinition,
    val progress: MissionProgress
)

/**
 * Valores usados apenas para SEMEAR o Firestore (coleção "missions_catalog") na primeira vez que o
 * catálogo é lido vazio. Depois de semeado, o catálogo real mora no banco — o app nunca mais lê
 * essa lista em runtime (ver UserRepository.getMissionCatalog).
 */
object MissionCatalog {
    val DEFAULTS = listOf(
        MissionDefinition("kill_20_enemies", "Derrote 20 inimigos", target = 20, rewardXp = 100, rewardCoins = 200),
        MissionDefinition("break_10_crates", "Quebre 10 caixas", target = 10, rewardXp = 0, rewardCoins = 150),
        MissionDefinition("complete_1_stage", "Complete uma fase", target = 1, rewardXp = 250, rewardCoins = 0),
        MissionDefinition("defeat_1_boss", "Derrote um vilão", target = 1, rewardXp = 500, rewardCoins = 0)
    )
}
