package com.app.hero_nexus.util

object Constants {

    const val COMIC_VINE_BASE_URL = "https://comicvine.gamespot.com/api/"
    const val COMIC_VINE_USER_AGENT = "HeroNexusApp/1.0 (Android; contato: maldonadojoao326@gmail.com)"

    const val MARVEL_PUBLISHER_ID = 31

    const val CHARACTERS_PAGE_SIZE = 20

    const val INITIAL_PAGE_TARGET = 20

    const val LOAD_MORE_BATCH_TARGET = 20

    const val MAX_REQUESTS_PER_BATCH = 8

    const val BATCH_LOAD_TIMEOUT_MILLIS = 25_000L

    const val SEARCH_REMOTE_TIMEOUT_MILLIS = 10_000L

    const val SPLASH_PREFETCH_TIMEOUT_MS = 6000L

    const val CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L

    const val MAX_TEAM_SIZE = 3

    const val STARTER_CHARACTER_COUNT = 3

    const val XP_PER_ENEMY = 25
    const val XP_PER_BOSS = 500
    const val XP_PER_MISSION = 200

    const val COINS_PER_ENEMY = 10
    const val COINS_PER_BOSS = 200
    const val COINS_PER_CRATE = 5

    const val XP_PER_LEVEL = 1000

    const val COL_USERS = "users"
    const val COL_USER_CHARACTERS = "characters"
    const val COL_DECK = "deck"
    const val COL_MISSIONS = "missions"
    const val COL_SKINS = "skins"
    const val COL_BATTLE_HISTORY = "battle_history"

    const val COL_MISSIONS_CATALOG = "missions_catalog"

    const val ROOM_DB_NAME = "hero_nexus_cache.db"

    const val TRANSLATION_BASE_URL = "https://api.mymemory.translated.net/"
    const val TRANSLATION_LANG_PAIR = "en|pt-BR"

    const val TRANSLATION_CONTACT_EMAIL = "maldonadojoao326@gmail.com"

    const val TRANSLATION_MAX_CHARS = 450

    const val ABOUT_MAX_CHARS = 420

    const val PEXELS_BASE_URL = "https://api.pexels.com/"
    const val PEXELS_LOGIN_BG_QUERY = "comic book superhero action dramatic"
}
