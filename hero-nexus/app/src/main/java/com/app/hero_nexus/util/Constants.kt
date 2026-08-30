package com.app.hero_nexus.util

/**
 * Constantes globais do Hero Nexus.
 * Valores de jogo (XP, moedas, tamanho do time) seguem o Documento de Projeto.
 */
object Constants {

    // Comic Vine API (seção 4 do documento)
    const val COMIC_VINE_BASE_URL = "https://comicvine.gamespot.com/api/"
    const val COMIC_VINE_USER_AGENT = "HeroNexusApp/1.0 (Android; contato: maldonadojoao326@gmail.com)"

    /** ID do publisher "Marvel" na Comic Vine (comicvine.gamespot.com/marvel/4010-31/). */
    const val MARVEL_PUBLISHER_ID = 31

    /** Quantos personagens buscar por página (máximo permitido pela API é 100). */
    const val CHARACTERS_PAGE_SIZE = 50

    /** Cache local: depois desse tempo os dados podem ser atualizados (seção 26 - evitar chamadas desnecessárias). */
    const val CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L // 24h

    // Regras de time / deck (seção 11)
    const val MAX_TEAM_SIZE = 3

    // Quantos personagens um jogador novo já recebe desbloqueados (precisa dar pra montar 1 time completo).
    const val STARTER_CHARACTER_COUNT = MAX_TEAM_SIZE

    // XP (seção 19)
    const val XP_PER_ENEMY = 25
    const val XP_PER_BOSS = 500
    const val XP_PER_MISSION = 200

    // Moedas (seção 20)
    const val COINS_PER_ENEMY = 10
    const val COINS_PER_BOSS = 200
    const val COINS_PER_CRATE = 5

    // Progressão de nível: XP necessário para o próximo nível cresce linearmente.
    const val XP_PER_LEVEL = 1000

    // Firestore (seção 5 - banco de dados da aplicação / dados do jogador)
    const val COL_USERS = "users"
    const val COL_USER_CHARACTERS = "characters"
    const val COL_DECK = "deck"
    const val COL_MISSIONS = "missions"
    const val COL_SKINS = "skins"
    const val COL_BATTLE_HISTORY = "battle_history"
    /** Catálogo global de missões (não é por usuário) — fica no banco em vez de mocado no app. */
    const val COL_MISSIONS_CATALOG = "missions_catalog"

    const val ROOM_DB_NAME = "hero_nexus_cache.db"
}
