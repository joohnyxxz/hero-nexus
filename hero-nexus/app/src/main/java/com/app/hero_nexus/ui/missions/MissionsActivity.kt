package com.app.hero_nexus.ui.missions

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.hero_nexus.R
import com.app.hero_nexus.data.model.Mission
import com.app.hero_nexus.data.model.MissionCatalog
import com.app.hero_nexus.data.model.MissionProgress
import com.app.hero_nexus.databinding.ActivityMissionsBinding
import com.app.hero_nexus.ui.common.MainNavActivity
import kotlinx.coroutines.launch

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
        val uid = app.userRepository.currentUid ?: run {
            Toast.makeText(this, "Sessão expirada — faça login de novo pra ver suas missões.", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val catalogResult = runCatching { app.userRepository.getMissionCatalog() }
            val catalog = catalogResult.getOrDefault(MissionCatalog.DEFAULTS)
            catalogResult.exceptionOrNull()?.let { e ->
                Log.e(TAG, "Falha ao carregar catálogo de missões do Firestore", e)
                Toast.makeText(
                    this@MissionsActivity,
                    "Não consegui carregar as missões do servidor (${e.message ?: "erro desconhecido"}). " +
                        "Mostrando lista padrão — confira as regras do Firestore pra \"missions_catalog\".",
                    Toast.LENGTH_LONG
                ).show()
            }

            val progressResult = runCatching { app.userRepository.getMissionProgress(uid) }
            val progressMap = progressResult.getOrDefault(emptyMap())
            progressResult.exceptionOrNull()?.let { e ->
                Log.e(TAG, "Falha ao carregar progresso de missões", e)
            }

            val missions = catalog.map { def ->
                var progress = progressMap[def.id] ?: MissionProgress(id = def.id)

                val now = System.currentTimeMillis()
                val shouldReset = when (def.category) {
                    "daily" -> MissionCatalog.isDifferentDay(progress.lastResetAt, now)
                    "weekly" -> MissionCatalog.isDifferentWeek(progress.lastResetAt, now)
                    else -> false
                }

                if (shouldReset) {
                    progress = MissionProgress(id = def.id, lastResetAt = now)
                }

                Mission(def, progress)
            }.sortedWith(compareBy({ it.progress.claimed }, { it.definition.category != "daily" }))
            adapter.submitList(missions)
        }
    }

    private fun claim(mission: Mission) {
        val uid = app.userRepository.currentUid ?: return
        lifecycleScope.launch {
            runCatching {
                app.userRepository.claimMission(uid, mission.definition.id)
                app.userRepository.addXpAndCoins(uid, mission.definition.rewardXp, mission.definition.rewardCoins)
            }.onFailure { e ->
                Log.e(TAG, "Falha ao resgatar missão ${mission.definition.id}", e)
                Toast.makeText(
                    this@MissionsActivity,
                    "Não consegui resgatar essa recompensa (${e.message ?: "erro"}). Tente de novo.",
                    Toast.LENGTH_LONG
                ).show()
            }
            refreshProfileHeader(binding.topBar.textCoins, binding.topBar.textLevel)
            loadMissions()
        }
    }

    private companion object {
        const val TAG = "MissionsActivity"
    }
}
