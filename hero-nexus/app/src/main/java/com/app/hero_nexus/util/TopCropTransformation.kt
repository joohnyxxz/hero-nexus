package com.app.hero_nexus.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

/**
 * Rodada 15, parte 57 (07/10/2026): igual ao CenterCrop padrao do Glide (escala pra preencher
 * largura E altura do container, cortando o excesso), so que ancorado no TOPO em vez do centro.
 *
 * Por que: as artes de personagem da Comic Vine sao retratos (bem mais altos que largos). Na
 * tela de detalhe, o container do cabecalho (260dp) e bem mais baixo que a proporcao real da
 * imagem -- CenterCrop cortava uma fatia enorme dos dois lados (topo E base) igualmente, o que
 * na pratica cortava a CABECA do personagem (perto do topo da imagem original). Usuario reportou
 * isso como "a imagem fica cortada por causa do header". Ancorando no topo, o corte sobra todo
 * embaixo (pernas/torso) em vez do rosto.
 */
class TopCropTransformation : BitmapTransformation() {

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val sourceWidth = toTransform.width
        val sourceHeight = toTransform.height
        if (sourceWidth <= 0 || sourceHeight <= 0 || outWidth <= 0 || outHeight <= 0) return toTransform

        val targetAspect = outWidth.toFloat() / outHeight.toFloat()
        val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()

        val scale: Float
        val dx: Float
        if (sourceAspect > targetAspect) {
            // Imagem proporcionalmente mais larga que o container: sobra nas laterais --
            // escala pela ALTURA e corta igual dos dois lados (nao tem rosto em jogo aqui).
            scale = outHeight.toFloat() / sourceHeight.toFloat()
            dx = (outWidth - sourceWidth * scale) * 0.5f
        } else {
            // Imagem proporcionalmente mais alta/estreita (o caso comum de retrato de
            // personagem): escala pela LARGURA e NAO centraliza verticalmente -- ancora no
            // topo (dy = 0), entao o que sobra pra cortar fica todo embaixo.
            scale = outWidth.toFloat() / sourceWidth.toFloat()
            dx = 0f
        }

        val result = pool.get(outWidth, outHeight, toTransform.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, 0f)
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(toTransform, matrix, paint)
        return result
    }

    override fun equals(other: Any?): Boolean = other is TopCropTransformation

    override fun hashCode(): Int = ID.hashCode()

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
    }

    companion object {
        private const val ID = "com.app.hero_nexus.util.TopCropTransformation"
        private val ID_BYTES = ID.toByteArray(Charsets.UTF_8)
    }
}
