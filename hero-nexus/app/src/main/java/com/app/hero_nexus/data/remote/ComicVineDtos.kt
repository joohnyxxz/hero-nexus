package com.app.hero_nexus.data.remote

import com.google.gson.annotations.SerializedName

data class ComicVineDetailResponseDto(
    @SerializedName("status_code") val statusCode: Int,
    @SerializedName("error") val error: String,
    @SerializedName("results") val results: CharacterDto?
)

data class ComicVineListResponseDto(
    @SerializedName("status_code") val statusCode: Int,
    @SerializedName("error") val error: String,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("number_of_page_results") val numberOfPageResults: Int,
    @SerializedName("number_of_total_results") val numberOfTotalResults: Int,
    @SerializedName("results") val results: List<CharacterDto>
)

data class CharacterDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("real_name") val realName: String?,
    @SerializedName("deck") val deck: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("aliases") val aliases: String?,
    @SerializedName("image") val image: ImageDto?,
    @SerializedName("publisher") val publisher: PublisherRefDto?,
    @SerializedName("powers") val powers: List<NamedRefDto>?,
    @SerializedName("count_of_issue_appearances") val countOfIssueAppearances: Int?,
    @SerializedName("site_detail_url") val siteDetailUrl: String?,
    @SerializedName("first_appeared_in_issue") val firstAppearedInIssue: NamedRefDto?
)

data class ImageDto(
    @SerializedName("icon_url") val iconUrl: String?,
    @SerializedName("medium_url") val mediumUrl: String?,
    @SerializedName("screen_url") val screenUrl: String?,
    @SerializedName("small_url") val smallUrl: String?,
    @SerializedName("super_url") val superUrl: String?,
    @SerializedName("thumb_url") val thumbUrl: String?,
    @SerializedName("original_url") val originalUrl: String?
)

data class PublisherRefDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("name") val name: String?
)

data class NamedRefDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("name") val name: String?
)
