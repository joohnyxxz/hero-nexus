package com.app.hero_nexus.data.repository

import com.app.hero_nexus.data.local.TranslationDao
import com.app.hero_nexus.data.local.TranslationEntity
import com.app.hero_nexus.data.remote.MyMemoryApi
import com.app.hero_nexus.util.Constants

/**
 * Traduz textos em inglês (vindos da Comic Vine) para PT-BR via MyMemory, com cache local em
 * Room — seção "tradução automática via API + cache" do documento revisado.
 *
 * Importante: NUNCA deixa uma exceção subir pra UI. Se a API de tradução cair ou a cota diária
 * gratuita acabar, devolve o texto original em inglês em vez de quebrar a tela — o app continua
 * 100% usável, só sem tradução naquele texto específico até a cota renovar.
 */
class TranslationRepository(
    private val api: MyMemoryApi,
    private val dao: TranslationDao
) {
    suspend fun translate(sourceText: String): String {
        val trimmed = sourceText.trim()
        if (trimmed.isBlank()) return sourceText

        val hash = trimmed.hashCode()
        dao.get(hash)?.let { return it.translatedText }

        val toSend = trimmed.take(Constants.TRANSLATION_MAX_CHARS)
        return try {
            val response = api.translate(
                text = toSend,
                langPair = Constants.TRANSLATION_LANG_PAIR,
                contactEmail = Constants.TRANSLATION_CONTACT_EMAIL
            )
            val translated = response.responseData?.translatedText
            if (translated.isNullOrBlank() || translated.contains("MYMEMORY WARNING", ignoreCase = true)) {
                // Cota do dia estourou ou resposta vazia: mostra o original em inglês e NÃO
                // guarda em cache, pra tentar de novo mais tarde (ex: quando a cota renovar).
                sourceText
            } else {
                dao.upsert(TranslationEntity(hash, toSend, translated, System.currentTimeMillis()))
                translated
            }
        } catch (e: Exception) {
            sourceText
        }
    }

    /** Traduz uma lista (ex: chips de poderes), item a item, reaproveitando o cache. */
    suspend fun translateList(items: List<String>): List<String> = items.map { translate(it) }
}
