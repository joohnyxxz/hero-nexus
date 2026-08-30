package com.app.hero_nexus.ui.missions

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.hero_nexus.R
import com.app.hero_nexus.data.model.Mission
import com.app.hero_nexus.data.model.MissionCatalog
import com.app.hero_nexus.data.model.MissionProgress
import com.app.hero_nexus.databinding.ActivityMissionsBinding
import com.app.hero_nexus.ui.common.MainNavActivity
import kotlinx.coroutines.launch

/** Seção 22 do documento — missões simples para o MVP. */
class MissionsActivity : MainNavActivity() {

    private lateinit var binding: ActivityMissionsBinding
    private lateinit var adapter: MissionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar(
            binding.topBar.textScreenTitle,
            binding.topBar.textCoins,
            binding.topBar.textLevel,
            binding.topBar.buttonLogout,
            R.string.missions_title
        )
        setupBottomNav(binding.bottomNav, R.id.nav_missions)

        adapter = MissionAdapter { mission -> claim(mission) }
        binding.recyclerMissions.layoutManager = LinearLayoutManager(this)
        binding.recyclerMissions.adapter = adapter

        loadMissions()
    }

    private fun loadMissions() {
        val uid = app.userRepository.currentUid ?: return
        lifecycleScope.launch {
            // Catálogo agora mora no Firestore (não fica mocado no app) — ver UserRepository.getMissionCatalog.
            val catalog = runCatching { app.userRepository.getMissionCatalog() }.getOrDefault(MissionCatalog.DEFAULTS)
            val progressMap = runCatching { app.userRepository.getMissionProgress(uid) }.getOrDefault(emptyMap())
            val missions = catalog.map { def ->
                Mission(def, progressMap[def.id] ?: MissionProgress(id = def.id))
            }
            adapter.submitList(missions)
        }
    }

    private fun claim(mission: Mission) {
        val uid = app.userRepository.currentUid ?: return
        lifecycleScope.launch {
            app.userRepository.claimMission(uid, mission.definition.id)
            app.userRepository.addXpAndCoins(uid, mission.definition.rewardXp, mission.definition.rewardCoins)
            refreshProfileHeader(binding.topBar.textCoins, binding.topBar.textLevel)
            loadMissions()
        }
    }
}
