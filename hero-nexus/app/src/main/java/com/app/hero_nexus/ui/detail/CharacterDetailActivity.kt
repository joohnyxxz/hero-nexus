package com.app.hero_nexus.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.hero_nexus.HeroNexusApp
import com.app.hero_nexus.R
import com.app.hero_nexus.data.model.BattleStats
import com.app.hero_nexus.data.model.Character
import com.app.hero_nexus.databinding.ActivityCharacterDetailBinding
import com.app.hero_nexus.databinding.ItemAttributeRowBinding
import com.app.hero_nexus.ui.compare.CompareActivity
import com.app.hero_nexus.util.loadCharacterImage
import com.app.hero_nexus.util.visibleIf
import com.google.android.material.chip.Chip

class CharacterDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCharacterDetailBinding

    private val app get() = application as HeroNexusApp

    private val viewModel: CharacterDetailViewModel by viewModels {
        viewModelFactory {
            initializer {
                CharacterDetailViewModel(app.characterRepository, app.userRepository, app.translationRepository)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCharacterDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backBar.buttonBack.setOnClickListener { finish() }

        val characterId = intent.getIntExtra(EXTRA_CHARACTER_ID, -1)
        viewModel.character.observe(this) { character ->
            if (character != null) render(character)
        }
        // "Sobre" e poderes são exibidos à parte do resto (ver comentário no ViewModel):
        // aparecem em inglês instantaneamente (cache local) e trocam pra PT-BR quando a
        // tradução chega, sem travar a tela.
        viewModel.aboutText.observe(this) { binding.textDescription.text = it }
        viewModel.powers.observe(this) { renderPowers(it) }
        viewModel.load(characterId, app.userRepository.currentUid)
    }

    private fun render(character: Character) {
        binding.backBar.textBackBarTitle.text = character.name
        binding.textName.text = character.name
        binding.textRarity.text = character.rarity.name
        binding.imageCharacter.loadCharacterImage(character.imageUrl)
        binding.textLevelPower.text = if (character.unlocked) {
            getString(R.string.level_short, character.level) + "  •  Poder geral ${character.stats.overallPower}"
        } else {
            "Poder geral ${character.stats.overallPower}"
        }

        binding.textLockedHint.visibleIf(!character.unlocked)

        renderAttributes(character.stats)

        binding.buttonCompare.setOnClickListener {
            startActivity(
                CompareActivity.newIntent(this, character.id)
            )
        }
    }

    private fun renderPowers(powers: List<String>) {
        binding.chipGroupPowers.removeAllViews()
        if (powers.isEmpty()) {
            binding.chipGroupPowers.addView(makeChip(getString(R.string.no_powers_listed)))
        } else {
            powers.forEach { binding.chipGroupPowers.addView(makeChip(it)) }
        }
    }

    private fun renderAttributes(stats: BattleStats) {
        binding.layoutAttributes.removeAllViews()
        val rows = listOf(
            getString(R.string.attribute_strength) to stats.strength,
            getString(R.string.attribute_speed) to stats.speed,
            getString(R.string.attribute_intelligence) to stats.intelligence,
            getString(R.string.attribute_durability) to stats.durability,
            getString(R.string.attribute_power) to stats.power,
            getString(R.string.attribute_combat) to stats.combat
        )
        val inflater = LayoutInflater.from(this)
        rows.forEach { (label, value) ->
            val rowBinding = ItemAttributeRowBinding.inflate(inflater, binding.layoutAttributes, true)
            rowBinding.textAttributeLabel.text = label
            rowBinding.textAttributeValue.text = value.toString()
            rowBinding.progressAttribute.progress = value
        }
    }

    private fun makeChip(text: String): Chip {
        return Chip(this).apply {
            this.text = text
            isClickable = false
            isCheckable = false
            setChipBackgroundColorResource(R.color.card_dark_light)
            setTextColor(getColor(R.color.text_white))
        }
    }

    companion object {
        const val EXTRA_CHARACTER_ID = "extra_character_id"
    }
}
