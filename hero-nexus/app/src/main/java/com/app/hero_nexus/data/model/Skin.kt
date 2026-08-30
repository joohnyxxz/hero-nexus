package com.app.hero_nexus.data.model

/**
 * Skin cosmética de um personagem (seção 21). Skins NUNCA alteram atributos (regra 4 / seção 21.1) —
 * o campo [priceCoins] é o único efeito de "progressão" que uma skin tem.
 */
data class Skin(
    val id: String,
    val characterId: Int,
    val name: String,
    val priceCoins: Int
) {
    val isFree: Boolean get() = priceCoins == 0
}

/** Gera duas skins padrão por personagem: uma clássica grátis e uma variante paga. */
object SkinCatalog {
    fun forCharacter(characterId: Int, characterName: String): List<Skin> = listOf(
        Skin(id = "${characterId}_classic", characterId = characterId, name = "Traje clássico", priceCoins = 0),
        Skin(id = "${characterId}_alt", characterId = characterId, name = "Traje alternativo", priceCoins = 1000)
    )
}
