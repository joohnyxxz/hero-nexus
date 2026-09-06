package com.app.hero_nexus.ui.battle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.app.hero_nexus.R
import com.app.hero_nexus.data.model.Character

/**
 * Renderiza a arena de batalha num Canvas simples (lutadores continuam círculos
 * coloridos com nome e barra de vida -- sprites reais ficam pra v3), com o CENÁRIO
 * usando arte real: pack CC0 "Background Elements" da Kenney (kenney.nl), recolorido
 * em duotone pra bater com a paleta "Comic Battle" (vermelho/marinho/dourado/tinta).
 *
 * Seção "fluxo contínuo" (feedback 31/08 -- "igual Golden Axe e Streets of Rage, você anda e a
 * câmera segue"): todo mundo (jogador, inimigos, caixas) vive em coordenada de MUNDO
 * (`Fighter.x`/`DestructibleObject.x`). Aqui a gente subtrai `engine.cameraX` na hora de
 * desenhar pra converter em posição de TELA -- é a única responsabilidade nova desta classe
 * em relação à câmera. O cenário (céu/chão) desenha em camadas com parallax, escorregando
 * um pouco mais devagar que o mundo, pra dar sensação de profundidade enquanto rola.
 *
 * O loop de jogo roda com postOnAnimation.
 */
class BattleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var engine: BattleEngine? = null
    private var started = false
    private var running = false
    private var lastFrameNs = 0L

    private val cratePaint = Paint().apply { color = Color.parseColor("#9C6A3C") }
    private val crateShadePaint = Paint().apply { color = Color.parseColor("#5E3E22") }
    private val crateHighlightPaint = Paint().apply { color = Color.parseColor("#C98F52"); isAntiAlias = true }
    private val crateRivetPaint = Paint().apply { color = Color.parseColor("#2B1B0E"); isAntiAlias = true }
    private val crateOutlinePaint = Paint().apply {
        color = Color.parseColor("#2B1B0E"); style = Paint.Style.STROKE; strokeWidth = 2.5f; isAntiAlias = true
    }
    private val barrelPaint = Paint().apply { color = Color.parseColor("#6B4622") }
    private val barrelBandPaint = Paint().apply { color = Color.parseColor("#2B1B0E") }
    private val barrelHighlightPaint = Paint().apply { color = Color.parseColor("#8A5C34"); isAntiAlias = true }
    private val barrelCapPaint = Paint().apply { color = Color.parseColor("#4A2F16"); isAntiAlias = true }
    private val barrelOutlinePaint = Paint().apply {
        color = Color.parseColor("#1D1006"); style = Paint.Style.STROKE; strokeWidth = 2.5f; isAntiAlias = true
    }
    // Fragmentos da animação de quebra (rodada 13, feedback 01/09: "uma animação quando
    // quebrar, tipo tremer e virar estilhaços") -- reaproveita os tons de madeira/metal já
    // usados nas caixas/barris, só que soltos.
    private val shardLightPaint = Paint().apply { color = Color.parseColor("#C98F52"); isAntiAlias = true }
    private val shardDarkPaint = Paint().apply { color = Color.parseColor("#2B1B0E"); isAntiAlias = true }
    private val playerPaint = Paint().apply { color = Color.parseColor("#FFC400") }
    private val playerStrokePaint = Paint().apply {
        color = Color.parseColor("#7A1414"); style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val enemyPaint = Paint().apply { color = Color.parseColor("#E62429") }
    private val bossPaint = Paint().apply { color = Color.parseColor("#8C1418") }
    private val bossStrokePaint = Paint().apply {
        color = Color.parseColor("#FFC400"); style = Paint.Style.STROKE; strokeWidth = 6f
    }
    // Miniboss (rodada 13): tom violeta pra ficar claramente diferente do capanga comum
    // (vermelho) e do vilão final (vinho + contorno dourado grosso), sem competir com nenhum
    // dos dois -- lido rápido em movimento, igual em Streets of Rage.
    private val minibossPaint = Paint().apply { color = Color.parseColor("#5E2E8C") }
    private val minibossStrokePaint = Paint().apply {
        color = Color.parseColor("#B14BF4"); style = Paint.Style.STROKE; strokeWidth = 4.5f
    }
    private val namePaint = Paint().apply {
        color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val healthBgPaint = Paint().apply { color = Color.parseColor("#40000000") }
    private val healthFgPaint = Paint().apply { color = Color.parseColor("#3CCB6E") }
    private val healthFgLowPaint = Paint().apply { color = Color.parseColor("#E62429") }

    // --------------------------------------------------------------------------- cenário/arena

    private val floorPaint = Paint().apply { color = Color.parseColor("#20141A") }
    private val horizonLinePaint = Paint().apply {
        color = Color.parseColor("#FFC400"); alpha = 130; strokeWidth = 3f
    }

    // Fallback só usado se os bitmaps do cenário não carregarem por algum motivo (recurso
    // ausente/corrompido) -- mantém o app funcional mesmo assim.
    private val fallbackPillarPaint = Paint().apply { color = Color.parseColor("#171B24") }
    private val fallbackPillarCapPaint = Paint().apply { color = Color.parseColor("#FFC400"); alpha = 160 }
    private val fallbackPillarRimPaint = Paint().apply {
        color = Color.parseColor("#E62429"); alpha = 90; style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private var fallbackSkyShader: LinearGradient? = null
    private var fallbackSkyShaderHeight = -1

    private val advanceArrowPaint = Paint().apply { color = Color.parseColor("#FFC400"); isAntiAlias = true }
    private val advanceArrowStroke = Paint().apply {
        color = Color.parseColor("#120A08"); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true
    }
    private val specialBurstPaint = Paint().apply {
        color = Color.parseColor("#FFC400"); style = Paint.Style.STROKE; isAntiAlias = true
    }

    // Cenário real (Kenney "Background Elements", CC0, recolorido na paleta do app). Três
    // variantes possíveis (castelo/templo/floresta -- seção "variedade de cenário", feedback
    // 31/08: "só existe um fundo de batalha"); a batalha atual usa uma delas, fixa, sorteada
    // por vilão em BattleEngine.sceneryVariant. Duas camadas cada: céu+skyline e chão
    // (cerca/árvores em silhueta), ambas roladas em parallax conforme a câmera.
    private var skyBitmap: Bitmap? = null
    private var groundBitmap: Bitmap? = null
    private var loadedVariant = -1
    private val sceneryMatrix = Matrix()

    private fun ensureSceneryLoaded(variant: Int) {
        if (loadedVariant == variant) return
        loadedVariant = variant
        val (skyRes, groundRes) = when (variant) {
            1 -> R.drawable.img_battle_scenery_temple_sky to R.drawable.img_battle_scenery_temple_ground
            2 -> R.drawable.img_battle_scenery_forest_sky to R.drawable.img_battle_scenery_forest_ground
            else -> R.drawable.img_battle_scenery_sky to R.drawable.img_battle_scenery_ground
        }
        skyBitmap = runCatching { BitmapFactory.decodeResource(resources, skyRes) }.getOrNull()
        groundBitmap = runCatching { BitmapFactory.decodeResource(resources, groundRes) }.getOrNull()
    }

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

    /** Dispara o especial (pulso de 1 tick -- botão dedicado, seção "especial"). */
    fun triggerSpecial() {
        engine?.specialRequested = true
    }

    /** Come o que tá guardado no slot de comida (pulso de 1 tick -- botão dedicado). */
    fun triggerUseFood() {
        engine?.useFoodRequested = true
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
        drawScenery(canvas)

        val e = engine ?: return
        if (!started) return
        val camX = e.cameraX
        val nowMs = System.currentTimeMillis()

        e.destructibles.forEach { obj ->
            val sx = obj.x - camX
            if (sx < -60f || sx > width + 60f) return@forEach
            if (!obj.broken) {
                drawDestructible(canvas, sx, obj.y, obj.kind)
            } else {
                val elapsed = nowMs - obj.brokenAtMs
                if (elapsed < BREAK_ANIM_DURATION_MS) {
                    drawBreakAnimation(canvas, sx, obj.y, obj.kind, obj.shards, elapsed)
                }
            }
        }

        e.enemies.filter { it.isAlive }.forEach { enemy ->
            val sx = enemy.x - camX
            if (sx < -90f || sx > width + 90f) return@forEach
            val radius = when {
                enemy.isBoss -> 56f
                enemy.isMiniboss -> 42f
                else -> 30f
            }
            val paint = when {
                enemy.isBoss -> bossPaint
                enemy.isMiniboss -> minibossPaint
                else -> enemyPaint
            }
            canvas.drawCircle(sx, enemy.y, radius, paint)
            if (enemy.isBoss) canvas.drawCircle(sx, enemy.y, radius, bossStrokePaint)
            if (enemy.isMiniboss) canvas.drawCircle(sx, enemy.y, radius, minibossStrokePaint)
            drawHealthBar(canvas, sx, enemy.y - radius - 18f, radius * 2, enemy.healthRatio)
            canvas.drawText(enemy.displayName, sx, enemy.y - radius - 24f, namePaint)
        }

        val player = e.player
        val psx = player.x - camX
        canvas.drawCircle(psx, player.y, 32f, playerPaint)
        canvas.drawCircle(psx, player.y, 32f, playerStrokePaint)
        drawHealthBar(canvas, psx, player.y - 32f - 18f, 64f, player.healthRatio)
        canvas.drawText(player.character.name, psx, player.y - 32f - 24f, namePaint)

        drawSpecialBurst(canvas, psx, player.y, e.specialEffectAtMs)

        if (e.showAdvanceHint) drawAdvanceArrow(canvas)
    }

    /** Cenário real (arte CC0 da Kenney, recolorida na paleta da logo), agora em camadas de
     * parallax que escorregam conforme `cameraX` -- pra dar sensação de mundo largo e contínuo
     * em vez de um fundo estático (seção "fluxo contínuo"). */
    private fun drawScenery(canvas: Canvas) {
        if (width <= 0 || height <= 0) {
            canvas.drawColor(Color.parseColor("#101A35"))
            return
        }
        val e = engine
        ensureSceneryLoaded(e?.sceneryVariant ?: 0)
        val cameraX = e?.cameraX ?: 0f
        val horizonY = height * BattleEngine.HORIZON_Y_RATIO
        val sky = skyBitmap
        val ground = groundBitmap

        if (sky != null) {
            drawParallaxLayer(canvas, sky, 0f, horizonY, cameraX, SKY_PARALLAX)
        } else {
            drawFallbackSky(canvas, horizonY)
        }

        canvas.drawLine(0f, horizonY, width.toFloat(), horizonY, horizonLinePaint)
        canvas.drawRect(0f, horizonY, width.toFloat(), height.toFloat(), floorPaint)

        if (ground != null) {
            // faixa de cerca/árvores em silhueta sobreposta bem perto do horizonte, pra dar
            // profundidade no chão sem mexer no degradê do céu -- escorrega um pouco mais rápido
            // que o céu (mais perto da câmera).
            val stripH = (horizonY * 0.55f).coerceAtLeast(50f)
            val top = horizonY - stripH + 10f
            drawParallaxLayer(canvas, ground, top, top + stripH, cameraX, GROUND_PARALLAX)
        }
    }

    /** Desenha [bmp] escalado pra altura [bottom]-[top] e ladrilhado horizontalmente, deslocado
     * por `cameraX * parallaxFactor` -- camadas com fator menor (céu) andam mais devagar que o
     * mundo, dando sensação de profundidade enquanto a câmera rola (técnica clássica de scroller
     * 2D, tipo Golden Axe/Streets of Rage). */
    private fun drawParallaxLayer(
        canvas: Canvas,
        bmp: Bitmap,
        top: Float,
        bottom: Float,
        cameraX: Float,
        parallaxFactor: Float
    ) {
        val destH = bottom - top
        if (destH <= 0f || bmp.height <= 0) return
        val scale = destH / bmp.height
        val scaledW = bmp.width * scale
        if (scaledW <= 1f) return

        val rawOffset = -(cameraX * parallaxFactor) % scaledW
        var x = if (rawOffset > 0f) rawOffset - scaledW else rawOffset

        canvas.save()
        canvas.clipRect(0f, top, width.toFloat(), bottom)
        while (x < width) {
            sceneryMatrix.reset()
            sceneryMatrix.setScale(scale, scale)
            sceneryMatrix.postTranslate(x, top)
            canvas.drawBitmap(bmp, sceneryMatrix, null)
            x += scaledW
        }
        canvas.restore()
    }

    private fun drawFallbackSky(canvas: Canvas, horizonY: Float) {
        if (fallbackSkyShader == null || fallbackSkyShaderHeight != height) {
            fallbackSkyShader = LinearGradient(
                0f, 0f, 0f, horizonY,
                Color.parseColor("#0B1330"), Color.parseColor("#2A1420"),
                Shader.TileMode.CLAMP
            )
            fallbackSkyShaderHeight = height
        }
        val skyPaint = Paint().apply { shader = fallbackSkyShader }
        canvas.drawRect(0f, 0f, width.toFloat(), horizonY, skyPaint)

        val pillarWidth = 34f
        val spacing = 150f
        var x = 0f
        while (x < width) {
            val top = horizonY * 0.22f
            canvas.drawRect(x, top, x + pillarWidth, horizonY, fallbackPillarPaint)
            canvas.drawRect(x, top, x + pillarWidth, top + 10f, fallbackPillarCapPaint)
            canvas.drawLine(x + pillarWidth, top, x + pillarWidth, horizonY, fallbackPillarRimPaint)
            x += spacing
        }
    }

    /** Caixa/barril com sombra, realce, contorno e reforços -- ainda são formas geométricas
     * (não sprite), mas rodada 13 (feedback 01/09: "os desenho de caixas e barrils deveria ser
     * melhor codado") ganharam mais leitura de objeto de verdade: a caixa tem ripas em X e
     * rebites nos 4 cantos (reforço estrutural, não só uma cruz reta), o barril ganhou tampas
     * elípticas no topo/base (lê como cilindro, não retângulo arredondado) e os dois têm um
     * contorno escuro fechando a silhueta. Recebe [sx] já convertido pra coordenada de TELA
     * (mundo menos a câmera). */
    private fun drawDestructible(canvas: Canvas, sx: Float, sy: Float, kind: DestructibleObject.Kind) {
        val half = 22f
        if (kind == DestructibleObject.Kind.CAIXA) {
            canvas.drawRoundRect(sx - half, sy - half + 4f, sx + half, sy + half + 4f, 6f, 6f, crateShadePaint)
            canvas.drawRoundRect(sx - half, sy - half, sx + half, sy + half, 6f, 6f, cratePaint)
            canvas.drawRoundRect(sx - half + 5f, sy - half + 5f, sx + half - 5f, sy + half - 5f, 3f, 3f, crateHighlightPaint)
            // Ripas em X (reforço estrutural da caixa -- mais "engenhado" que uma cruz reta).
            canvas.drawLine(sx - half + 4f, sy - half + 4f, sx + half - 4f, sy + half - 4f, crateRivetPaint)
            canvas.drawLine(sx - half + 4f, sy + half - 4f, sx + half - 4f, sy - half + 4f, crateRivetPaint)
            canvas.drawRoundRect(sx - half, sy - half, sx + half, sy + half, 6f, 6f, crateOutlinePaint)
            // Rebites nos 4 cantos.
            for (dx in listOf(-1f, 1f)) {
                for (dy in listOf(-1f, 1f)) {
                    canvas.drawCircle(sx + dx * (half - 6f), sy + dy * (half - 6f), 2.4f, crateRivetPaint)
                }
            }
        } else {
            val capH = 6f
            canvas.drawRoundRect(sx - half, sy - half + 4f, sx + half, sy + half + 4f, half, half, crateShadePaint)
            canvas.drawRoundRect(sx - half, sy - half, sx + half, sy + half, half * 0.4f, half * 0.4f, barrelPaint)
            canvas.drawRoundRect(sx - half + 6f, sy - half + 3f, sx - half + 12f, sy + half - 3f, 3f, 3f, barrelHighlightPaint)
            canvas.drawRect(sx - half, sy - half + 4f, sx + half, sy - half + 9f, barrelBandPaint)
            canvas.drawRect(sx - half, sy - 2.5f, sx + half, sy + 2.5f, barrelBandPaint)
            canvas.drawRect(sx - half, sy + half - 9f, sx + half, sy + half - 4f, barrelBandPaint)
            // Tampas elípticas no topo/base -- dá leitura de cilindro em pé, não retângulo.
            canvas.drawOval(sx - half, sy - half - capH * 0.5f, sx + half, sy - half + capH * 0.7f, barrelCapPaint)
            canvas.drawOval(sx - half, sy + half - capH * 0.7f, sx + half, sy + half + capH * 0.5f, barrelCapPaint)
            canvas.drawRoundRect(sx - half, sy - half, sx + half, sy + half, half * 0.4f, half * 0.4f, barrelOutlinePaint)
        }
    }

    /** Animação de quebra (rodada 13, feedback 01/09: "uma animação quando quebrar, tipo tremer
     * e virar estilhaços"): fase 1 (0-[SHAKE_DURATION_MS]ms) a caixa/barril treme no lugar; fase
     * 2 (até [BREAK_ANIM_DURATION_MS]ms) os estilhaços voam pra fora em leque e somem com um
     * fade, puxados por uma "gravidade" leve. [elapsedMs] é sempre < BREAK_ANIM_DURATION_MS
     * (quem chama já filtra isso), então não precisa clampar o fim aqui. */
    private fun drawBreakAnimation(
        canvas: Canvas,
        sx: Float,
        sy: Float,
        kind: DestructibleObject.Kind,
        shards: List<ShardSpec>,
        elapsedMs: Long
    ) {
        if (elapsedMs < SHAKE_DURATION_MS) {
            val shakeAmount = 5f * (1f - elapsedMs.toFloat() / SHAKE_DURATION_MS)
            val jitter = if ((elapsedMs / 26L) % 2 == 0L) shakeAmount else -shakeAmount
            drawDestructible(canvas, sx + jitter, sy, kind)
            return
        }
        val t = ((elapsedMs - SHAKE_DURATION_MS).toFloat() /
            (BREAK_ANIM_DURATION_MS - SHAKE_DURATION_MS)).coerceIn(0f, 1f)
        val alpha = ((1f - t) * 255f).toInt().coerceIn(0, 255)
        shards.forEachIndexed { i, shard ->
            val rad = Math.toRadians(shard.angle.toDouble())
            val dist = shard.speed * t
            val px = sx + (Math.cos(rad) * dist).toFloat()
            val py = sy + (Math.sin(rad) * dist).toFloat() + t * t * 50f // gravidade leve
            val paint = if (i % 2 == 0) shardLightPaint else shardDarkPaint
            paint.alpha = alpha
            canvas.save()
            canvas.rotate(shard.spin * t, px, py)
            canvas.drawRect(px - shard.size / 2f, py - shard.size / 2f, px + shard.size / 2f, py + shard.size / 2f, paint)
            canvas.restore()
        }
    }

    private fun drawAdvanceArrow(canvas: Canvas) {
        val cx = width - 46f
        val cy = height * 0.5f
        val size = 34f
        advanceArrowPaint.alpha = 235
        val path = Path().apply {
            moveTo(cx - size * 0.6f, cy - size)
            lineTo(cx + size * 0.6f, cy)
            lineTo(cx - size * 0.6f, cy + size)
            close()
        }
        canvas.drawPath(path, advanceArrowPaint)
        canvas.drawPath(path, advanceArrowStroke)
    }

    /** Anel dourado se expandindo e sumindo -- efeito visual rápido do golpe especial. */
    private fun drawSpecialBurst(canvas: Canvas, cx: Float, cy: Float, effectAtMs: Long) {
        if (effectAtMs <= 0L) return
        val elapsed = System.currentTimeMillis() - effectAtMs
        if (elapsed > SPECIAL_EFFECT_DURATION_MS) return
        val progress = (elapsed / SPECIAL_EFFECT_DURATION_MS.toFloat()).coerceIn(0f, 1f)
        val radius = 40f + progress * 220f
        specialBurstPaint.strokeWidth = 10f * (1f - progress) + 2f
        specialBurstPaint.alpha = ((1f - progress) * 255).toInt()
        canvas.drawCircle(cx, cy, radius, specialBurstPaint)
    }

    private fun drawHealthBar(canvas: Canvas, centerX: Float, topY: Float, width: Float, ratio: Float) {
        val left = centerX - width / 2
        val right = centerX + width / 2
        canvas.drawRect(left, topY, right, topY + 8f, healthBgPaint)
        val fg = if (ratio > 0.3f) healthFgPaint else healthFgLowPaint
        canvas.drawRect(left, topY, left + width * ratio.coerceIn(0f, 1f), topY + 8f, fg)
    }

    companion object {
        private const val SKY_PARALLAX = 0.35f
        private const val GROUND_PARALLAX = 0.75f
        private const val SPECIAL_EFFECT_DURATION_MS = 420L

        // Animação de quebra de caixa/barril (rodada 13): tempo tremendo + tempo estilhaçando.
        private const val SHAKE_DURATION_MS = 110L
        private const val BREAK_ANIM_DURATION_MS = 480L
    }
}
