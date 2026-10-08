package com.app.hero_nexus.data.model

data class Skin(
    val id: String,
    val characterId: Int,
    val name: String,
    val priceCoins: Int
) {
    val isFree: Boolean get() = priceCoins == 0
}

object SkinCatalog {
    fun forCharacter(characterId: Int, characterName: String): List<Skin> {
        val key = characterName.lowercase()
        val premium = when {
            key.contains("spider-man") || key.contains("spiderman") ->
                Skin(id = "${characterId}_symbiote", characterId = characterId, name = "Simbionte", priceCoins = 0)
            key.contains("captain america") || key.contains("capitao america") || key.contains("capitão america") ->
                Skin(id = "${characterId}_zombie", characterId = characterId, name = "Zumbi (Marvel Zombies)", priceCoins = 10000)
            key.contains("iron man") || key.contains("homem de ferro") ->
                Skin(id = "${characterId}_mark1", characterId = characterId, name = "Mark I", priceCoins = 10000)
            else -> null
        } ?: return emptyList()
        val classic = Skin(id = "${characterId}_classic", characterId = characterId, name = "Traje clássico", priceCoins = 0)
        return listOf(classic, premium)
    }
}
