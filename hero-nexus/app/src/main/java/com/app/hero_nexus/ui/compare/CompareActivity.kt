package com.app.hero_nexus.ui.compare

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.app.hero_nexus.HeroNexusApp
import com.app.hero_nexus.R
import com.app.hero_nexus.data.local.CharacterEntity
import com.app.hero_nexus.databinding.ActivityCompareBinding
import com.app.hero_nexus.databinding.ItemCompareAttributeRowBinding
import com.app.hero_nexus.util.loadCharacterImage
import kotlinx.coroutines.launch

class CompareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompareBinding
    private val app get() = application as HeroNexusApp

    private var allCharacters: List<CharacterEntity> = emptyList()
    private var left: CharacterEntity? = null
    private var right: CharacterEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backBar.buttonBack.setOnClickListener { finish() }
        binding.backBar.textBackBarTitle.setText(R.string.compare_title)

        binding.buttonPickLeft.setOnClickListener { showPicker { left = it; render() } }
        binding.buttonPickRight.setOnClickListener { showPicker { right = it; render() } }

        lifecycleScope.launch {
            allCharacters = app.characterRepository.getAllCached()
            val preselectedId = intent.getIntExtra(EXTRA_FIRST_CHARACTER_ID, -1)
            left = allCharacters.firstOrNull { it.comicVineId == preselectedId }
            render()
        }
    }

    private fun showPicker(onPicked: (CharacterEntity) -> Unit) {
        val names = allCharacters.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.collection_title)
            .setItems(names) { _, which -> onPicked(allCharacters[which]) }
            .show()
    }

    private fun render() {
        left?.let { binding.imageLeft.loadCharacterImage(it.imageUrl) }
        right?.let { binding.imageRight.loadCharacterImage(it.imageUrl) }
        binding.buttonPickLeft.text = left?.name ?: getString(R.string.compare_pick_first)
        binding.buttonPickRight.text = right?.name ?: getString(R.string.compare_pick_second)

        binding.layoutCompareRows.removeAllViews()
        val l = left
        val r = right
        if (l == null || r == null) return

        val rows = listOf(
            Triple(getString(R.string.attribute_strength), l.strength, r.strength),
            Triple(getString(R.string.attribute_speed), l.speed, r.speed),
            Triple(getString(R.string.attribute_intelligence), l.intelligence, r.intelligence),
            Triple(getString(R.string.attribute_durability), l.durability, r.durability),
            Triple(getString(R.string.attribute_power), l.power, r.power),
            Triple(getString(R.string.attribute_combat), l.combat, r.combat)
        )
        rows.forEach { (label, leftValue, rightValue) ->
            val rowBinding = ItemCompareAttributeRowBinding.inflate(layoutInflater, binding.layoutCompareRows, true)
            rowBinding.textLabel.text = label
            rowBinding.textValueLeft.text = leftValue.toString()
            rowBinding.textValueRight.text = rightValue.toString()
            val winnerColor = getColor(R.color.success_green)
            val normalColor = getColor(R.color.text_white)
            rowBinding.textValueLeft.setTextColor(if (leftValue >= rightValue) winnerColor else normalColor)
            rowBinding.textValueRight.setTextColor(if (rightValue >= leftValue) winnerColor else normalColor)
        }
    }

    companion object {
        private const val EXTRA_FIRST_CHARACTER_ID = "extra_first_character_id"
        fun newIntent(context: Context, firstCharacterId: Int) =
            Intent(context, CompareActivity::class.java).putExtra(EXTRA_FIRST_CHARACTER_ID, firstCharacterId)
    }
}
