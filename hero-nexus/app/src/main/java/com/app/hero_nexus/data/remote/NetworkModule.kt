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

/**
 * Rodada 15, parte 58 (07/10/2026): bug real de verdade, confirmado por teste -- a MESMA URL
 * (com os MESMOS parametros, copiada direto do Logcat) devolvia JSON valido e instantaneo num
 * navegador comum, mas dentro do app, rodando no emulador, a chamada ficava presa ate o teto de
 * tempo (nem o connectTimeout nem o readTimeout do OkHttp abaixo chegavam a disparar -- o
 * cancelamento sempre vinha do timeout do PROPRIO coroutine, nunca de uma excecao do OkHttp).
 * Isso e a assinatura classica de um problema conhecido do emulador Android: a rede virtual dele
 * tem suporte a IPv6 quebrado -- o DNS do sistema devolve um endereco IPv6 "valido", o emulador
 * tenta se conectar por ele, e o pacote simplesmente nunca chega a lugar nenhum (sem RST, sem
 * erro, sem nada), entao nem o fallback automatico pra IPv4 (Happy Eyeballs) nem os timeouts
 * configurados abaixo tem chance de agir a tempo. Forcar IPv4 aqui elimina esse caminho quebrado.
 */
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
            // Rodada 15, parte 31 (30/09/2026): restaurado -- este .protocols(...) (fixando
            // HTTP/1.1) tinha sido adicionado na parte 10 pra corrigir requisições à Comic Vine
            // que ficavam presas/canceladas no aparelho real do usuário (negociação HTTP/2 do
            // OkHttp lidando mal com a rede dele), e sumiu sem explicação depois da passagem do
            // Gemini por este arquivo -- provavelmente a causa raiz real por trás de "não sei se
            // ele cagou a listagem de cards".
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

    // Cliente separado pra tradução: não precisa (nem deve) mandar o User-Agent específico
    // da Comic Vine pra um serviço diferente.
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
            .client(translationHttpClient) // sem User-Agent especifico, mesmo client simples da traducao
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PexelsApi::class.java)
    }
}
