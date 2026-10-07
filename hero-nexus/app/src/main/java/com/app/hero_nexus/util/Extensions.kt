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

/** Rodada 15, parte 57 (07/10/2026): igual a [loadCharacterImage], só que com
 * [TopCropTransformation] em vez de centerCrop -- usada só no cabeçalho grande da tela de
 * detalhe, onde centerCrop cortava a cabeça do personagem (ver comentário da transformação). Os
 * cards pequenos da coleção/time continuam em centerCrop normal -- lá a proporção do container
 * já é mais próxima da imagem, então não tem o mesmo problema. */
fun ImageView.loadCharacterHeaderImage(url: String?) {
    Glide.with(this)
        .load(url)
        .placeholder(R.drawable.ic_character_placeholder)
        .error(R.drawable.ic_character_placeholder)
        .transform(TopCropTransformation())
        .into(this)
}

/** Remove tags HTML que a Comic Vine costuma retornar em "description"/"deck". */
fun String?.stripHtml(): String {
    if (this.isNullOrBlank()) return ""
    return HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
}

fun String?.asHtmlSpanned() = HtmlCompat.fromHtml(this ?: "", HtmlCompat.FROM_HTML_MODE_COMPACT)

/**
 * Corta um texto longo demais (ex: campo "sobre" do personagem) num tamanho exibível,
 * tentando não quebrar no meio de uma palavra, e adiciona "…" no final.
 */
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
