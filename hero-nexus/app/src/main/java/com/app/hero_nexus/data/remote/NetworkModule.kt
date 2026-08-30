package com.app.hero_nexus.data.remote

import com.app.hero_nexus.BuildConfig
import com.app.hero_nexus.util.Constants
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * A Comic Vine bloqueia requisições sem um User-Agent "real" (retorna 403 para
 * clientes anônimos/genéricos), então todo request precisa desse header.
 */
private class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", Constants.COMIC_VINE_USER_AGENT)
            .build()
        return chain.proceed(request)
    }
}

object NetworkModule {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor())
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val comicVineApi: ComicVineApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.COMIC_VINE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ComicVineApi::class.java)
    }
}
