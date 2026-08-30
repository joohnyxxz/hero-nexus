package com.app.hero_nexus.ui.collection

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.hero_nexus.data.local.CharacterEntity
import com.app.hero_nexus.data.local.toDomain
import com.app.hero_nexus.data.model.Character
import com.app.hero_nexus.data.model.UserCharacterState
import com.app.hero_nexus.data.repository.CharacterRepository
import com.app.hero_nexus.data.repository.UserRepository
import com.app.hero_nexus.util.Constants
import com.app.hero_nexus.util.Resource
import kotlinx.coroutines.launch

class CollectionViewModel(
    private val characterRepository: CharacterRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _characters = MutableLiveData<List<Character>>()
    val characters: LiveData<List<Character>> = _characters

    private val _refreshState = MutableLiveData<Resource<Unit>>()
    val refreshState: LiveData<Resource<Unit>> = _refreshState

    private var lastEntities: List<CharacterEntity> = emptyList()
    private var userStates: Map<Int, UserCharacterState> = emptyMap()

    fun start(uid: String) {
        viewModelScope.launch {
            _refreshState.value = Resource.Loading
            val result = characterRepository.refreshIfNeeded()
            _refreshState.value = result

            // Jogador novo (sem NENHUM personagem registrado ainda) começa com alguns já desbloqueados,
            // senão não dá pra montar time nem entrar na batalha (seção 6/11 do documento).
            val cached = characterRepository.getAllCached()
            if (cached.isNotEmpty()) {
                val starterIds = cached
                    .sortedByDescending { it.countOfIssueAppearances }
                    .take(Constants.STARTER_CHARACTER_COUNT)
                    .map { it.comicVineId }
                runCatching { userRepository.ensureStarterCharacters(uid, starterIds) }
            }

            userStates = runCatching { userRepository.getCharacterStates(uid) }.getOrDefault(emptyMap())

            characterRepository.observeCollection().collect { entities ->
                lastEntities = entities
                recompute()
            }
        }
    }

    fun refresh(uid: String) {
        viewModelScope.launch {
            _refreshState.value = Resource.Loading
            userStates = runCatching { userRepository.getCharacterStates(uid) }.getOrDefault(userStates)
            _refreshState.value = characterRepository.refreshFromNetwork()
            recompute()
        }
    }

    /** Chame depois de desbloquear um personagem (baú/boss) para a coleção refletir na hora. */
    fun refreshUserStatesOnly(uid: String) {
        viewModelScope.launch {
            userStates = runCatching { userRepository.getCharacterStates(uid) }.getOrDefault(userStates)
            recompute()
        }
    }

    private fun recompute() {
        _characters.value = lastEntities.map { entity ->
            entity.toDomain(userStates[entity.comicVineId] ?: UserCharacterState())
        }
    }
}
