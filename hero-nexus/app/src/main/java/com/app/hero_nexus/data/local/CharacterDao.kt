package com.app.hero_nexus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {

    @Query("SELECT * FROM characters ORDER BY (strength + speed + intelligence + durability + power + combat) DESC")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE comicVineId = :id LIMIT 1")
    suspend fun getById(id: Int): CharacterEntity?

    @Query("SELECT * FROM characters ORDER BY name ASC")
    suspend fun getAllOnce(): List<CharacterEntity>

    @Query("SELECT COUNT(*) FROM characters")
    suspend fun count(): Int

    @Query("SELECT MAX(cachedAtMillis) FROM characters")
    suspend fun lastCachedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(characters: List<CharacterEntity>)

    @Query("DELETE FROM characters")
    suspend fun clear()

    @Query("UPDATE characters SET powers = :powers WHERE comicVineId = :id")
    suspend fun updatePowers(id: Int, powers: List<String>)

    @Query("UPDATE characters SET isTopRanked = (comicVineId IN (:topIds))")
    suspend fun setTopRanked(topIds: List<Int>)

    @Transaction
    suspend fun replaceAll(characters: List<CharacterEntity>) {
        clear()
        insertAll(characters)
    }
}
