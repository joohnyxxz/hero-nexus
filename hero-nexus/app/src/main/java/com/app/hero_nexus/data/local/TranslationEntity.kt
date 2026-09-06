package com.app.hero_nexus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache de traduções PT-BR (documento revisado — "tradução automática via API + cache").
 *
 * A chave é o hash do texto ORIGINAL em inglês, não o id do personagem: assim um texto que se
 * repete entre personagens (ex: o poder "Super Strength") só é traduzido uma vez só, e a
 * tradução fica numa tabela totalmente separada do dado vindo da Comic Vine — nunca sobrescreve
 * o texto original (seção "separação entre dados da API e dados nossos" do documento).
 */
@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey val sourceHash: Int,
    val sourceText: String,
    val translatedText: String,
    val translatedAtMillis: Long
)
