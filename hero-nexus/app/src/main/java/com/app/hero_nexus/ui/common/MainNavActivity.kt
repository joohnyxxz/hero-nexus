package com.app.hero_nexus.ui.common

import android.content.Intent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.app.hero_nexus.HeroNexusApp
import com.app.hero_nexus.R
import com.app.hero_nexus.ui.auth.LoginActivity
import com.app.hero_nexus.ui.chests.ChestsActivity
import com.app.hero_nexus.ui.collection.CollectionActivity
import com.app.hero_nexus.ui.missions.MissionsActivity
import com.app.hero_nexus.ui.store.SkinStoreActivity
import com.app.hero_nexus.ui.team.TeamSelectionActivity
import com.app.hero_nexus.util.applyStatusBarTopInset
import com.app.hero_nexus.util.formatCoins
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

abstract class MainNavActivity : AppCompatActivity() {

    protected val app: HeroNexusApp get() = application as HeroNexusApp

    protected fun setupTopBar(
        textTitle: TextView,
        textCoins: TextView,
        textLevel: TextView,
        buttonLogout: ImageButton,
        @StringRes titleRes: Int
    ) {
        textTitle.setText(titleRes)

        (textTitle.parent as? View)?.applyStatusBarTopInset()
        buttonLogout.setOnClickListener {
            app.userRepository.logout()
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
        refreshProfileHeader(textCoins, textLevel)
    }

    protected fun refreshProfileHeader(textCoins: TextView, textLevel: TextView) {
        val uid = app.userRepository.currentUid ?: return
        lifecycleScope.launch {
            runCatching { app.userRepository.getProfile(uid) }.getOrNull()?.let { profile ->
                textCoins.text = profile.coins.formatCoins()
                textLevel.text = getString(R.string.level_short, profile.level)
            }
        }
    }

    protected fun setupBottomNav(bottomNav: BottomNavigationView, currentItemId: Int) {
        bottomNav.selectedItemId = currentItemId
        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == currentItemId) return@setOnItemSelectedListener true
            val target = when (item.itemId) {
                R.id.nav_collection -> CollectionActivity::class.java
                R.id.nav_team -> TeamSelectionActivity::class.java
                R.id.nav_missions -> MissionsActivity::class.java
                R.id.nav_chests -> ChestsActivity::class.java
                R.id.nav_store -> SkinStoreActivity::class.java
                else -> null
            }
            if (target != null) {
                startActivity(Intent(this, target))
                overridePendingTransition(0, 0)
                finish()
            }
            true
        }
    }
}
