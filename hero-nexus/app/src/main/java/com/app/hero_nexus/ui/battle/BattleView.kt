package com.app.hero_nexus.ui.battle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.app.hero_nexus.data.model.Character

/**
 * Renderiza a arena de batalha num Canvas simples (sem sprites/assets externos):
 * cada lutador é um círculo colorido com nome e barra de vida, ao estilo de um
 * protótipo de beat 'em up (seção 12). O loop de jogo roda com postOnAnimation.
 */
class BattleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var engine: BattleEngine? = null
    private var started = false
    private var running = false
    private var lastFrameNs = 0L

    private val floorPaint = Paint().apply { color = Color.parseColor("#1A2748") }
    private val cratePaint = Paint().apply { color = Color.parseColor("#8A5A32") }
    private val barrelPaint = Paint().apply { color = Color.parseColor("#5A3D1E") }
    private val playerPaint = Paint().apply { color = Color.parseColor("#2E9BFF") }
    private val enemyPaint = Paint().apply { color = Color.parseColor("#E62429") }
    private val bossPaint = Paint().apply { color = Color.parseColor("#8C1418") }
    private val bossStrokePaint = Paint().apply {
        color = Color.parseColor("#FFC400"); style = Paint.Style.STROKE; strokeWidth = 6f
    }
    private val namePaint = Paint().apply {
        color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val healthBgPaint = Paint().apply { color = Color.parseColor("#40000000") }
    private val healthFgPaint = Paint().apply { color = Color.parseColor("#3CCB6E") }
    private val healthFgLowPaint = Paint().apply { color = Color.parseColor("#E62429") }

    fun setup(team: List<Character>, boss: Character?, listener: BattleListener): BattleEngine {
        val newEngine = BattleEngine(team, boss, listener)
        engine = newEngine
        if (width > 0 && height > 0) {
            newEngine.arenaWidth = width.toFloat()
            newEngine.arenaHeight = height.toFloat()
            newEngine.start(System.currentTimeMillis())
            started = true
            startLoop()
        }
        return newEngine
    }

    fun setMove(x: Float, y: Float) {
        engine?.moveX = x
        engine?.moveY = y
    }

    fun setAttackPressed(pressed: Boolean) {
        engine?.attackRequested = pressed
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val e = engine ?: return
        e.arenaWidth = w.toFloat()
        e.arenaHeight = h.toFloat()
        if (!started && w > 0 && h > 0) {
            e.start(System.currentTimeMillis())
            started = true
            startLoop()
        }
    }

    private fun startLoop() {
        running = true
        lastFrameNs = System.nanoTime()
        postOnAnimation(loopRunnable)
    }

    fun pauseLoop() {
        running = false
    }

    fun resumeLoop() {
        if (started && !running) {
            running = true
            lastFrameNs = System.nanoTime()
            postOnAnimation(loopRunnable)
        }
    }

    private val loopRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val deltaSeconds = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNs = now
            engine?.update(System.currentTimeMillis(), deltaSeconds)
            invalidate()
            if (running) postOnAnimation(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#101A35"))
        canvas.drawRect(0f, height * 0.15f, width.toFloat(), height.toFloat(), floorPaint)

        val e = engine ?: return
        if (!started) return

        e.destructibles.filter { !it.broken }.forEach { obj ->
            val paint = if (obj.kind == DestructibleObject.Kind.CAIXA) cratePaint else barrelPaint
            canvas.drawRoundRect(obj.x - 22f, obj.y - 22f, obj.x + 22f, obj.y + 22f, 6f, 6f, paint)
        }

        e.enemies.filter { it.isAlive }.forEach { enemy ->
            val radius = if (enemy.isBoss) 56f else 30f
            val paint = if (enemy.isBoss) bossPaint else enemyPaint
            canvas.drawCircle(enemy.x, enemy.y, radius, paint)
            if (enemy.isBoss) canvas.drawCircle(enemy.x, enemy.y, radius, bossStrokePaint)
            drawHealthBar(canvas, enemy.x, enemy.y - radius - 18f, radius * 2, enemy.healthRatio)
            canvas.drawText(enemy.displayName, enemy.x, enemy.y - radius - 24f, namePaint)
        }

        val player = e.player
        canvas.drawCircle(player.x, player.y, 32f, playerPaint)
        drawHealthBar(canvas, player.x, player.y - 32f - 18f, 64f, player.healthRatio)
        canvas.drawText(player.character.name, player.x, player.y - 32f - 24f, namePaint)
    }

    private fun drawHealthBar(canvas: Canvas, centerX: Float, topY: Float, width: Float, ratio: Float) {
        val left = centerX - width / 2
        val right = centerX + width / 2
        canvas.drawRect(left, topY, right, topY + 8f, healthBgPaint)
        val fg = if (ratio > 0.3f) healthFgPaint else healthFgLowPaint
        canvas.drawRect(left, topY, left + width * ratio.coerceIn(0f, 1f), topY + 8f, fg)
    }
}
