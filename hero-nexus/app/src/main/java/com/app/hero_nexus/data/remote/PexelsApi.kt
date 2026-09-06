package com.app.hero_nexus.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Pexels — banco de fotos gratuito (documento original, seção 27: "outras APIs só quando houver
 * necessidade real"). Usado aqui pra dar um fundo temático de verdade em telas que hoje são só
 * cor sólida (ex: login), em vez de tentar desenhar tudo programaticamente.
 *
 * Precisa de uma API key (gratuita, https://www.pexels.com/api/), mas se ela estiver vazia
 * (BuildConfig.PEXELS_API_KEY == "") o app simplesmente não busca fundo — ver
 * BackgroundRepository. Nunca é obrigatória pro app funcionar.
 */
interface PexelsApi {
    @GET("v1/search")
    suspend fun search(
        @Header("Authorization") apiKey: String,
        @Query("query") query: String,
        @Query("per_page") perPage: Int = 5,
        @Query("orientation") orientation: String = "portrait"
    ): PexelsSearchResponse
}

data class PexelsSearchResponse(
    val photos: List<PexelsPhoto> = emptyList()
)

data class PexelsPhoto(
    val id: Long = 0,
    val photographer: String? = null,
    val src: PexelsPhotoSrc? = null
)

data class PexelsPhotoSrc(
    val large2x: String? = null,
    val large: String? = null,
    val portrait: String? = null,
    val original: String? = null
)
