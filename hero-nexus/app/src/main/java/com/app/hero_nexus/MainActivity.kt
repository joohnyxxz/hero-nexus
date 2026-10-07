package com.app.hero_nexus

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
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
 * Rodada 15, parte 13 (30/09/2026): dispara o carregamento de verdade da primeira leva de
 * personagens em paralelo à barra visual.
 *
 * Rodada 15, parte 67 (07/10/2026): primeira versão da animação, um anel só, giro mais implosão.
 *
 * Rodada 15, parte 68 (07/10/2026): 3 camadas girando independente, implosão seguida de explosão
 * com flash servindo de transição de verdade.
 *
 * Rodada 15, parte 69 (07/10/2026): usuário testou no aparelho e reportou 3 problemas reais.
 * 1) Depois da barra chegar a 100%, a tela ficava travada esperando mais um tanto antes da
 *    explosão -- causa raiz: o código fazia `delay(holdDurationMs)` e DEPOIS, separado,
 *    `withTimeoutOrNull(SPLASH_PREFETCH_TIMEOUT_MS) { prefetch.await() }`, então o pior caso
 *    SOMAVA os dois tempos (holdDurationMs + até 6s de rede) em vez de esperar só o maior dos
 *    dois. Trocado por um `launch { delay(holdDurationMs) }` em paralelo com o await do prefetch,
 *    seguido de `.join()` nesse job -- agora o tempo total de espera é o MAIOR entre os dois,
 *    nunca a soma. A barra também para de fingir que terminou: ela anima só até 96% durante a
 *    espera (não 100%), e só sobe pra 100% de verdade bem na hora que os dois terminam, com um
 *    "snap" rápido de 120ms, emendando direto na implosão sem nenhuma pausa escondida depois.
 * 2) A explosão "travava" um pouco -- 3 ImageViews de vetor grandes sendo escalados/girados ao
 *    mesmo tempo é pesado pra renderizar (vetor não é bitmap, reconstrói o path todo frame).
 *    Ligado android:layerType hardware nessas views pro tempo da animação, cacheia como bitmap
 *    enquanto anima em vez de re-rasterizar o vetor a cada frame. A escala máxima da explosão
 *    também foi reduzida (não ia mais caber inteira na tela mesmo, growth exagerado só desperdiça
 *    o efeito saindo da área visível).
 * 3) Dava pra ler "HERO NEXUS" ainda no instante do corte pra próxima tela -- a rotação/escala da
 *    explosão usava um AccelerateInterpolator (começa devagar), e esse MESMO interpolator também
 *    estava no alpha, então o fade ficava devagar demais no começo, continuando bem visível
 *    quando a troca de Activity já tinha acontecido. Separado agora: a escala continua acelerando
 *    (sensação de estouro), mas o alpha de cada elemento tem seu PRÓPRIO animator com
 *    DecelerateInterpolator (cai rápido logo no início) -- a wordmark já está quase invisível bem
 *    antes do pico do flash. O flash em si também mudou de cor (era reward_gold, a MESMA cor do
 *    texto, então não "lavava" o texto por completo de propósito) pra branco, e subiu de pico
 *    (0.92 -> 0.97), um flash de verdade embaça tudo embaixo, sem depender só do alpha do texto.
 *
 * Rodada 15, parte 69: posição vertical também subiu de bias 0.5 pra 0.62 em activity_main.xml
 * (usuário reportou a composição grudando no topo/cortada pela área da câmera/notch).
 */
class MainActivity : AppCompatActivity() {

    /** Quanto tempo a barra passa girando/subindo até 96% antes de considerar "pronto" --
     * deliberadamente generoso agora (era 2600ms), dá tempo de apreciar o giro independente das
     * 3 camadas. Nunca mais SOMA com o tempo de rede, ver comentário da classe. */
    private val holdDurationMs = 3200L
    private val implosionDurationMs = 550L
    private val explosionDurationMs = 2000L

    /** Quando, dentro da explosão, a troca de Activity acontece de verdade -- tem que cair perto
     * do pico do flash E depois que o alpha (que agora cai rápido, ver DecelerateInterpolator
     * dedicado) já apagou quase tudo, não no fim da animação inteira. */
    private val navigateDuringExplosionDelayMs = 700L

    private lateinit var riftA: ImageView
    private lateinit var riftB: ImageView
    private lateinit var riftEmbers: ImageView
    private lateinit var glow: View
    private lateinit var wordmark: ImageView
    private lateinit var flash: View
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var loadingLabel: String

