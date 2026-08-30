package com.app.hero_nexus.util

import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.core.text.HtmlCompat
import com.bumptech.glide.Glide
import com.app.hero_nexus.R

fun ImageView.loadCharacterImage(url: String?) {
    Glide.with(this)
        .load(url)
        .placeholder(R.drawable.ic_character_placeholder)
        .error(R.drawable.ic_character_placeholder)
        .centerCrop()
        .into(this)
}

/** Remove tags HTML que a Comic Vine costuma retornar em "description"/"deck". */
fun String?.stripHtml(): String {
    if (this.isNullOrBlank()) return ""
    return HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
}

fun String?.asHtmlSpanned() = HtmlCompat.fromHtml(this ?: "", HtmlCompat.FROM_HTML_MODE_COMPACT)

fun View.visible() { visibility = View.VISIBLE }
fun View.gone() { visibility = View.GONE }
fun View.visibleIf(condition: Boolean) { visibility = if (condition) View.VISIBLE else View.GONE }

fun Context.dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).toInt()

fun Int.formatCoins(): String = "%,d".format(this).replace(",", ".")
