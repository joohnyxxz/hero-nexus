package com.app.hero_nexus.util

/**
 * Wrapper simples para representar o estado de uma operação assíncrona (rede/banco) na UI.
 */
sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()
    data object Loading : Resource<Nothing>()
}
