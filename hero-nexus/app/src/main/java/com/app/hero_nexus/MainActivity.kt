package com.app.hero_nexus

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.app.hero_nexus.ui.auth.LoginActivity
import com.app.hero_nexus.ui.collection.CollectionActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tela de abertura: mostra a marca por um instante e decide para onde ir
 * (seção 6.2 - "Login -> Coleção. O progresso do usuário será carregado a partir do banco de dados").
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as HeroNexusApp
        lifecycleScope.launch {
            delay(600)
            val destination = if (app.userRepository.isLoggedIn) {
                Intent(this@MainActivity, CollectionActivity::class.java)
            } else {
                Intent(this@MainActivity, LoginActivity::class.java)
            }
            startActivity(destination)
            finish()
        }
    }
}
