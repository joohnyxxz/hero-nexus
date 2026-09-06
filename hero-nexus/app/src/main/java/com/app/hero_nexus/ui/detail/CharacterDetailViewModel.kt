package com.app.hero_nexus.ui.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.hero_nexus.data.local.toDomain
import com.app.hero_nexus.data.model.Character
import com.app.hero_nexus.data.model.UserCharacterState
import com.app.hero_nexus.data.repository.CharacterRepository
import com.app.hero_nexus.data.repository.TranslationRepository
import com.app.hero_nexus.data.repository.UserRepository
import com.app.hero_nexus.util.Constants
import com.app.hero_nexus.util.stripHtml
import com.app.hero_nexus.util.truncateWithEllipsis
import kotlinx.coroutines.launch

/**
 * Tela de detalhe do personagem.
 *
 * Separação de dados (documento revisado — "separação entre dados vindos da API e dados
 * nossos"): [character] é sempre o dado cru vindo da Comic Vine (cache Room) + progresso do
 * jogador (Firestore), sem alterações. [aboutText] e [powers] são uma camada de EXIBIÇÃO por
 * cima disso — texto resumido/cortado e traduzido via [TranslationRepository] (com cache) — que
 * nunca sobrescreve o dado original. Mostra o texto em inglês primeiro (cache local, instantâneo)
 * e troca pra PT-BR assim que a tradução chega, sem travar a tela nem exigir loading.
 */
class CharacterDetailViewModel(
    private val characterRepository: CharacterRepository,
    private val userRepository: UserRepository,
    private val translationRepository: TranslationRepository
) : ViewModel() {

    private val _character = MutableLiveData<Character?>()
    val character: LiveData<Character?> = _character

    private val _aboutText = MutableLiveData<String>()
    val aboutText: LiveData<String> = _aboutText

    private val _powers = MutableLiveData<List<String>>(emptyList())
    val powers: LiveData<List<String>> = _powers

    fun load(characterId: Int, uid: String?) {
        viewModelScope.launch {
            val entity = characterRepository.getCached(characterId) ?: return@launch
            val state = uid?.let { runCatching { userRepository.getCharacterState(it, characterId) }.getOrNull() }
            val domain = entity.toDomain(state ?: UserCharacterState())
            _character.value = domain

            // Prioriza o resumo curto (deck); só recorre à description longa se não houver deck —
            // e mesmo assim corta num tamanho exibível (campo "sobre" era longo demais).
            val rawAbout = domain.deck?.stripHtml()?.takeIf { it.isNotBlank() }
                ?: domain.description?.stripHtml()?.truncateWithEllipsis(Constants.ABOUT_MAX_CHARS)
                ?: ""
            _aboutText.value = rawAbout.ifBlank { "Sem informações adicionais disponíveis." }
            _powers.value = domain.powers

            if (rawAbout.isNotBlank()) {
                val translated = runCatching { translationRepository.translate(rawAbout) }.getOrDefault(rawAbout)
                _aboutText.value = translated
            }
            if (domain.powers.isNotEmpty()) {
                val translatedPowers = runCatching { translationRepository.translateList(domain.powers) }
                    .getOrDefault(domain.powers)
                _powers.value = translatedPowers
            }
        }
    }
}
