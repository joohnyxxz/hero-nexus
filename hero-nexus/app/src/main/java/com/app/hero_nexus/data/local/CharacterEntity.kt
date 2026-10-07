package com.app.hero_nexus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Cache local dos personagens vindos da Comic Vine (seção 25/26 do documento:
 * "characters (Cache/controlador local)"). Evita bater na API toda vez que a coleção abre.
 */
@Entity(tableName = "characters")
@TypeConverters(StringListConverter::class)
data class CharacterEntity(
    @PrimaryKey val comicVineId: Int,
    val name: String,
    val realName: String?,
    val imageUrl: String?,
    val deck: String?,
    val description: String?,
    val powers: List<String>,
    val publisherName: String?,
    val countOfIssueAppearances: Int,
    val siteDetailUrl: String?,
    val category: String, // CharacterCategory.name
    val strength: Int,
    val speed: Int,
    val intelligence: Int,
    val durability: Int,
    val power: Int,
    val combat: Int,
    // Rodada 15, parte 57 (07/10/2026): raridade LENDARIO deixou de ser um limiar fixo de OVR
    // e virou "os 3 melhores do catálogo inteiro" (pedido do usuário) -- este flag é recalculado
    // toda vez que o cache muda (ver CharacterRepository.refreshTopRankedFlags()) e é o que
    // Character.rarity (CharacterMappers.kt) usa pra decidir LENDARIO de verdade.
    val isTopRanked: Boolean = false,
    val cachedAtMillis: Long
)

class StringListConverter {
    private val gson = Gson()
    private val type = object : TypeToken<List<String>>() {}.type

    @TypeConverter
    fun fromList(value: List<String>?): String = gson.toJson(value ?: emptyList<String>())

    @TypeConverter
    fun toList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return gson.fromJson(value, type) ?: emptyList()
    }
}
