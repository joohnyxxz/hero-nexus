package com.app.hero_nexus.ui.collection

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.hero_nexus.data.local.CharacterEntity
import com.app.hero_nexus.data.local.toDomain
import com.app.hero_nexus.data.model.Character
import com.app.hero_nexus.data.model.CharacterCategory
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

    /** Rodada 15, parte 13 (30/09/2026): estado só de "carregar mais" (scroll até o fim),
     * separado do [refreshState] acima (que é sobre a 1ª leva / pull-to-refresh). */
    private val _loadMoreState = MutableLiveData<Resource<Unit>>()
    val loadMoreState: LiveData<Resource<Unit>> = _loadMoreState

    private var isLoadingMore = false
    val hasMore: Boolean get() = characterRepository.hasMore

    private var lastEntities: List<CharacterEntity> = emptyList()
    private var userStates: Map<Int, UserCharacterState> = emptyMap()

    fun start(uid: String) {
        // Observa o Room desde já: se já tiver cache válido, a tela pinta na hora, sem esperar
        // a leva de rede abaixo (que aí nem precisa rodar, ver ensureFirstBatch).
        viewModelScope.launch {
            characterRepository.observeCollection().collect { entities ->
                lastEntities = entities
                recompute()
            }
        }

        viewModelScope.launch {
            _refreshState.value = Resource.Loading
            _refreshState.value = characterRepository.ensureFirstBatch()

            userStates = runCatching { userRepository.getCharacterStates(uid) }.getOrDefault(emptyMap())
            recompute()

            // Jogador novo (sem NENHUM personagem registrado ainda) começa com alguns já desbloqueados,
            // senão não dá pra montar time nem entrar na batalha (seção 6/11 do documento).
            val cached = characterRepository.getAllCached()
            if (cached.isNotEmpty()) {
                // Starters nunca podem ser vilão (revisão pós-validação rodada 13): categoria
                // VILAO é justamente o que o reset abaixo tranca de novo, e os dois rodam no
                // mesmo start() -- sem esse filtro, uma conta nova podia sortear um vilão como
                // starter e perder aquele slot de time na hora, ficando com menos de
                // MAX_TEAM_SIZE personagens jogáveis.
                val starterIds = cached
                    .filter { it.category != CharacterCategory.VILAO.name }
                    .sortedByDescending { it.countOfIssueAppearances }
                    .take(Constants.STARTER_CHARACTER_COUNT)
                    .map { it.comicVineId }
                runCatching { userRepository.ensureStarterCharacters(uid, starterIds) }

                // Migração única, rodada 13 (feedback 01/09: "tirar todos os viloes que
                // conquistei pra eu começar do 0"). Roda logo depois dos starters -- mesmo
                // padrão de "só mexe uma vez por conta" (a própria função é quem confere a
                // flag antes de fazer qualquer coisa).
                val villainIds = cached
                    .filter { it.category == CharacterCategory.VILAO.name }
                    .map { it.comicVineId }
                runCatching { userRepository.runVillainResetOnceIfNeeded(uid, villainIds) }

                // As duas migrações acima podem ter desbloqueado personagem (starter novo) --
                // busca os estados de novo pra a coleção refletir isso sem precisar de outro evento.
                userStates = runCatching { userRepository.getCharacterStates(uid) }.getOrDefault(userStates)
                recompute()
            }
        }
    }

    /**
     * "Por partes": chamado quando o usuário rola até perto do fim da grade (ver
     * CollectionActivity). Busca mais [Constants.LOAD_MORE_BATCH_TARGET] personagens Marvel
     * novos e acrescenta ao cache -- a lista observada em [characters] cresce sozinha.
     */
    fun loadMore() {
        if (isLoadingMore || !hasMore) return
        isLoadingMore = true
        viewModelScope.launch {
            _loadMoreState.value = Resource.Loading
            _loadMoreState.value = characterRepository.loadMore()
            isLoadingMore = false
        }
    }

    /** Puxar-para-atualizar: refaz a primeira leva com a Comic Vine. */
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
