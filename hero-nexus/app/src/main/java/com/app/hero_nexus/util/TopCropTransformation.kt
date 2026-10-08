package com.app.hero_nexus.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

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

            scale = outHeight.toFloat() / sourceHeight.toFloat()
            dx = (outWidth - sourceWidth * scale) * 0.5f
        } else {

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
