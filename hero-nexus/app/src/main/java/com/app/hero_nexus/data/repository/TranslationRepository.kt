package com.app.hero_nexus.data.repository

import com.app.hero_nexus.data.local.TranslationDao
import com.app.hero_nexus.data.local.TranslationEntity
import com.app.hero_nexus.data.remote.MyMemoryApi
import com.app.hero_nexus.util.Constants

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

                sourceText
            } else {
                dao.upsert(TranslationEntity(hash, toSend, translated, System.currentTimeMillis()))
                translated
            }
        } catch (e: Exception) {
            sourceText
        }
    }

    suspend fun translateList(items: List<String>): List<String> = items.map { translate(it) }
}
