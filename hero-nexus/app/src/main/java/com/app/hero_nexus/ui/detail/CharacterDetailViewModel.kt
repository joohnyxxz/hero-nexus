package com.app.hero_nexus.ui.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.hero_nexus.data.local.toDomain
import com.app.hero_nexus.data.model.Character
import com.app.hero_nexus.data.repository.CharacterRepository
import com.app.hero_nexus.data.repository.UserRepository
import kotlinx.coroutines.launch

class CharacterDetailViewModel(
    private val characterRepository: CharacterRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _character = MutableLiveData<Character?>()
    val character: LiveData<Character?> = _character

    fun load(characterId: Int, uid: String?) {
        viewModelScope.launch {
            val entity = characterRepository.getCached(characterId) ?: return@launch
            val state = uid?.let { runCatching { userRepository.getCharacterState(it, characterId) }.getOrNull() }
            _character.value = entity.toDomain(state ?: com.app.hero_nexus.data.model.UserCharacterState())
        }
    }
}
