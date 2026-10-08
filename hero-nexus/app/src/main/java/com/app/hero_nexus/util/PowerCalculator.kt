package com.app.hero_nexus.util

import com.app.hero_nexus.data.model.BattleStats
import com.app.hero_nexus.data.model.CharacterCategory
import java.util.Locale
import kotlin.random.Random

object PowerCalculator {

    private const val MIN_STAT = 35
    private const val MAX_STAT = 99

    private const val TRAIT_BONUS = 22

    fun calculateStats(
        comicVineId: Int,
        countOfIssueAppearances: Int,
        category: CharacterCategory,
        deck: String?,
        description: String?
    ): BattleStats {
        val random = Random(comicVineId.toLong())
        val baseBonus = popularityBonus(countOfIssueAppearances) + categoryBonus(category)
        val text = ((deck ?: "") + " " + (description ?: "")).lowercase(Locale.ROOT)

        fun stat(traitKeywords: Set<String>): Int {
            val hasTrait = traitKeywords.any { text.contains(it) }
            val bonus = (baseBonus + if (hasTrait) TRAIT_BONUS else 0).coerceAtMost(MAX_STAT - MIN_STAT)
            return (random.nextInt(MIN_STAT, MAX_STAT - bonus + 1) + bonus).coerceIn(MIN_STAT, MAX_STAT)
        }

        return BattleStats(
            strength = stat(STRENGTH_KEYWORDS),
            speed = stat(SPEED_KEYWORDS),
            intelligence = stat(INTELLIGENCE_KEYWORDS),
            durability = stat(DURABILITY_KEYWORDS),
            power = stat(POWER_KEYWORDS),
            combat = stat(COMBAT_KEYWORDS)
        )
    }

    private fun popularityBonus(appearances: Int): Int = when {
        appearances >= 2000 -> 12
        appearances >= 800 -> 8
        appearances >= 300 -> 5
        appearances >= 100 -> 2
        else -> 0
    }

    private fun categoryBonus(category: CharacterCategory): Int = when (category) {
        CharacterCategory.VILAO -> 26
        CharacterCategory.ANTI_HEROI -> 8
        CharacterCategory.HEROI -> 0
    }

    private val INTELLIGENCE_KEYWORDS = setOf(
        "genius", "scientist", "inventor", "tactician", "strategist", "intellect",
        "brilliant", "engineer", "prodigy", "scientific mind", "super-genius"
    )
    private val STRENGTH_KEYWORDS = setOf(
        "superhuman strength", "super strength", "super-strength", "incredibly strong",
        "enormous strength", "immense strength", "strongest"
    )
    private val SPEED_KEYWORDS = setOf(
        "super speed", "superhuman speed", "super-speed", "fastest", "speed force"
    )
    private val DURABILITY_KEYWORDS = setOf(
        "invulnerab", "regenerat", "healing factor", "nearly indestructible", "unstoppable",
        "impervious", "immortal"
    )
    private val POWER_KEYWORDS = setOf(
        "cosmic power", "cosmic energy", "mystical", "magic", "energy blasts",
        "reality-warping", "omega-level", "god of"
    )
    private val COMBAT_KEYWORDS = setOf(
        "master martial artist", "expert combatant", "master combatant", "skilled fighter",
        "weapons expert", "master assassin", "trained by", "master tactician in combat",
        "hand-to-hand combat"
    )
}

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
