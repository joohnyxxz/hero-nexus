package com.app.hero_nexus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {

    // Rodada 15, parte 60 (07/10/2026): correção de bug real -- isto ordenava só pela coluna
    // "power" (UM dos 6 atributos, sem relação com raridade), não pelo Poder Geral (média dos
    // 6 -- ver BattleStats.overallPower). Resultado: um personagem LENDARIO de Força/Combate
    // altíssimos mas "power" mediano aparecia depois de um EPICO com "power" alto -- exatamente
    // o "começa aparecendo os épicos" relatado. Ordenar pela SOMA dos 6 atributos é equivalente
    // a ordenar pela média (só não divide por 6), sem precisar de coluna nova nem migração.
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

    /** Rodada 15, parte 57 (07/10/2026): cache já veio do recurso plural sem `powers` (ver
     * CharacterRepository.fetchAndCachePowers) -- grava o que a tela de detalhe buscou depois,
     * no recurso singular, pra não precisar buscar de novo da próxima vez. */
    @Query("UPDATE characters SET powers = :powers WHERE comicVineId = :id")
    suspend fun updatePowers(id: Int, powers: List<String>)

    /** Marca só os ids em [topIds] como isTopRanked = true e TODO o resto como false, numa
     * única instrução -- sempre o ranking inteiro recalculado do zero (ver
     * CharacterRepository.refreshTopRankedFlags()), nunca um ajuste incremental. */
    @Query("UPDATE characters SET isTopRanked = (comicVineId IN (:topIds))")
    suspend fun setTopRanked(topIds: List<Int>)

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
