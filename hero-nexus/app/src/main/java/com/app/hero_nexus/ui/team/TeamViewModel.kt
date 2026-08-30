package com.app.hero_nexus.ui.team

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.hero_nexus.data.local.toDomain
import com.app.hero_nexus.data.model.Character
import com.app.hero_nexus.data.repository.CharacterRepository
import com.app.hero_nexus.data.repository.UserRepository
import com.app.hero_nexus.util.Constants
import kotlinx.coroutines.launch

class TeamViewModel(
    private val characterRepository: CharacterRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _unlockedCharacters = MutableLiveData<List<Character>>()
    val unlockedCharacters: LiveData<List<Character>> = _unlockedCharacters

    private val _selectedIds = MutableLiveData<List<Int>>(emptyList())
    val selectedIds: LiveData<List<Int>> = _selectedIds

    private val _saved = MutableLiveData<Boolean>()
    val saved: LiveData<Boolean> = _saved

    fun start(uid: String) {
        viewModelScope.launch {
            val entities = characterRepository.getAllCached()
            val states = runCatching { userRepository.getCharacterStates(uid) }.getOrDefault(emptyMap())
            val unlocked = entities
                .filter { states[it.comicVineId]?.unlocked == true }
                .map { it.toDomain(states[it.comicVineId]!!) }
            _unlockedCharacters.value = unlocked

            val team = runCatching { userRepository.getTeam(uid) }.getOrNull()
            _selectedIds.value = team?.characterIds?.filter { id -> unlocked.any { it.id == id } } ?: emptyList()
        }
    }

    fun toggle(characterId: Int) {
        val current = _selectedIds.value.orEmpty()
        _selectedIds.value = if (current.contains(characterId)) {
            current - characterId
        } else if (current.size < Constants.MAX_TEAM_SIZE) {
            current + characterId
        } else {
            current
        }
    }

    fun save(uid: String) {
        viewModelScope.launch {
            userRepository.saveTeam(uid, _selectedIds.value.orEmpty())
            _saved.value = true
        }
    }

    fun characterById(id: Int): Character? = _unlockedCharacters.value?.firstOrNull { it.id == id }
}
