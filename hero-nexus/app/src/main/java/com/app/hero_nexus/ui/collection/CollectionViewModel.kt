package com.app.hero_nexus.ui.collection

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.hero_nexus.data.local.CharacterEntity
import com.app.hero_nexus.data.local.toDomain
import com.app.hero_nexus.data.model.Character
import com.app.hero_nexus.data.model.CharacterCategory
import com.app.hero_nexus.data.model.Team
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

    private val _loadMoreState = MutableLiveData<Resource<Unit>>()
    val loadMoreState: LiveData<Resource<Unit>> = _loadMoreState

    private var isLoadingMore = false
    val hasMore: Boolean get() = characterRepository.hasMore

    private val _remoteSearchState = MutableLiveData<Resource<Int>>()
    val remoteSearchState: LiveData<Resource<Int>> = _remoteSearchState

    private var lastEntities: List<CharacterEntity> = emptyList()
    private var userStates: Map<Int, UserCharacterState> = emptyMap()

    fun start(uid: String) {

        viewModelScope.launch {
            characterRepository.observeCollection().collect { entities ->
                lastEntities = entities
                recompute()
            }
        }

        viewModelScope.launch {
            _refreshState.value = Resource.Loading

            characterRepository.ensureFirstBatch()

            val currentTeam = runCatching { userRepository.getTeam(uid) }.getOrDefault(Team())

            val earlyStates = runCatching { userRepository.getCharacterStates(uid) }.getOrDefault(emptyMap())
            val hasAnyUnlocked = earlyStates.values.any { it.unlocked }

            if (currentTeam.isEmpty || !hasAnyUnlocked) {

                val starterIds = listOf(1443, 1442, 1440)

                val cached = characterRepository.getAllCached()
                val starterNames = mapOf(1443 to "Spider-Man", 1442 to "Captain America", 1440 to "Wolverine")

                starterIds.forEach { id ->
                    if (cached.none { it.comicVineId == id }) {
                        characterRepository.searchRemote(starterNames[id]!!)
                    }
                }

                runCatching { userRepository.ensureStarterCharacters(uid, starterIds) }.onFailure {
                    it.printStackTrace()
                }
            }

            userStates = runCatching { userRepository.getCharacterStates(uid) }.getOrDefault(emptyMap())

            val allCached = characterRepository.getAllCached()
            val villainIds = allCached.filter { it.category == CharacterCategory.VILAO.name }.map { it.comicVineId }
            runCatching { userRepository.runVillainResetOnceIfNeeded(uid, villainIds) }

            userStates = runCatching { userRepository.getCharacterStates(uid) }.getOrDefault(userStates)

            _refreshState.value = Resource.Success(Unit)
            recompute()
        }
    }

    fun loadMore() {
        if (isLoadingMore || !hasMore) return
        isLoadingMore = true
        viewModelScope.launch {
            _loadMoreState.value = Resource.Loading
            _loadMoreState.value = characterRepository.loadMore()
            isLoadingMore = false
        }
    }

    fun searchRemote(query: String) {
        viewModelScope.launch {
            _remoteSearchState.value = Resource.Loading
            _remoteSearchState.value = characterRepository.searchRemote(query)
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
