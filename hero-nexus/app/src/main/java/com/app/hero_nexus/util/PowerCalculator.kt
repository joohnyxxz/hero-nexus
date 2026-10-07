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
 * do personagem na Comic Vine, com dois bônus que empurram o "piso" do sorteio pra cima:
 * popularidade (count_of_issue_appearances) e, agora, categoria/alinhamento.
 *
 * Rodada 15, parte 57 (07/10/2026): correção de um bug real relatado pelo usuário -- "Gavião
 * Arqueiro tem mais OVR que Doutor Destino?????" -- tinha fundamento. Antes, o ÚNICO sinal de
 * "quão forte é esse personagem" era o bônus de popularidade, no máximo +12 (empurra só o PISO
 * do sorteio, não a média inteira -- o ganho real na média é metade disso, uns +6). Como tanto
 * heróis populares quanto vilões populares costumam limpar a MESMA faixa de aparições (ambos são
 * nomes grandes do catálogo), os dois acabavam empatando nesse bônus -- e o resultado virava uma
 * loteria pura decidida só pela seed do id. Agora o bônus de CATEGORIA (CharacterCategorizer já
 * classifica Doutor Destino como VILAO e Gavião Arqueiro como ANTI_HEROI -- listas curadas que já
 * existiam no projeto) pesa bem mais que popularidade sozinha, porque é o sinal mais confiável
 * que temos de "é um antagonista de peso" sem ter atributos reais de combate vindos da API.
 */
object PowerCalculator {

    private const val MIN_STAT = 35
    private const val MAX_STAT = 99

    // Rodada 15, parte 60 (07/10/2026): correção de bug real relatado pelo usuário -- "Juggernaut
    // com Inteligência maior que o Homem de Ferro?? mentira total" -- e com razão: até aqui, os 6
    // atributos eram 6 sorteios CEGOS e INDEPENDENTES, todos com o MESMO bônus (popularidade +
    // categoria), sem nenhuma ligação com quem o personagem realmente É. Inteligência, Força,
    // Velocidade etc. não tinham relação nenhuma com o texto real da Comic Vine sobre aquele
    // personagem -- um "cérebro" e um "braço forte" tinham a mesma chance de sortear Inteligência
    // alta. Agora cada atributo ganha um bônus EXTRA, PRÓPRIO dele, quando o deck/description da
    // Comic Vine (texto real, sempre vem preenchido no recurso usado pra listar -- diferente do
    // campo `powers`, ver fetchAndCachePowers) menciona palavras-chave associadas àquele atributo
    // especificamente. Continua sendo um cálculo aproximado (a Comic Vine não tem atributo
    // numérico), mas agora pelo menos reflete o que está escrito sobre o personagem, em vez de
    // ruído puro.
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

    /** Vilões tendem a ser retratados como ameaças de poder bem maior que o herói médio --
     * bônus bem mais forte que popularidade sozinha, pra personagens como Doutor Destino não
     * ficarem reféns do sorteio por id contra um anti-herói/herói qualquer. Ainda vale pra TODOS
     * os atributos (um vilão de peso costuma ser mais perigoso de forma geral), por cima do
     * bônus por traço específico abaixo. */
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
