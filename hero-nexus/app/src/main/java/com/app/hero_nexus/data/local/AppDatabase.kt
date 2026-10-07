package com.app.hero_nexus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.app.hero_nexus.util.Constants

@Database(
    entities = [CharacterEntity::class, TranslationEntity::class],
    // Rodada 15, parte 57 (07/10/2026): 2 -> 3, coluna nova isTopRanked em CharacterEntity.
    // fallbackToDestructiveMigration() abaixo cobre isso (é só cache, repopulado da API).
    version = 3,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun characterDao(): CharacterDao
    abstract fun translationDao(): TranslationDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Constants.ROOM_DB_NAME
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
