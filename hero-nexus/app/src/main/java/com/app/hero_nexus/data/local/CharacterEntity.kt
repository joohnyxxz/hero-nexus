package com.app.hero_nexus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
    val category: String,
    val strength: Int,
    val speed: Int,
    val intelligence: Int,
    val durability: Int,
    val power: Int,
    val combat: Int,

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
