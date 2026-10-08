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

class MainActivity : AppCompatActivity() {

    private val holdDurationMs = 3200L
    private val implosionDurationMs = 550L
    private val explosionDurationMs = 2000L

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

            val prefetch = if (loggedIn) {
                async { runCatching { app.characterRepository.ensureFirstBatch() } }
            } else {
                null
            }

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

    private fun playExplosion() {
        val growInterpolator = AccelerateInterpolator()
        val fadeInterpolator = DecelerateInterpolator(1.8f)

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
