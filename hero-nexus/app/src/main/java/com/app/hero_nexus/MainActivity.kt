package com.app.hero_nexus

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.app.hero_nexus.ui.auth.LoginActivity
import com.app.hero_nexus.ui.collection.CollectionActivity
import com.app.hero_nexus.util.Constants
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tela de abertura: mostra a marca por um instante e decide para onde ir
 * (secao 6.2 - "Login -> Colecao. O progresso do usuario sera carregado a partir do banco de dados").
 *
 * Rodada 15, parte 13 (30/09/2026): pedido do usuário ("a splash realmente esperar carregar o
 * back com os cards") -- a splash volta a disparar o carregamento de verdade da primeira leva de
 * personagens (CharacterRepository.ensureFirstBatch(), rápida de propósito: só
 * Constants.INITIAL_PAGE_TARGET personagens, não a coleção inteira) em paralelo à barra visual, e
 * só navega depois que as duas coisas terminarem -- a Coleção abre já com cards de verdade na
 * tela, em vez de abrir vazia e só então começar a buscar. Um teto extra
 * (Constants.SPLASH_PREFETCH_TIMEOUT_MS) garante que uma rede ruim/fora do ar nunca trava a
 * splash pra sempre -- nesse caso ela segue em frente mesmo assim, e a Coleção lida com cache
 * vazio/erro do jeito de sempre.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress)
        val loadingText = findViewById<android.widget.TextView>(R.id.textLoading)
        val loadingLabel = getString(R.string.splash_loading)
        val splashDurationMs = 900L

        val animator = ValueAnimator.ofInt(0, 100).apply {
            duration = splashDurationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val pct = anim.animatedValue as Int
                progressBar.progress = pct
                loadingText.text = "$loadingLabel $pct%"
            }
        }
        animator.start()

        val app = application as HeroNexusApp
        lifecycleScope.launch {
            val loggedIn = app.userRepository.isLoggedIn

            // Só vale a pena pré-carregar personagem se o destino for a Coleção. Disparado em
            // paralelo (async), não bloqueia a barra visual acima.
            val prefetch = if (loggedIn) {
                async { runCatching { app.characterRepository.ensureFirstBatch() } }
            } else {
                null
            }

            delay(splashDurationMs) // garante a barra visual sempre completar até 100%

            prefetch?.let {
                withTimeoutOrNull(Constants.SPLASH_PREFETCH_TIMEOUT_MS) { it.await() }
            }

            val destination = if (loggedIn) {
                Intent(this@MainActivity, CollectionActivity::class.java)
            } else {
                Intent(this@MainActivity, LoginActivity::class.java)
            }
            startActivity(destination)
            finish()
        }
    }
}
