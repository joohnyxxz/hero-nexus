package com.app.hero_nexus.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.hero_nexus.data.repository.UserRepository
import com.app.hero_nexus.util.Resource
import kotlinx.coroutines.launch

class AuthViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _state = MutableLiveData<Resource<Unit>>()
    val state: LiveData<Resource<Unit>> = _state

    fun login(emailOrUsername: String, password: String) {
        _state.value = Resource.Loading
        viewModelScope.launch {
            val result = userRepository.login(emailOrUsername, password)
            _state.value = result.fold(
                onSuccess = { Resource.Success(Unit) },
                onFailure = { Resource.Error(it.message ?: "Não foi possível entrar.") }
            )
        }
    }

    fun register(username: String, email: String, password: String) {
        _state.value = Resource.Loading
        viewModelScope.launch {
            val result = userRepository.register(username, email, password)
            _state.value = result.fold(
                onSuccess = { Resource.Success(Unit) },
                onFailure = { Resource.Error(it.message ?: "Não foi possível cadastrar.") }
            )
        }
    }
}
