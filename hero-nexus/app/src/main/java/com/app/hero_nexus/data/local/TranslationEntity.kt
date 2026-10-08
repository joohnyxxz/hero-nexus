package com.app.hero_nexus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey val sourceHash: Int,
    val sourceText: String,
    val translatedText: String,
    val translatedAtMillis: Long
)
