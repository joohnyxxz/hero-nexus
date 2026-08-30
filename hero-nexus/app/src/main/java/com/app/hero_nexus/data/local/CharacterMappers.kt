package com.app.hero_nexus.data.local

import com.app.hero_nexus.data.model.BattleStats
import com.app.hero_nexus.data.model.Character
import com.app.hero_nexus.data.model.CharacterCategory
import com.app.hero_nexus.data.model.UserCharacterState

fun CharacterEntity.toDomain(state: UserCharacterState = UserCharacterState()): Character {
    val stats = BattleStats(
        strength = strength,
        speed = speed,
        intelligence = intelligence,
        durability = durability,
        power = power,
        combat = combat
    )
    val category = runCatching { CharacterCategory.valueOf(category) }.getOrDefault(CharacterCategory.HEROI)
    return Character(
        id = comicVineId,
        name = name,
        realName = realName,
        imageUrl = imageUrl,
        deck = deck,
        description = description,
        powers = powers,
        publisherName = publisherName,
        category = category,
        isBossCandidate = category == CharacterCategory.VILAO,
        stats = stats,
        siteDetailUrl = siteDetailUrl,
        unlocked = state.unlocked,
        level = state.level,
        xp = state.xp,
        equippedSkinId = state.equippedSkinId
    )
}
