package com.app.hero_nexus.util

import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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

fun ImageView.loadCharacterHeaderImage(url: String?) {
    Glide.with(this)
        .load(url)
        .placeholder(R.drawable.ic_character_placeholder)
        .error(R.drawable.ic_character_placeholder)
        .transform(TopCropTransformation())
        .into(this)
}

fun String?.stripHtml(): String {
    if (this.isNullOrBlank()) return ""
    return HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
}

fun String?.asHtmlSpanned() = HtmlCompat.fromHtml(this ?: "", HtmlCompat.FROM_HTML_MODE_COMPACT)

fun String.truncateWithEllipsis(maxLength: Int): String {
    if (length <= maxLength) return this
    val cut = substring(0, maxLength).trimEnd()
    val lastSpace = cut.lastIndexOf(' ')
    val safeCut = if (lastSpace > maxLength * 0.6) cut.substring(0, lastSpace) else cut
    return "$safeCut…"
}

fun View.visible() { visibility = View.VISIBLE }
fun View.gone() { visibility = View.GONE }
fun View.visibleIf(condition: Boolean) { visibility = if (condition) View.VISIBLE else View.GONE }

fun Context.dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).toInt()

fun Int.formatCoins(): String = "%,d".format(this).replace(",", ".")

fun View.applyStatusBarTopInset() {
    val initialPaddingTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        view.updatePadding(top = initialPaddingTop + statusBarInset.top)
        insets
    }
}