    private var spinA: ObjectAnimator? = null
    private var spinB: ObjectAnimator? = null
    private var spinEmbers: ObjectAnimator? = null
    private var pulseEmbers: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progress)
        loadingText = findViewById(R.id.textLoading)
        riftA = findViewById(R.id.imageRiftA)
        riftB = findViewById(R.id.imageRiftB)
        riftEmbers = findViewById(R.id.imageRiftEmbers)
        glow = findViewById(R.id.glowLogo)
        wordmark = findViewById(R.id.textLogo)
        flash = findViewById(R.id.viewSplashFlash)
        loadingLabel = getString(R.string.splash_loading)

        // Rodada 15, parte 69: cacheia cada camada animada como bitmap (hardware layer) enquanto
        // a splash estiver na tela, em vez de deixar o vetor ser re-rasterizado a cada frame de
        // rotação/escala -- é essa re-rasterização repetida, com 3 vetores grandes ao mesmo
        // tempo, que fazia a explosão "travar" um pouco num aparelho real.
        for (view in listOf<View>(riftA, riftB, riftEmbers, glow, wordmark)) {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        startContinuousMotion()

        val progressAnimator = ValueAnimator.ofInt(0, 96).apply {
            duration = holdDurationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim -> updateProgress(anim.animatedValue as Int) }
        }
        progressAnimator.start()

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

            // Rodada 15, parte 69: minHoldJob roda em paralelo com o await do prefetch logo
            // abaixo (nunca em sequência) -- o .join() no final só espera o que ainda faltar do
            // maior dos dois tempos, nunca a soma.
            val minHoldJob = launch { delay(holdDurationMs) }
            prefetch?.let {
                withTimeoutOrNull(Constants.SPLASH_PREFETCH_TIMEOUT_MS) { it.await() }
            }
            minHoldJob.join()

            snapProgressTo100()

            stopContinuousMotion()
            playImplosion()
            delay(implosionDurationMs)
            playExplosion()
            delay(navigateDuringExplosionDelayMs)
            navigateTo(loggedIn)
        }
    }

    private fun updateProgress(pct: Int) {
        progressBar.progress = pct
        loadingText.text = "$loadingLabel $pct%"
    }

    /** Sobe rápido de onde a barra estava (96%, ou menos se o prefetch foi mais rápido que
     * holdDurationMs) até 100% de verdade -- feedback claro de "pronto", sem pausa escondida
     * depois dele (ver comentário da classe, problema 1). */
    private suspend fun snapProgressTo100() {
        val start = progressBar.progress
        if (start >= 100) return
        val snapDurationMs = 120L
        ValueAnimator.ofInt(start, 100).apply {
            duration = snapDurationMs
            addUpdateListener { anim -> updateProgress(anim.animatedValue as Int) }
        }.start()
        delay(snapDurationMs)
    }

    /** Fase 1: giro contínuo e independente de cada camada, começa junto com a tela. */
    private fun startContinuousMotion() {
        spinA = ObjectAnimator.ofFloat(riftA, View.ROTATION, 0f, 360f).apply {
            duration = 5200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        spinB = ObjectAnimator.ofFloat(riftB, View.ROTATION, 0f, -360f).apply {
            duration = 6800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        spinEmbers = ObjectAnimator.ofFloat(riftEmbers, View.ROTATION, 0f, 360f).apply {
            duration = 9000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        pulseEmbers = ObjectAnimator.ofPropertyValuesHolder(
            riftEmbers,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f)
        ).apply {
            duration = 1300L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun stopContinuousMotion() {
        spinA?.cancel()
        spinB?.cancel()
        spinEmbers?.cancel()
        pulseEmbers?.cancel()
    }

    /** Fase 2: tudo encolhe e acelera o giro até quase sumir, tipo mola comprimindo antes de
     * soltar -- a wordmark também encolhe um pouco, antecipação antes da explosão. */
    private fun playImplosion() {
        val riftAImplode = ObjectAnimator.ofPropertyValuesHolder(
            riftA,
            PropertyValuesHolder.ofFloat(View.ROTATION, riftA.rotation, riftA.rotation + 260f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.05f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.05f)
        )
        val riftBImplode = ObjectAnimator.ofPropertyValuesHolder(
            riftB,
            PropertyValuesHolder.ofFloat(View.ROTATION, riftB.rotation, riftB.rotation - 300f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.05f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.05f)
        )
        val embersImplode = ObjectAnimator.ofPropertyValuesHolder(
            riftEmbers,
            PropertyValuesHolder.ofFloat(View.ROTATION, riftEmbers.rotation, riftEmbers.rotation + 200f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, riftEmbers.scaleX, 0.05f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, riftEmbers.scaleY, 0.05f)
        )
        val glowImplode = ObjectAnimator.ofPropertyValuesHolder(
            glow,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.25f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.25f)
        )
        val wordmarkSquash = ObjectAnimator.ofPropertyValuesHolder(
            wordmark,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.9f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.9f)
        )

        // Limpa o HUD durante a implosão para focar na energia
        val hudFade = ObjectAnimator.ofFloat(1f, 0f).apply {
            addUpdateListener { anim ->
                val alpha = anim.animatedValue as Float
                progressBar.alpha = alpha
                loadingText.alpha = alpha
            }
        }

        AnimatorSet().apply {
            duration = implosionDurationMs
            interpolator = AccelerateInterpolator(1.3f)
            playTogether(riftAImplode, riftBImplode, embersImplode, glowImplode, wordmarkSquash, hudFade)
            start()
        }
    }

    /** Fase 3: estoura pra fora e some rápido, enquanto o flash de tela cheia pisca -- é esse
     * flash que disfarça o corte real pra próxima tela (ver navigateDuringExplosionDelayMs).
     *
     * Rodada 15, parte 69: escala/rotação (crescimento) e alpha (sumiço) de cada elemento agora
     * são DOIS animators separados, cada um com seu próprio interpolator -- a escala acelera
     * (AccelerateInterpolator, sensação de estouro) mas o alpha cai rápido logo no início
     * (DecelerateInterpolator), bem antes do pico do flash. Antes os dois compartilhavam o mesmo
     * AccelerateInterpolator, o que deixava o alpha devagar demais no começo e ainda dava pra ler
     * a wordmark no instante do corte de tela. */
    private fun playExplosion() {
        val growInterpolator = AccelerateInterpolator()
        val fadeInterpolator = DecelerateInterpolator(1.8f)

        // Rodada 15, parte 69: cada elemento sai da implosão num tamanho DIFERENTE (riftA/B e
        // embers encolhem até 0.05, glow só até 0.25, a wordmark só até 0.9, ver playImplosion()),
        // então o "de onde" de cada escala aqui tem que ler o valor REAL de cada view
        // (target.scaleX, igual já se fazia com a rotação) em vez de supor 0.05 pra tudo -- um
        // valor fixo errado causava um "pulo" visual (a view encolhia de repente até 0.05 só pra
        // já voltar a crescer) bem no instante em que a explosão começava.
        fun moveAnimator(target: View, rotationDelta: Float, scaleTo: Float): ObjectAnimator {
            val fromScale = target.scaleX
            val holders = if (rotationDelta != 0f) {
                arrayOf(
                    PropertyValuesHolder.ofFloat(View.ROTATION, target.rotation, target.rotation + rotationDelta),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, fromScale, scaleTo),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, fromScale, scaleTo)
                )
            } else {
                arrayOf(
                    PropertyValuesHolder.ofFloat(View.SCALE_X, fromScale, scaleTo),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, fromScale, scaleTo)
                )
            }
            return ObjectAnimator.ofPropertyValuesHolder(target, *holders).apply {
                duration = explosionDurationMs
                interpolator = growInterpolator
            }
        }

        fun fadeAnimator(target: View): ObjectAnimator {
            return ObjectAnimator.ofFloat(target, View.ALPHA, 1f, 0f).apply {
                duration = explosionDurationMs
                interpolator = fadeInterpolator
            }
        }

        val riftAMove = moveAnimator(riftA, 240f, 2.0f)
        val riftAFade = fadeAnimator(riftA)
        val riftBMove = moveAnimator(riftB, -260f, 2.1f)
        val riftBFade = fadeAnimator(riftB)
        val embersMove = moveAnimator(riftEmbers, 0f, 1.9f)
        val embersFade = fadeAnimator(riftEmbers)
        val glowMove = moveAnimator(glow, 0f, 1.9f)
        val glowFade = fadeAnimator(glow)
        val wordmarkMove = moveAnimator(wordmark, 0f, 1.25f)
        val wordmarkFade = fadeAnimator(wordmark)

        val flashUp = ObjectAnimator.ofFloat(flash, View.ALPHA, 0f, 0.97f).apply {
            duration = (explosionDurationMs * 0.35f).toLong()
            interpolator = DecelerateInterpolator()
        }
        val flashDown = ObjectAnimator.ofFloat(flash, View.ALPHA, 0.97f, 0f).apply {
            duration = (explosionDurationMs * 0.65f).toLong()
            interpolator = AccelerateInterpolator()
        }
        val flashSequence = AnimatorSet().apply { playSequentially(flashUp, flashDown) }

        AnimatorSet().apply {
            playTogether(
                riftAMove, riftAFade, riftBMove, riftBFade, embersMove, embersFade,
                glowMove, glowFade, wordmarkMove, wordmarkFade, flashSequence
            )
            start()
        }
    }

    private fun navigateTo(loggedIn: Boolean) {
        val destination = if (loggedIn) {
            Intent(this, CollectionActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        startActivity(destination)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.splash_fade_in, R.anim.splash_fade_out_hold)
        finish()
    }
}
