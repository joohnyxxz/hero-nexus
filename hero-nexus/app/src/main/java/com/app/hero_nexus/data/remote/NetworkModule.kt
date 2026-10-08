package com.app.hero_nexus.data.remote

import com.app.hero_nexus.BuildConfig
import com.app.hero_nexus.util.Constants
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

private class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", Constants.COMIC_VINE_USER_AGENT)
            .build()
        return chain.proceed(request)
    }
}

private object Ipv4OnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = Dns.SYSTEM.lookup(hostname)
        val ipv4Only = all.filterIsInstance<Inet4Address>()
        return ipv4Only.ifEmpty { all }
    }
}

object NetworkModule {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(Ipv4OnlyDns)
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

            .protocols(listOf(Protocol.HTTP_1_1))
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

    private val translationHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(Ipv4OnlyDns)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val myMemoryApi: MyMemoryApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.TRANSLATION_BASE_URL)
            .client(translationHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyMemoryApi::class.java)
    }

    val pexelsApi: PexelsApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.PEXELS_BASE_URL)
            .client(translationHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PexelsApi::class.java)
    }
}
