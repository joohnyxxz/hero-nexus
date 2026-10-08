package com.app.hero_nexus.data.repository

import com.app.hero_nexus.data.local.CharacterDao
import com.app.hero_nexus.data.local.CharacterEntity
import com.app.hero_nexus.data.remote.CharacterDto
import com.app.hero_nexus.data.remote.ComicVineApi
import com.app.hero_nexus.util.CharacterCategorizer
import com.app.hero_nexus.util.Constants
import com.app.hero_nexus.util.PowerCalculator
import com.app.hero_nexus.util.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class CharacterRepository(
    private val api: ComicVineApi,
    private val dao: CharacterDao
) {
    private var nextOffset = 0
    private var totalResults = Int.MAX_VALUE
    private var exhausted = false

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val firstBatchMutex = Mutex()
    private var inFlightFirstBatch: Deferred<Resource<Unit>>? = null

    val hasMore: Boolean get() = !exhausted

    fun observeCollection(): Flow<List<CharacterEntity>> = dao.observeAll()

    suspend fun getCached(id: Int): CharacterEntity? = dao.getById(id)

    suspend fun getAllCached(): List<CharacterEntity> = dao.getAllOnce()

    suspend fun fetchAndCachePowers(id: Int): List<String>? = try {
        val response = api.getCharacterDetail(id)
        val powers = response.results?.powers?.mapNotNull { it.name }
        if (!powers.isNullOrEmpty()) dao.updatePowers(id, powers)
        powers
    } catch (e: Exception) {
        null
    }

    suspend fun ensureFirstBatch(): Resource<Unit> {
        val count = dao.count()
        val lastCached = dao.lastCachedAt() ?: 0L
        val isStale = System.currentTimeMillis() - lastCached > Constants.CACHE_TTL_MILLIS

        if (count >= Constants.INITIAL_PAGE_TARGET && !isStale) return Resource.Success(Unit)

        val replace = count == 0 || isStale
        val deferred = firstBatchMutex.withLock {
            inFlightFirstBatch?.takeIf { it.isActive } ?: repositoryScope.async {
                try {
                    fetchBatch(target = Constants.INITIAL_PAGE_TARGET, replace = replace)
                } finally {
                    firstBatchMutex.withLock { inFlightFirstBatch = null }
                }
            }.also { inFlightFirstBatch = it }
        }
        return deferred.await()
    }

    suspend fun refreshFromNetwork(): Resource<Unit> =
        fetchBatch(target = Constants.INITIAL_PAGE_TARGET, replace = false)

    suspend fun loadMore(): Resource<Unit> {
        if (exhausted) return Resource.Success(Unit)
        return fetchBatch(target = Constants.LOAD_MORE_BATCH_TARGET, replace = false)
    }

    private suspend fun fetchBatch(target: Int, replace: Boolean): Resource<Unit> {
        if (replace) {
            nextOffset = 0
            totalResults = Int.MAX_VALUE
            exhausted = false
        } else if (nextOffset == 0) {

            val count = dao.count()
            if (count > 0) {

                nextOffset = count.coerceAtLeast(nextOffset)
            }
        }

        val result = mutableListOf<CharacterDto>()
        var requests = 0

        try {
            withTimeoutOrNull(Constants.BATCH_LOAD_TIMEOUT_MILLIS) {

                while (result.size < target &&
                    nextOffset < totalResults &&
                    requests < Constants.MAX_REQUESTS_PER_BATCH
                ) {
                    val response = api.getCharacters(offset = nextOffset)
                    requests++
                    totalResults = response.numberOfTotalResults
                    if (response.results.isEmpty()) break
                    result += response.results.filter {
                        it.publisher?.id == Constants.MARVEL_PUBLISHER_ID ||
                        it.publisher?.name?.contains("Marvel", ignoreCase = true) == true
                    }
                    nextOffset += Constants.CHARACTERS_PAGE_SIZE
                }
            }
        } catch (e: Exception) {

            if (result.isEmpty()) {
                return Resource.Error(e.message ?: "Falha ao buscar personagens da Comic Vine", e)
            }
        }

        if (result.isEmpty() && requests == 0) {
            return Resource.Error("A Comic Vine demorou demais pra responder. Tente de novo em instantes.")
        }

        if (nextOffset >= totalResults) exhausted = true

        val entities = result
            .distinctBy { it.id }
            .filter { !it.name.isNullOrBlank() }
            .map { it.toEntity() }

        if (replace) {
            dao.replaceAll(entities)
        } else if (entities.isNotEmpty()) {
            dao.insertAll(entities)
        }
        return Resource.Success(Unit)
    }

    suspend fun searchRemote(query: String): Resource<Int> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Resource.Success(0)
        return try {
            val response = withTimeoutOrNull(Constants.SEARCH_REMOTE_TIMEOUT_MILLIS) {
                api.searchCharacters(filter = "name:$trimmed")
            } ?: return Resource.Error("A Comic Vine demorou demais pra responder essa busca. Tente de novo.")

            if (response.statusCode != 1) {
                return Resource.Error(
                    "Comic Vine recusou a busca (status ${response.statusCode}: ${response.error})."
                )
            }

            val entities = response.results
                .filter {
                    it.publisher?.id == Constants.MARVEL_PUBLISHER_ID ||
                        it.publisher?.name?.contains("Marvel", ignoreCase = true) == true
                }
                .distinctBy { it.id }
                .filter { !it.name.isNullOrBlank() }
                .map { it.toEntity() }

            if (entities.isNotEmpty()) {
                dao.insertAll(entities)
            }
            Resource.Success(entities.size)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Falha ao buscar na Comic Vine", e)
        }
    }

    private fun CharacterDto.toEntity(): CharacterEntity {
        val appearances = countOfIssueAppearances ?: 0
        val category = CharacterCategorizer.categorize(name ?: "")
        val stats = PowerCalculator.calculateStats(id, appearances, category, deck, description)
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
            isTopRanked = false,
            cachedAtMillis = System.currentTimeMillis()
        )
    }

}
