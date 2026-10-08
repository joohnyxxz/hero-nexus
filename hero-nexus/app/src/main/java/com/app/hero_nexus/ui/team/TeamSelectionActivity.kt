package com.app.hero_nexus.ui.team

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.GridLayoutManager
import com.app.hero_nexus.R
import com.app.hero_nexus.databinding.ActivityTeamSelectionBinding
import com.app.hero_nexus.databinding.ItemTeamSlotBinding
import com.app.hero_nexus.ui.battle.BattleActivity
import com.app.hero_nexus.ui.common.MainNavActivity
import com.app.hero_nexus.util.Constants
import com.app.hero_nexus.util.dpToPx
import com.app.hero_nexus.util.loadCharacterImage
import com.app.hero_nexus.util.visibleIf

class TeamSelectionActivity : MainNavActivity() {

    private lateinit var binding: ActivityTeamSelectionBinding
    private lateinit var adapter: TeamCharacterAdapter

    private val viewModel: TeamViewModel by viewModels {
        viewModelFactory {
            initializer { TeamViewModel(app.characterRepository, app.userRepository) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeamSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar(
            binding.topBar.textScreenTitle,
            binding.topBar.textCoins,
            binding.topBar.textLevel,
            binding.topBar.buttonLogout,
            R.string.team_title
        )
        setupBottomNav(binding.bottomNav, R.id.nav_team)

        adapter = TeamCharacterAdapter(
            isSelected = { c -> viewModel.selectedIds.value.orEmpty().contains(c.id) },
            onClick = { c ->
                val current = viewModel.selectedIds.value.orEmpty()
                if (!current.contains(c.id) && current.size >= Constants.MAX_TEAM_SIZE) {
                    Toast.makeText(this, R.string.team_max_reached, Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.toggle(c.id)
                }
            }
        )
        val spanCount = (resources.displayMetrics.widthPixels / dpToPx(150f)).coerceAtLeast(2)
        binding.recyclerCharacters.layoutManager = GridLayoutManager(this, spanCount)
        binding.recyclerCharacters.adapter = adapter

        binding.buttonSaveTeam.setOnClickListener {
            app.userRepository.currentUid?.let { viewModel.save(it) }
        }
        binding.buttonStartBattle.setOnClickListener { startBattle() }

        viewModel.unlockedCharacters.observe(this) { adapter.submitList(it) }
        viewModel.selectedIds.observe(this) { ids ->
            adapter.notifyDataSetChanged()
            binding.textSlotsFilled.text = getString(R.string.team_slots_filled, ids.size)
            renderSlots(ids)
        }
        viewModel.saved.observe(this) {
            Toast.makeText(this, R.string.team_saved, Toast.LENGTH_SHORT).show()
        }

        app.userRepository.currentUid?.let { viewModel.start(it) }
    }

    private fun renderSlots(ids: List<Int>) {
        binding.layoutSlots.removeAllViews()
        for (i in 0 until Constants.MAX_TEAM_SIZE) {
            val slotBinding = ItemTeamSlotBinding.inflate(layoutInflater, binding.layoutSlots, true)
            val characterId = ids.getOrNull(i)
            if (characterId != null) {
                val character = viewModel.characterById(characterId)
                slotBinding.imageSlot.visibleIf(true)
                slotBinding.textSlotNumber.visibleIf(false)
                slotBinding.textSlotOrder.visibleIf(true)
                slotBinding.textSlotOrder.text = "${i + 1}º"
                character?.let { slotBinding.imageSlot.loadCharacterImage(it.imageUrl) }
                slotBinding.root.setOnClickListener { viewModel.toggle(characterId) }
            } else {
                slotBinding.imageSlot.visibleIf(false)
                slotBinding.textSlotOrder.visibleIf(false)
                slotBinding.textSlotNumber.visibleIf(true)
                slotBinding.textSlotNumber.text = "${i + 1}"
                slotBinding.root.setOnClickListener(null)
            }
        }
    }

    private fun startBattle() {
        val ids = viewModel.selectedIds.value.orEmpty()
        if (ids.size != Constants.MAX_TEAM_SIZE) {
            Toast.makeText(this, R.string.team_need_full, Toast.LENGTH_SHORT).show()
            return
        }
        app.userRepository.currentUid?.let { viewModel.save(it) }
        startActivity(BattleActivity.newIntent(this, ids.toIntArray()))

        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}
