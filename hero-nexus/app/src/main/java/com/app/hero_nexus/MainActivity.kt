package com.app.hero_nexus

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.app.hero_nexus.ui.auth.LoginActivity
import com.app.hero_nexus.ui.collection.CollectionActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tela de abertura: mostra a marca por um instante e decide para onde ir
 * (secao 6.2 - "Login -> Colecao. O progresso do usuario sera carregado a partir do banco de dados").
 *
 * Rodada 10 (01/09): pedido explicito do usuario ("nao tem aquela barra de carregando bonitinha
 * que vc mostrou") -- o spinner circular padrao do Material virou uma barra horizontal
 * (progress_splash_bar.xml) animada de 0 a 100 com ValueAnimator, sincronizada com o delay real
 * antes de navegar, em vez de um indeterminado generico.
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
            delay(splashDurationMs)
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
