package com.app.hero_nexus.data.repository

import com.app.hero_nexus.BuildConfig
import com.app.hero_nexus.data.remote.PexelsApi

class BackgroundRepository(private val api: PexelsApi) {

    private val cache = mutableMapOf<String, String?>()

    suspend fun findBackgroundUrl(query: String): String? {
        val key = BuildConfig.PEXELS_API_KEY
        if (key.isBlank()) return null
        cache[query]?.let { return it }
        if (cache.containsKey(query)) return null

        return runCatching {
            val response = api.search(apiKey = key, query = query, perPage = 5)
            val url = response.photos.firstOrNull()?.src?.let { it.portrait ?: it.large2x ?: it.large }
            cache[query] = url
            url
        }.getOrNull()
    }
}
