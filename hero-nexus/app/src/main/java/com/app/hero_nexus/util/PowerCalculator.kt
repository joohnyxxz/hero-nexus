package com.app.hero_nexus.util

import com.app.hero_nexus.data.model.BattleStats
import com.app.hero_nexus.data.model.CharacterCategory
import java.util.Locale
import kotlin.random.Random

/**
 * A Comic Vine não fornece atributos numéricos de combate (Força, Velocidade, etc.) —
 * apenas nomes de poderes e texto livre. O documento (seção 4.3) prevê explicitamente
 * que esses atributos podem ser "adaptados ou calculados pela aplicação".
 *
 * Geramos valores determinísticos (mesma seed = mesmo resultado sempre) a partir do id
 * do personagem na Comic Vine, com um pequeno bônus de popularidade
 * (count_of_issue_appearances), para que personagens mais famosos tendam a ser mais fortes.
 */
object PowerCalculator {

    private const val MIN_STAT = 35
    private const val MAX_STAT = 99

    fun calculateStats(comicVineId: Int, countOfIssueAppearances: Int): BattleStats {
        val random = Random(comicVineId.toLong())
        val popularityBonus = popularityBonus(countOfIssueAppearances)

        fun stat(): Int = (random.nextInt(MIN_STAT, MAX_STAT - popularityBonus + 1) + popularityBonus)
            .coerceIn(MIN_STAT, MAX_STAT)

        return BattleStats(
            strength = stat(),
            speed = stat(),
            intelligence = stat(),
            durability = stat(),
            power = stat(),
            combat = stat()
        )
    }

    private fun popularityBonus(appearances: Int): Int = when {
        appearances >= 2000 -> 12
        appearances >= 800 -> 8
        appearances >= 300 -> 5
        appearances >= 100 -> 2
        else -> 0
    }
}

/**
 * Classificação de alinhamento (Herói / Anti-herói / Vilão) usada nos filtros da coleção
 * (seção 8) e para decidir quem pode aparecer como chefe de fase (seção 16).
 *
 * A Comic Vine não expõe esse dado de forma estruturada, então usamos listas curadas dos
 * personagens Marvel mais conhecidos. Qualquer nome fora dessas listas é tratado como Herói
 * por padrão — a lista pode crescer conforme o jogo evolui.
 */
object CharacterCategorizer {

    private val villains = setOf(
        "duende verde", "green goblin", "thanos", "loki", "doutor destino", "doctor doom",
        "ultron", "magneto", "venom", "carnificina", "carnage", "red skull", "caveira vermelha",
        "kingpin", "rei do crime", "mefisto", "mephisto", "apocalipse", "apocalypse",
        "abutre", "vulture", "rinoceronte", "rhino", "eletro", "electro", "mistério", "mysterio",
        "kraven", "kraven the hunter", "kang", "galactus", "dormammu", "hela", "ultron",
        "sabertooth", "dentes de sabre", "bullseye", "olho de falcão inimigo", "shocker",
        "choque", "chameleon", "camaleão", "sandman", "homem-areia", "lizard", "lagarto",
        "hobgoblin", "duende sinistro", "puppet master", "titânia", "titania", "abomination",
        "abominação", "juggernaut", "titã", "maximus", "surtur", "annihilus", "dark phoenix",
        "fênix negra", "mister sinister", "senhor sinistro", "baron zemo", "barão zemo"
    )

    private val antiHeroes = setOf(
        "wolverine", "venom", "punisher", "justiceiro", "deadpool", "moon knight",
        "cavaleiro da lua", "ghost rider", "motoqueiro fantasma", "elektra", "namor",
        "gamora", "rocket raccoon", "quicksilver", "mercurio", "mercúrio",
        "winter soldier", "soldado invernal", "red hulk", "hulk vermelho", "hawkeye",
        "gaviao arqueiro", "gavião arqueiro"
    )

    fun categorize(name: String): CharacterCategory {
        val n = name.lowercase(Locale.ROOT)
        return when {
            villains.any { n.contains(it) } -> CharacterCategory.VILAO
            antiHeroes.any { n.contains(it) } -> CharacterCategory.ANTI_HEROI
            else -> CharacterCategory.HEROI
        }
    }

    fun isBossCandidate(category: CharacterCategory): Boolean = category == CharacterCategory.VILAO
}
