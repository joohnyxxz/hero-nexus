package com.app.hero_nexus.data.remote

import com.app.hero_nexus.BuildConfig
import com.app.hero_nexus.util.Constants
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("character/4005-{id}/")
    suspend fun getCharacterDetail(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String = BuildConfig.COMIC_VINE_API_KEY,
        @Query("format") format: String = "json",
        @Query("field_list") fieldList: String = "id,powers"
    ): ComicVineDetailResponseDto

    companion object {

        const val FIELD_LIST = "id,name,real_name,deck,description,aliases,image,publisher," +
            "powers,count_of_issue_appearances,site_detail_url,first_appeared_in_issue"
    }
}
