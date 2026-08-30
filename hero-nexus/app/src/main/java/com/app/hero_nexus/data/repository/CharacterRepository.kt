package com.app.hero_nexus.data.repository

import com.app.hero_nexus.data.local.CharacterDao
import com.app.hero_nexus.data.local.CharacterEntity
import com.app.hero_nexus.data.remote.CharacterDto
import com.app.hero_nexus.data.remote.ComicVineApi
import com.app.hero_nexus.util.CharacterCategorizer
import com.app.hero_nexus.util.Constants
import com.app.hero_nexus.util.PowerCalculator
import com.app.hero_nexus.util.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Fonte única dos dados de personagens: busca na Comic Vine e guarda em cache local (Room),
 * seguindo a seção 26 do documento ("evitar consultar a API repetidamente sem necessidade").
 */
class CharacterRepository(
    private val api: ComicVineApi,
    private val dao: CharacterDao
) {
    /** Fluxo reativo da coleção em cache — a UI observa isso e é atualizada sozinha após um refresh. */
    fun observeCollection(): Flow<List<CharacterEntity>> = dao.observeAll()

    suspend fun getCached(id: Int): CharacterEntity? = dao.getById(id)

    suspend fun getAllCached(): List<CharacterEntity> = dao.getAllOnce()

    /**
     * Garante que existam dados. Só bate na API se o cache estiver vazio, "velho" (> CACHE_TTL)
     * ou se [force] for true (ex: pull-to-refresh).
     */
    suspend fun refreshIfNeeded(force: Boolean = false): Resource<Unit> {
        val count = dao.count()
        val lastCached = dao.lastCachedAt() ?: 0L
        val isStale = System.currentTimeMillis() - lastCached > Constants.CACHE_TTL_MILLIS
        if (!force && count > 0 && !isStale) {
            return Resource.Success(Unit)
        }
        return refreshFromNetwork()
    }

    suspend fun refreshFromNetwork(): Resource<Unit> {
        return try {
            val marvelOnly = mutableListOf<CharacterDto>()
            val seenIds = mutableSetOf<Int>()
            var offset = 0
            var totalResults = Int.MAX_VALUE
            // Busca algumas páginas (personagens mais populares primeiro, ver ComicVineApi.getCharacters)
            // até um teto razoável para não estourar limites da API nem deixar o app pesado.
            val maxCharacters = Constants.CHARACTERS_PAGE_SIZE * 4
            // A Comic Vine costuma IGNORAR o filter=publisher:X no recurso /characters/ (falha
            // conhecida da API) — é comum aparecer gente de outros universos (ex: Brainiac 5, da DC)
            // mesmo pedindo só Marvel. Por isso SEMPRE filtramos por publisher no cliente também,
            // abaixo. Como isso pode descartar boa parte de cada página, damos um teto maior de
            // requisições (não só de personagens) pra ainda conseguir juntar ~200 marvels de verdade.
            val maxRequests = 15
            var requests = 0
            while (offset < totalResults && marvelOnly.size < maxCharacters && requests < maxRequests) {
                val response = api.getCharacters(offset = offset)
                requests++
                totalResults = response.numberOfTotalResults
                response.results
                    .filter { it.publisher?.id == Constants.MARVEL_PUBLISHER_ID }
                    .filter { seenIds.add(it.id) }
                    .let { marvelOnly += it }
                offset += Constants.CHARACTERS_PAGE_SIZE
                if (response.results.isEmpty()) break
            }
            val entities = marvelOnly
                .filter { !it.name.isNullOrBlank() }
                .map { it.toEntity() }
            dao.replaceAll(entities)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Falha ao buscar personagens da Comic Vine", e)
        }
    }

    private fun CharacterDto.toEntity(): CharacterEntity {
        val appearances = countOfIssueAppearances ?: 0
        val category = CharacterCategorizer.categorize(name ?: "")
        val stats = PowerCalculator.calculateStats(id, appearances)
        return CharacterEntity(
            comicVineId = id,
            name = name ?: "Desconhecido",
            realName = realName,
            imageUrl = image?.mediumUrl ?: image?.originalUrl,
            deck = deck,
            description = description,
            powers = powers?.mapNotNull { it.name } ?: emptyList(),
            publisherName = publisher?.name,
            countOfIssueAppearances = appearances,
            siteDetailUrl = siteDetailUrl,
            category = category.name,
            strength = stats.strength,
            speed = stats.speed,
            intelligence = stats.intelligence,
            durability = stats.durability,
            power = stats.power,
            combat = stats.combat,
            cachedAtMillis = System.currentTimeMillis()
        )
    }
}
