package com.app.hero_nexus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {

    @Query("SELECT * FROM characters ORDER BY power DESC")
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

    /**
     * Substitui o cache inteiro de uma vez só (limpa + insere), numa transação — necessário pro
     * refresh sempre refletir exatamente o que a API/filtro devolveram agora, sem sobrar lixo de
     * uma busca anterior (ex: personagem de outro universo que não deveria mais estar aqui).
     */
    @Transaction
    suspend fun replaceAll(characters: List<CharacterEntity>) {
        clear()
        insertAll(characters)
    }
}
