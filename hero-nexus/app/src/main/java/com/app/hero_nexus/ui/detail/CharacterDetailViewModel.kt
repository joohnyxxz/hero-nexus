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

            val rawAbout = domain.deck?.stripHtml()?.takeIf { it.isNotBlank() }
                ?: domain.description?.stripHtml()?.truncateWithEllipsis(Constants.ABOUT_MAX_CHARS)
                ?: ""
            _aboutText.value = rawAbout.ifBlank { "Sem informações adicionais disponíveis." }
            _powers.value = domain.powers

            if (rawAbout.isNotBlank()) {
                launch {
                    val translated = runCatching { translationRepository.translate(rawAbout) }.getOrDefault(rawAbout)
                    _aboutText.value = translated
                }
            }

            launch {

                var powersToShow = domain.powers
                if (powersToShow.isEmpty()) {
                    val fetched = runCatching { characterRepository.fetchAndCachePowers(characterId) }.getOrNull()
                    if (!fetched.isNullOrEmpty()) {
                        powersToShow = fetched
                        _powers.value = powersToShow
                    }
                }
                if (powersToShow.isNotEmpty()) {
                    val translatedPowers = runCatching { translationRepository.translateList(powersToShow) }
                        .getOrDefault(powersToShow)
                    _powers.value = translatedPowers
                }
            }
        }
    }
}
