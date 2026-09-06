package com.app.hero_nexus.data.repository

import com.app.hero_nexus.BuildConfig
import com.app.hero_nexus.data.remote.PexelsApi

/**
 * Busca uma imagem de fundo temática via Pexels pra telas que hoje são só cor sólida (seção 27
 * do documento: outra API só quando resolve um problema real — aqui, o app "parecendo genérico").
 *
 * Cache simples em memória (dura a vida do processo do app): a mesma busca não bate na API de
 * novo a cada vez que a tela abre. Se a chave estiver vazia ou a busca falhar por qualquer
 * motivo (sem internet, cota da Pexels, etc.), devolve null e quem chamou mantém o fundo sólido
 * de sempre — isso nunca deve derrubar uma tela.
 */
class BackgroundRepository(private val api: PexelsApi) {

    private val cache = mutableMapOf<String, String?>()

    suspend fun findBackgroundUrl(query: String): String? {
        val key = BuildConfig.PEXELS_API_KEY
        if (key.isBlank()) return null
        cache[query]?.let { return it }
        if (cache.containsKey(query)) return null // já buscamos e não achamos nada

        return runCatching {
            val response = api.search(apiKey = key, query = query, perPage = 5)
            val url = response.photos.firstOrNull()?.src?.let { it.portrait ?: it.large2x ?: it.large }
            cache[query] = url
            url
        }.getOrNull()
    }
}
