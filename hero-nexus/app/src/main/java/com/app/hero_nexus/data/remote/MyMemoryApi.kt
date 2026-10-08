package com.app.hero_nexus.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

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

    val responseStatus: String? = null
)

data class MyMemoryData(
    val translatedText: String?
)
