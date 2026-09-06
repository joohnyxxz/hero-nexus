package com.app.hero_nexus.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * MyMemory Translation API — tradução automática sem precisar de API key.
 * Escolhida de propósito: depois do incidente de segurança (chave da Comic Vine vazada no
 * histórico do git), decidimos não introduzir mais nenhum segredo pra guardar/vazar.
 * Doc: https://mymemory.translated.net/doc/spec.php
 */
interface MyMemoryApi {
    @GET("get")
    suspend fun translate(
        @Query("q") text: String,
        @Query("langpair") langPair: String,
        @Query("de") contactEmail: String? = null
    ): MyMemoryResponse
}

data class MyMemoryResponse(
    val responseData: MyMemoryData?,
    // Gson lê tanto número quanto string aqui (a API às vezes manda 200 como int, às vezes como texto).
    val responseStatus: String? = null
)

data class MyMemoryData(
    val translatedText: String?
)
