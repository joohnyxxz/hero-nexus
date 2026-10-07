package com.app.hero_nexus.data.remote

import com.app.hero_nexus.BuildConfig
import com.app.hero_nexus.util.Constants
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Ver seção 4 do Documento de Projeto: a Comic Vine API é a fonte principal de personagens.
 * Usamos o recurso "characters" (seção 4.1, plural, pra listar/cachear o catálogo), filtrando
 * pelo publisher da Marvel, e o recurso singular "character/{guid}/" (rodada 15, parte 57) só
 * pra reforçar o campo `powers`, que o recurso plural raramente devolve preenchido.
 */
interface ComicVineApi {

    @GET("characters/")
    suspend fun getCharacters(
        @Query("api_key") apiKey: String = BuildConfig.COMIC_VINE_API_KEY,
        @Query("format") format: String = "json",
        @Query("filter") filter: String = "publisher:${Constants.MARVEL_PUBLISHER_ID}",
        @Query("sort") sort: String = "count_of_issue_appearances:desc",
        @Query("field_list") fieldList: String = FIELD_LIST,
        @Query("limit") limit: Int = Constants.CHARACTERS_PAGE_SIZE,
        @Query("offset") offset: Int = 0
    ): ComicVineListResponseDto

    @GET("characters/")
    suspend fun searchCharacters(
        @Query("api_key") apiKey: String = BuildConfig.COMIC_VINE_API_KEY,
        @Query("format") format: String = "json",
        @Query("filter") filter: String,
        @Query("field_list") fieldList: String = FIELD_LIST,
        @Query("limit") limit: Int = Constants.CHARACTERS_PAGE_SIZE
    ): ComicVineListResponseDto

    /**
     * Recurso SINGULAR (não "characters/", "character/{guid}/" -- guid = "4005-{id}", onde 4005
     * é o resource_type fixo da Comic Vine pra personagens). Diferente do recurso plural, este
     * devolve o campo `powers` de forma confiável pra quase todo personagem -- por isso a tela
     * de detalhe chama isto (via CharacterRepository.fetchAndCachePowers) quando o cache veio
     * sem poderes, em vez de confiar só no que já tá salvo do recurso plural.
     */
    @GET("character/4005-{id}/")
    suspend fun getCharacterDetail(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String = BuildConfig.COMIC_VINE_API_KEY,
        @Query("format") format: String = "json",
        @Query("field_list") fieldList: String = "id,powers"
    ): ComicVineDetailResponseDto

    companion object {
        /** Só pedimos os campos que a aplicação realmente usa (seção 4.2 do documento). */
        const val FIELD_LIST = "id,name,real_name,deck,description,aliases,image,publisher," +
            "powers,count_of_issue_appearances,site_detail_url,first_appeared_in_issue"
    }
}
