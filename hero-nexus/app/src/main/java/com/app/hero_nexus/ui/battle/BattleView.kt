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
import kotlin.random.Random

/**
 * Renderiza a arena de batalha num Canvas simples (lutadores continuam círculos
 * coloridos com nome e barra de vida -- sprites reais ficam pra v3), com o CENÁRIO
 * usando arte real: pack gratuito "Free Pixel Art Street 2D Backgrounds" (CraftPix,
 * craftpix.net -- licença gratuita conferida, uso comercial permitido, sem exigência de
 * atribuição), 4 variantes de rua (1920x1080 cada, cena completa já composta pelo artista).
 *
 * Seção "fluxo contínuo" (feedback 31/08 -- "igual Golden Axe e Streets of Rage, você anda e a
 * câmera segue"): todo mundo (jogador, inimigos, caixas) vive em coordenada de MUNDO
 * (`Fighter.x`/`DestructibleObject.x`). Aqui a gente subtrai `engine.cameraX` na hora de
 * desenhar pra converter em posição de TELA -- é a única responsabilidade nova desta classe
 * em relação à câmera.
 *
 * Rodada 15, parte 18 (30/09/2026): trocado o cenário antigo (Kenney, tiras feitas pra repetir)
 * pelo pack CraftPix "Free Pixel Art Street 2D Backgrounds" -- 4 cenas únicas e completas
 * (prédios, rua, tudo numa posição fixa).
 *
 * Parte 19 (30/09/2026): a parte 18 tentou desenhar cada cena só UMA vez, ampliada e deslizando
 * até a própria borda (achando que repetir ia "colar" feio) -- só que isso quebrou de duas formas
 * reportadas pelo usuário ("ta quebrado a imagem no app e vc nao se movimenta, ta horroroso"):
 * (1) a imagem era ampliada só pela ALTURA da tela, então em celulares com tela mais larga que
 * 16:9 (comum -- a arte é 1920x1080, mas a maioria dos aparelhos modernos em paisagem é mais
 * larga que isso) a imagem ampliada ficava mais ESTREITA que a tela, sobrando um vão; (2) o
 * cálculo de deslize dependia dessa mesma largura sobrando, então nesses aparelhos o "vão" zerava
 * o deslize inteiro -- a rua nunca se movia. A solução agora (`drawLoopingBackground()`): voltar a
 * LADRILHAR a imagem (só que inteira, não mais um par céu/chão), deslizando 1:1 com a câmera do
 * mundo e repetindo em loop quando a última cópia sai da tela -- exatamente o que o usuário pediu
 * ("conforme vc vai andando pro final, começa de novo o começo da imagem, olhando da esquerda pra
 * direita, pelo menos por enquanto"). Ladrilhar também resolve o problema (1) de graça: não
 * importa a proporção da tela, sempre cabem cópias suficientes pra cobrir ela inteira.
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

    // Rodada 15, parte 19 (30/09/2026): as caixas/barris eram formas geométricas desenhadas na
    // mão -- pedido do usuário ("na pasta que te anexei tem um modelo de caixas, usa esses
    // pequenos png") trocou o CORPO deles por sprites reais do mesmo pack CraftPix (ver
    // `ensurePropSpritesLoaded()`/`drawDestructible()`). Os Paints de caixa/barril antigos saíram
    // -- só os tons de estilhaço (`shardLightPaint`/`shardDarkPaint`) continuam, a animação de
    // quebra ainda é feita de retângulos soltos, não teria por que ser sprite.
    // Fragmentos da animação de quebra (rodada 13, feedback 01/09: "uma animação quando
    // quebrar, tipo tremer e virar estilhaços") -- reaproveita os tons de madeira/metal já
    // usados nas caixas/barris, só que soltos.
    private val shardLightPaint = Paint().apply { color = Color.parseColor("#C98F52"); isAntiAlias = true }
    private val shardDarkPaint = Paint().apply { color = Color.parseColor("#2B1B0E"); isAntiAlias = true }
    // Rodada 15, parte 19 (ajuste do mesmo dia -- usuário reportou "consigo quebrar as novas
    // caixas mas não consigo enxergar"): o objeto em si sempre existia e reagia a golpe
    // (o modelo/`DestructibleObject` nunca dependeu do sprite carregar), só o DESENHO podia sumir
    // se o bitmap não carregasse por algum motivo (ex: build incremental que não empacotou um PNG
    // novo adicionado nesta mesma rodada -- cenário plausível, os 9 arquivos foram adicionados na
    // parte 19 e o Android Studio às vezes não pega recurso novo sem um rebuild limpo). Esses
    // Paints de reserva garantem que NUNCA mais fica invisível: se o sprite falhar, cai pra uma
    // forma simples e visível em vez de nada -- ver `drawGroundSprite()`.
    private val cratePropFallbackPaint = Paint().apply { color = Color.parseColor("#C98F52"); isAntiAlias = true }
    private val barrelPropFallbackPaint = Paint().apply { color = Color.parseColor("#3A3A3A"); isAntiAlias = true }
    private val decoPropFallbackPaint = Paint().apply { color = Color.parseColor("#6B6B6B"); isAntiAlias = true }
    private val playerPaint = Paint().apply { color = Color.parseColor("#FFC400") }
    private val playerStrokePaint = Paint().apply {
        color = Color.parseColor("#7A1414"); style = Paint.Style.STROKE; strokeWidth = 5f
    }
    // Rodada 15, parte 36 (03/10/2026): primeira sprite de personagem de verdade (Homem-Aranha,
    // gerada pelo usuário via IA a partir do prompt que passei) -- ver HERO_SPRITES/
    // drawHeroBitmap() mais abaixo (parte 37 adicionou golpe/dano/derrota e a transição de morte
    // em 3 fases, ver DeathTransitionPhase em BattleEngine.kt). `isFilterBitmap = false` é de
    // propósito: sem isso, o Android suaviza a imagem ao escalar o pixel art de 64x64 pra cima,
    // borrando os pixels -- queremos o visual "quadriculado" nítido, igual o resto da estética
    // retrô do jogo.
    private val heroSpritePaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val heroBitmapCache = HashMap<Int, Bitmap?>()
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
    // Rodada 15, parte 34 (03/10/2026): tremor de tela durante a entrada do chefe -- ver
    // onDraw()/SCREEN_SHAKE_AMPLITUDE no companion object.
    private val shakeRandom = Random(System.nanoTime())
    private val healthBgPaint = Paint().apply { color = Color.parseColor("#40000000") }
    private val healthFgPaint = Paint().apply { color = Color.parseColor("#3CCB6E") }
    private val healthFgLowPaint = Paint().apply { color = Color.parseColor("#E62429") }

    // --------------------------------------------------------------------------- cenário/arena

    // Fallback só usado se o bitmap do cenário não carregar por algum motivo (recurso
    // ausente/corrompido) -- mantém o app funcional mesmo assim. Agora preenche a tela INTEIRA
    // (antes só ia até o horizonte, porque havia uma camada de chão sólida separada por baixo;
    // essa camada não existe mais -- ver comentário de `drawScenery()`).
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

    // Rodada 15, parte 18 (30/09/2026): cenário real, pack "Free Pixel Art Street 2D
    // Backgrounds" (CraftPix). 4 variantes de rua (`SCENERY_VARIANT_COUNT` em BattleEngine),
    // sorteada por trecho (ver `BattleEngine.currentVariant()`), cada uma UM bitmap único (a cena
    // inteira já composta pelo artista -- não mais um par céu/chão ladrilhado). Ver
    // `drawLoopingBackground()` pra como ele é desenhado (ladrilhado, em loop -- parte 19).
    private var backgroundBitmap: Bitmap? = null
    private var loadedVariant = -1
    private val sceneryMatrix = Matrix()
    private val propMatrix = Matrix()

    /** Rodada 15, parte 32 (02/10/2026): 8 cenas novas somadas às 4 ruas originais (mesmos 2
     * packs CraftPix citados em BattleEngine.SCENERY_VARIANT_COUNT) -- índices 4-7 são o pack
     * "Pixel Art Battlegrounds" (ruínas/salão do trono com dragão/selva/cripta), 8-11 são o pack
     * "Postapocalypse Backgrounds". Mesma mecânica de sempre (uma imagem ÚNICA e fixa por
     * variante, nunca duas camadas) -- só a lista de recursos cresceu. */
    private fun ensureSceneryLoaded(variant: Int) {
        if (loadedVariant == variant) return
        loadedVariant = variant
        val res = when (variant) {
            1 -> R.drawable.img_battle_scenery_city2
            2 -> R.drawable.img_battle_scenery_city3
            3 -> R.drawable.img_battle_scenery_city4
            4 -> R.drawable.img_battle_scenery_ruins1
            5 -> R.drawable.img_battle_scenery_throne2
            6 -> R.drawable.img_battle_scenery_jungle3
            7 -> R.drawable.img_battle_scenery_crypt4
            8 -> R.drawable.img_battle_scenery_apoc1
            9 -> R.drawable.img_battle_scenery_apoc2
            10 -> R.drawable.img_battle_scenery_apoc3
            11 -> R.drawable.img_battle_scenery_apoc4
            else -> R.drawable.img_battle_scenery_city1
        }
        backgroundBitmap = runCatching { BitmapFactory.decodeResource(resources, res) }.getOrNull()
    }

    // Rodada 15, parte 19 (30/09/2026): caixa/barril e decoração de cenário viraram sprites reais
    // (mesmo pack CraftPix), pedido do usuário pra deixar o nível "menos infinito" -- ao contrário
    // do fundo (que troca por variante de cenário), estes são fixos, carregados uma vez só.
    // `cratePropBitmap`/`barrelPropBitmap`: corpo de `DestructibleObject` (ver drawDestructible()).
    // `decoBitmaps`: pool de decoração sem colisão espalhada pelo mundo (ver
    // BattleEngine.sceneryProps / drawSceneryProps()) -- ordem tem que bater com
    // `BattleEngine.SCENERY_PROP_KIND_COUNT` (6 itens, índice = SceneryProp.kind).
    private var cratePropBitmap: Bitmap? = null
    private var barrelPropBitmap: Bitmap? = null
    private var decoBitmaps: Array<Bitmap?>? = null
    private var propSpritesLoaded = false

    private fun ensurePropSpritesLoaded() {
        if (propSpritesLoaded) return
        propSpritesLoaded = true
        cratePropBitmap = runCatching {
            BitmapFactory.decodeResource(resources, R.drawable.img_battle_prop_crate)
        }.getOrNull()
        barrelPropBitmap = runCatching {
            BitmapFactory.decodeResource(resources, R.drawable.img_battle_prop_barrel)
        }.getOrNull()
        // Rodada 15, parte 33 (03/10/2026): removida a "lojinha" (kiosk) -- pedido explícito
        // do usuário ("tira a imagem de uma lojinha, ela nao combina com quase nenhum
        // cenario"). 6 -> 5 entradas; índice reindexado (ver SCENERY_PROP_KIND_COUNT em
        // BattleEngine.kt e DECO_SPRITE_HEIGHTS logo abaixo, mesma ordem).
        val decoRes = intArrayOf(
            R.drawable.img_battle_deco_hydrant,
            R.drawable.img_battle_deco_callbox,
            R.drawable.img_battle_deco_cafe_table,
            R.drawable.img_battle_deco_policebox,
            R.drawable.img_battle_deco_fountain
        )
        decoBitmaps = Array(decoRes.size) { i ->
            runCatching { BitmapFactory.decodeResource(resources, decoRes[i]) }.getOrNull()
        }
    }

    /** Jogo só tem sprite de verdade pra quem já foi desenhado -- os outros personagens
     * continuam como bolinha (fallback) até terem a arte pronta. Rodada 15, parte 37
     * (04/10/2026): além de `idle`/`walk`, ganhou `punch` (golpe), `hurt` (levando dano) e
     * `death` (derrota) -- arrays vazios por padrão, pra um personagem com sprite só de
     * idle/walk (nenhum caso hoje, mas deixa a porta aberta) não precisar listar os 3 também.
     * Cada array toca UMA VEZ (não faz loop) durante a janela de tempo da respectiva ação -- ver
     * `onDraw()`, que decide a janela E o frame. */
    private data class HeroSpriteSet(
        val idle: Int,
        val walk: IntArray,
        val punch: IntArray = IntArray(0),
        val hurt: IntArray = IntArray(0),
        val death: IntArray = IntArray(0)
    )

    private val HERO_SPRITES: List<Pair<List<String>, HeroSpriteSet>> = listOf(
        listOf("spider-man", "spiderman") to HeroSpriteSet(
            idle = R.drawable.img_hero_spiderman_idle,
            walk = intArrayOf(
                R.drawable.img_hero_spiderman_walk_0,
                R.drawable.img_hero_spiderman_walk_1,
                R.drawable.img_hero_spiderman_walk_2,
                R.drawable.img_hero_spiderman_walk_3,
                R.drawable.img_hero_spiderman_walk_4,
                R.drawable.img_hero_spiderman_walk_5,
                R.drawable.img_hero_spiderman_walk_6,
                R.drawable.img_hero_spiderman_walk_7
            ),
            // Rodada 15, parte 37: só existe gerado na direção "south-east" (as outras 3
            // animações são todas "east") -- usado assim mesmo, por pedido direto do usuário,
            // mas o ângulo destoa um pouco do resto enquanto não for regerado em "east".
            punch = intArrayOf(
                R.drawable.img_hero_spiderman_punch_0,
                R.drawable.img_hero_spiderman_punch_1,
                R.drawable.img_hero_spiderman_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_hero_spiderman_hurt_0,
                R.drawable.img_hero_spiderman_hurt_1,
                R.drawable.img_hero_spiderman_hurt_2,
                R.drawable.img_hero_spiderman_hurt_3,
                R.drawable.img_hero_spiderman_hurt_4,
                R.drawable.img_hero_spiderman_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_hero_spiderman_death_0,
                R.drawable.img_hero_spiderman_death_1,
                R.drawable.img_hero_spiderman_death_2,
                R.drawable.img_hero_spiderman_death_3,
                R.drawable.img_hero_spiderman_death_4,
                R.drawable.img_hero_spiderman_death_5,
                R.drawable.img_hero_spiderman_death_6
            )
        ),
        // Rodada 15, parte 39 (04/10/2026): segundo personagem com sprite de verdade -- Capitão
        // América. Mesmo conjunto completo do Homem-Aranha (idle/andar/golpe/dano/derrota), e
        // desta vez as 4 animações já vieram todas na direção "east" (sem a inconsistência de
        // ângulo do golpe do Homem-Aranha, que ficou em "south-east").
        listOf("captain america", "capitao america", "capitão america") to HeroSpriteSet(
            idle = R.drawable.img_hero_captainamerica_idle,
            walk = intArrayOf(
                R.drawable.img_hero_captainamerica_walk_0,
                R.drawable.img_hero_captainamerica_walk_1,
                R.drawable.img_hero_captainamerica_walk_2,
                R.drawable.img_hero_captainamerica_walk_3,
                R.drawable.img_hero_captainamerica_walk_4,
                R.drawable.img_hero_captainamerica_walk_5,
                R.drawable.img_hero_captainamerica_walk_6,
                R.drawable.img_hero_captainamerica_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_hero_captainamerica_punch_0,
                R.drawable.img_hero_captainamerica_punch_1,
                R.drawable.img_hero_captainamerica_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_hero_captainamerica_hurt_0,
                R.drawable.img_hero_captainamerica_hurt_1,
                R.drawable.img_hero_captainamerica_hurt_2,
                R.drawable.img_hero_captainamerica_hurt_3,
                R.drawable.img_hero_captainamerica_hurt_4,
                R.drawable.img_hero_captainamerica_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_hero_captainamerica_death_0,
                R.drawable.img_hero_captainamerica_death_1,
                R.drawable.img_hero_captainamerica_death_2,
                R.drawable.img_hero_captainamerica_death_3,
                R.drawable.img_hero_captainamerica_death_4,
                R.drawable.img_hero_captainamerica_death_5,
                R.drawable.img_hero_captainamerica_death_6
            )
        ),
        // Rodada 15, parte 41 (04/10/2026): terceiro personagem com sprite de verdade --
        // Wolverine. Mesmo conjunto completo (idle/andar/golpe/dano/derrota), e as 4 animações
        // vieram todas em "east" (igual Capitão América, sem a inconsistência de ângulo do golpe
        // do Homem-Aranha).
        listOf("wolverine") to HeroSpriteSet(
            idle = R.drawable.img_hero_wolverine_idle,
            walk = intArrayOf(
                R.drawable.img_hero_wolverine_walk_0,
                R.drawable.img_hero_wolverine_walk_1,
                R.drawable.img_hero_wolverine_walk_2,
                R.drawable.img_hero_wolverine_walk_3,
                R.drawable.img_hero_wolverine_walk_4,
                R.drawable.img_hero_wolverine_walk_5,
                R.drawable.img_hero_wolverine_walk_6,
                R.drawable.img_hero_wolverine_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_hero_wolverine_punch_0,
                R.drawable.img_hero_wolverine_punch_1,
                R.drawable.img_hero_wolverine_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_hero_wolverine_hurt_0,
                R.drawable.img_hero_wolverine_hurt_1,
                R.drawable.img_hero_wolverine_hurt_2,
                R.drawable.img_hero_wolverine_hurt_3,
                R.drawable.img_hero_wolverine_hurt_4,
                R.drawable.img_hero_wolverine_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_hero_wolverine_death_0,
                R.drawable.img_hero_wolverine_death_1,
                R.drawable.img_hero_wolverine_death_2,
                R.drawable.img_hero_wolverine_death_3,
                R.drawable.img_hero_wolverine_death_4,
                R.drawable.img_hero_wolverine_death_5,
                R.drawable.img_hero_wolverine_death_6
            )
        ),
        // Rodada 15, parte 60 (04/10/2026): CORRIGIDO -- Carnificina já tinha sprite completo
        // desde a parte 50, mas só cadastrado em ENEMY_SPRITES (pra aparecer como chefão). Quando
        // o jogador desbloqueia e escolhe ele pra JOGAR, BattleView olha é HERO_SPRITES -- como
        // não tinha entrada aqui, caía na bolinha de cor (amarela, a cor de raridade dele) em vez
        // do sprite. Mesma arte, mesmos 25 arquivos ("img_enemy_carnage_*"), nenhum arquivo novo
        // precisou ser copiado -- só faltava essa segunda entrada.
        listOf("carnage", "carnificina") to HeroSpriteSet(
            idle = R.drawable.img_enemy_carnage_idle,
            walk = intArrayOf(
                R.drawable.img_enemy_carnage_walk_0,
                R.drawable.img_enemy_carnage_walk_1,
                R.drawable.img_enemy_carnage_walk_2,
                R.drawable.img_enemy_carnage_walk_3,
                R.drawable.img_enemy_carnage_walk_4,
                R.drawable.img_enemy_carnage_walk_5,
                R.drawable.img_enemy_carnage_walk_6,
                R.drawable.img_enemy_carnage_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_enemy_carnage_punch_0,
                R.drawable.img_enemy_carnage_punch_1,
                R.drawable.img_enemy_carnage_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_enemy_carnage_hurt_0,
                R.drawable.img_enemy_carnage_hurt_1,
                R.drawable.img_enemy_carnage_hurt_2,
                R.drawable.img_enemy_carnage_hurt_3,
                R.drawable.img_enemy_carnage_hurt_4,
                R.drawable.img_enemy_carnage_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_enemy_carnage_death_0,
                R.drawable.img_enemy_carnage_death_1,
                R.drawable.img_enemy_carnage_death_2,
                R.drawable.img_enemy_carnage_death_3,
                R.drawable.img_enemy_carnage_death_4,
                R.drawable.img_enemy_carnage_death_5,
                R.drawable.img_enemy_carnage_death_6
            )
        ),
        // Rodada 15, parte 61 (04/10/2026): Homem de Ferro -- segundo sprite jogavel gerado
        // nesta rodada (o primeiro foi so o conserto do Carnificina, que ja tinha arte).
        listOf("iron man", "homem de ferro") to HeroSpriteSet(
            idle = R.drawable.img_hero_ironman_idle,
            walk = intArrayOf(
                R.drawable.img_hero_ironman_walk_0,
                R.drawable.img_hero_ironman_walk_1,
                R.drawable.img_hero_ironman_walk_2,
                R.drawable.img_hero_ironman_walk_3,
                R.drawable.img_hero_ironman_walk_4,
                R.drawable.img_hero_ironman_walk_5,
                R.drawable.img_hero_ironman_walk_6,
                R.drawable.img_hero_ironman_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_hero_ironman_punch_0,
                R.drawable.img_hero_ironman_punch_1,
                R.drawable.img_hero_ironman_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_hero_ironman_hurt_0,
                R.drawable.img_hero_ironman_hurt_1,
                R.drawable.img_hero_ironman_hurt_2,
                R.drawable.img_hero_ironman_hurt_3,
                R.drawable.img_hero_ironman_hurt_4,
                R.drawable.img_hero_ironman_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_hero_ironman_death_0,
                R.drawable.img_hero_ironman_death_1,
                R.drawable.img_hero_ironman_death_2,
                R.drawable.img_hero_ironman_death_3,
                R.drawable.img_hero_ironman_death_4,
                R.drawable.img_hero_ironman_death_5,
                R.drawable.img_hero_ironman_death_6
            )
        )
    )

    /** Match por "contém", igual o `CharacterCategorizer` (`PowerCalculator.kt`) -- o nome real
     * do personagem vem da Comic Vine e pode vir com variações (ex: "Spider-Man (Peter Parker)"),
     * então comparar com `.contains()` em vez de igualdade exata evita precisar listar cada
     * variação manualmente. */
    private fun heroSpriteSetFor(name: String): HeroSpriteSet? {
        val key = name.lowercase()
        HERO_SPRITES.firstOrNull { (aliases, _) -> aliases.any { key.contains(it) } }?.let { return it.second }
        // Rodada 15, parte 62 (05/10/2026): CORRIGIDO -- pedido explícito do usuário ("qualquer
        // vilao desbloqueado deve ser usado a sprite como personagem pro usuario"). Até aqui,
        // um vilão só jogava com sprite se alguém lembrasse de cadastrar ele NAS DUAS listas
        // (HERO_SPRITES e ENEMY_SPRITES) -- foi exatamente isso que faltou pro Lagarto (só
        // entrou em ENEMY_SPRITES, pra aparecer como chefe) e ele caiu na bolinha quando o
        // usuário tentou JOGAR com ele. Agora, se não achar uma entrada própria de herói, cai
        // automaticamente pro conjunto de INIMIGO do mesmo nome, se existir -- um vilão com
        // sprite de chefão já sai pronto pra ser jogável também, sem precisar duplicar nada.
        val enemySet = ENEMY_SPRITES.firstOrNull { (aliases, _) -> aliases.any { key.contains(it) } }?.second
            ?: return null
        return HeroSpriteSet(enemySet.idle, enemySet.walk, enemySet.punch, enemySet.hurt, enemySet.death)
    }

    /** Rodada 15, parte 45 (04/10/2026): primeiro INIMIGO com sprite de verdade (ate aqui,
     * so os herois controlaveis tinham -- capanga/miniboss/chefao eram sempre circulos
     * coloridos). Reaproveita o MESMO sistema de desenho dos herois (`drawHeroBitmap`, agora
     * parametrizado por tamanho/sink) -- so o cadastro de sprites e separado
     * (`ENEMY_SPRITES`), porque um inimigo nao passa pela transicao de morte em 3 fases dos
     * herois (ele so some da lista de `enemies` ao morrer, sem fase de queda/pisca/proximo
     * entrando) -- por isso nao tem campo `death` aqui ainda. `hurt` tambem fica de fora por
     * enquanto -- o engine nao guarda um "instante do ultimo hit" por inimigo, so por
     * jogador (`playerHurtAtMs`). Deixa a porta aberta pros dois depois, se for pedido. */
    private data class EnemySpriteSet(
        val idle: Int,
        val walk: IntArray,
        val punch: IntArray = IntArray(0),
        val hurt: IntArray = IntArray(0),
        val death: IntArray = IntArray(0)
    )

    private val ENEMY_SPRITES: List<Pair<List<String>, EnemySpriteSet>> = listOf(
        listOf("bulldozer") to EnemySpriteSet(
            idle = R.drawable.img_enemy_bulldozer_idle,
            walk = intArrayOf(
                R.drawable.img_enemy_bulldozer_walk_0,
                R.drawable.img_enemy_bulldozer_walk_1,
                R.drawable.img_enemy_bulldozer_walk_2,
                R.drawable.img_enemy_bulldozer_walk_3,
                R.drawable.img_enemy_bulldozer_walk_4,
                R.drawable.img_enemy_bulldozer_walk_5,
                R.drawable.img_enemy_bulldozer_walk_6,
                R.drawable.img_enemy_bulldozer_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_enemy_bulldozer_punch_0,
                R.drawable.img_enemy_bulldozer_punch_1,
                R.drawable.img_enemy_bulldozer_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_enemy_bulldozer_hurt_0,
                R.drawable.img_enemy_bulldozer_hurt_1,
                R.drawable.img_enemy_bulldozer_hurt_2,
                R.drawable.img_enemy_bulldozer_hurt_3,
                R.drawable.img_enemy_bulldozer_hurt_4,
                R.drawable.img_enemy_bulldozer_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_enemy_bulldozer_death_0,
                R.drawable.img_enemy_bulldozer_death_1,
                R.drawable.img_enemy_bulldozer_death_2,
                R.drawable.img_enemy_bulldozer_death_3,
                R.drawable.img_enemy_bulldozer_death_4,
                R.drawable.img_enemy_bulldozer_death_5,
                R.drawable.img_enemy_bulldozer_death_6
            )
        ),
        listOf("carnage", "carnificina") to EnemySpriteSet(
            idle = R.drawable.img_enemy_carnage_idle,
            walk = intArrayOf(
                R.drawable.img_enemy_carnage_walk_0,
                R.drawable.img_enemy_carnage_walk_1,
                R.drawable.img_enemy_carnage_walk_2,
                R.drawable.img_enemy_carnage_walk_3,
                R.drawable.img_enemy_carnage_walk_4,
                R.drawable.img_enemy_carnage_walk_5,
                R.drawable.img_enemy_carnage_walk_6,
                R.drawable.img_enemy_carnage_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_enemy_carnage_punch_0,
                R.drawable.img_enemy_carnage_punch_1,
                R.drawable.img_enemy_carnage_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_enemy_carnage_hurt_0,
                R.drawable.img_enemy_carnage_hurt_1,
                R.drawable.img_enemy_carnage_hurt_2,
                R.drawable.img_enemy_carnage_hurt_3,
                R.drawable.img_enemy_carnage_hurt_4,
                R.drawable.img_enemy_carnage_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_enemy_carnage_death_0,
                R.drawable.img_enemy_carnage_death_1,
                R.drawable.img_enemy_carnage_death_2,
                R.drawable.img_enemy_carnage_death_3,
                R.drawable.img_enemy_carnage_death_4,
                R.drawable.img_enemy_carnage_death_5,
                R.drawable.img_enemy_carnage_death_6
            )
        ),
        // Rodada 15, parte 61 (04/10/2026): Wrecker/Piledriver/Thunderball (capangas comuns,
        // o resto da "Wrecking Crew" do Bulldozer) + Lagarto e Doutor Destino (candidatos a
        // chefe) -- os 25 arquivos de cada um foram gerados pelo usuario (prompts revisados
        // antes, pra bater com a aparencia real dos personagens da Marvel) e so precisavam
        // dessa entrada pra sair da bolinha generica.
        listOf("wrecker") to EnemySpriteSet(
            idle = R.drawable.img_enemy_wrecker_idle,
            walk = intArrayOf(
                R.drawable.img_enemy_wrecker_walk_0,
                R.drawable.img_enemy_wrecker_walk_1,
                R.drawable.img_enemy_wrecker_walk_2,
                R.drawable.img_enemy_wrecker_walk_3,
                R.drawable.img_enemy_wrecker_walk_4,
                R.drawable.img_enemy_wrecker_walk_5,
                R.drawable.img_enemy_wrecker_walk_6,
                R.drawable.img_enemy_wrecker_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_enemy_wrecker_punch_0,
                R.drawable.img_enemy_wrecker_punch_1,
                R.drawable.img_enemy_wrecker_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_enemy_wrecker_hurt_0,
                R.drawable.img_enemy_wrecker_hurt_1,
                R.drawable.img_enemy_wrecker_hurt_2,
                R.drawable.img_enemy_wrecker_hurt_3,
                R.drawable.img_enemy_wrecker_hurt_4,
                R.drawable.img_enemy_wrecker_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_enemy_wrecker_death_0,
                R.drawable.img_enemy_wrecker_death_1,
                R.drawable.img_enemy_wrecker_death_2,
                R.drawable.img_enemy_wrecker_death_3,
                R.drawable.img_enemy_wrecker_death_4,
                R.drawable.img_enemy_wrecker_death_5,
                R.drawable.img_enemy_wrecker_death_6
            )
        ),
        listOf("piledriver") to EnemySpriteSet(
            idle = R.drawable.img_enemy_piledriver_idle,
            walk = intArrayOf(
                R.drawable.img_enemy_piledriver_walk_0,
                R.drawable.img_enemy_piledriver_walk_1,
                R.drawable.img_enemy_piledriver_walk_2,
                R.drawable.img_enemy_piledriver_walk_3,
                R.drawable.img_enemy_piledriver_walk_4,
                R.drawable.img_enemy_piledriver_walk_5,
                R.drawable.img_enemy_piledriver_walk_6,
                R.drawable.img_enemy_piledriver_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_enemy_piledriver_punch_0,
                R.drawable.img_enemy_piledriver_punch_1,
                R.drawable.img_enemy_piledriver_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_enemy_piledriver_hurt_0,
                R.drawable.img_enemy_piledriver_hurt_1,
                R.drawable.img_enemy_piledriver_hurt_2,
                R.drawable.img_enemy_piledriver_hurt_3,
                R.drawable.img_enemy_piledriver_hurt_4,
                R.drawable.img_enemy_piledriver_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_enemy_piledriver_death_0,
                R.drawable.img_enemy_piledriver_death_1,
                R.drawable.img_enemy_piledriver_death_2,
                R.drawable.img_enemy_piledriver_death_3,
                R.drawable.img_enemy_piledriver_death_4,
                R.drawable.img_enemy_piledriver_death_5,
                R.drawable.img_enemy_piledriver_death_6
            )
        ),
        listOf("thunderball") to EnemySpriteSet(
            idle = R.drawable.img_enemy_thunderball_idle,
            walk = intArrayOf(
                R.drawable.img_enemy_thunderball_walk_0,
                R.drawable.img_enemy_thunderball_walk_1,
                R.drawable.img_enemy_thunderball_walk_2,
                R.drawable.img_enemy_thunderball_walk_3,
                R.drawable.img_enemy_thunderball_walk_4,
                R.drawable.img_enemy_thunderball_walk_5,
                R.drawable.img_enemy_thunderball_walk_6,
                R.drawable.img_enemy_thunderball_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_enemy_thunderball_punch_0,
                R.drawable.img_enemy_thunderball_punch_1,
                R.drawable.img_enemy_thunderball_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_enemy_thunderball_hurt_0,
                R.drawable.img_enemy_thunderball_hurt_1,
                R.drawable.img_enemy_thunderball_hurt_2,
                R.drawable.img_enemy_thunderball_hurt_3,
                R.drawable.img_enemy_thunderball_hurt_4,
                R.drawable.img_enemy_thunderball_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_enemy_thunderball_death_0,
                R.drawable.img_enemy_thunderball_death_1,
                R.drawable.img_enemy_thunderball_death_2,
                R.drawable.img_enemy_thunderball_death_3,
                R.drawable.img_enemy_thunderball_death_4,
                R.drawable.img_enemy_thunderball_death_5,
                R.drawable.img_enemy_thunderball_death_6
            )
        ),
        listOf("lizard", "lagarto") to EnemySpriteSet(
            idle = R.drawable.img_enemy_lizard_idle,
            walk = intArrayOf(
                R.drawable.img_enemy_lizard_walk_0,
                R.drawable.img_enemy_lizard_walk_1,
                R.drawable.img_enemy_lizard_walk_2,
                R.drawable.img_enemy_lizard_walk_3,
                R.drawable.img_enemy_lizard_walk_4,
                R.drawable.img_enemy_lizard_walk_5,
                R.drawable.img_enemy_lizard_walk_6,
                R.drawable.img_enemy_lizard_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_enemy_lizard_punch_0,
                R.drawable.img_enemy_lizard_punch_1,
                R.drawable.img_enemy_lizard_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_enemy_lizard_hurt_0,
                R.drawable.img_enemy_lizard_hurt_1,
                R.drawable.img_enemy_lizard_hurt_2,
                R.drawable.img_enemy_lizard_hurt_3,
                R.drawable.img_enemy_lizard_hurt_4,
                R.drawable.img_enemy_lizard_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_enemy_lizard_death_0,
                R.drawable.img_enemy_lizard_death_1,
                R.drawable.img_enemy_lizard_death_2,
                R.drawable.img_enemy_lizard_death_3,
                R.drawable.img_enemy_lizard_death_4,
                R.drawable.img_enemy_lizard_death_5,
                R.drawable.img_enemy_lizard_death_6
            )
        ),
        listOf("doom", "destino") to EnemySpriteSet(
            idle = R.drawable.img_enemy_doom_idle,
            walk = intArrayOf(
                R.drawable.img_enemy_doom_walk_0,
                R.drawable.img_enemy_doom_walk_1,
                R.drawable.img_enemy_doom_walk_2,
                R.drawable.img_enemy_doom_walk_3,
                R.drawable.img_enemy_doom_walk_4,
                R.drawable.img_enemy_doom_walk_5,
                R.drawable.img_enemy_doom_walk_6,
                R.drawable.img_enemy_doom_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_enemy_doom_punch_0,
                R.drawable.img_enemy_doom_punch_1,
                R.drawable.img_enemy_doom_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_enemy_doom_hurt_0,
                R.drawable.img_enemy_doom_hurt_1,
                R.drawable.img_enemy_doom_hurt_2,
                R.drawable.img_enemy_doom_hurt_3,
                R.drawable.img_enemy_doom_hurt_4,
                R.drawable.img_enemy_doom_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_enemy_doom_death_0,
                R.drawable.img_enemy_doom_death_1,
                R.drawable.img_enemy_doom_death_2,
                R.drawable.img_enemy_doom_death_3,
                R.drawable.img_enemy_doom_death_4,
                R.drawable.img_enemy_doom_death_5,
                R.drawable.img_enemy_doom_death_6
            )
        ),
    )

    private fun enemySpriteSetFor(name: String): EnemySpriteSet? {
        val key = name.lowercase()
        return ENEMY_SPRITES.firstOrNull { (aliases, _) -> aliases.any { key.contains(it) } }?.second
    }

    private fun heroBitmap(res: Int): Bitmap? = heroBitmapCache.getOrPut(res) {
        runCatching { BitmapFactory.decodeResource(resources, res) }.getOrNull()
    }

    /** Desenha UM frame já resolvido, ancorado pelo PÉ em `footY` -- mesmo conceito de ancoragem
     * já usado pro resto da arena (BattleEngine.floorTopY(), rodada 29). Espelha horizontalmente
     * quando o personagem olha pra esquerda, em vez de precisar de sprite "west" separada.
     *
     * Rodada 15, parte 37 (04/10/2026): CORRIGIDO o bug "personagem parado fica maior que
     * correndo" -- antes, TODO frame (idle 64x64, ou qualquer animação) era esticado pro mesmo
     * retângulo fixo `HERO_SPRITE_DRAW_SIZE`. O PixelLab exporta Idle num canvas 64x64 "justo",
     * mas as animações (Running/Taking_Punch/Falling_Back_Death/Lead_Jab) vêm num canvas 84x84
     * (o personagem precisa de mais espaço pra braço/perna esticados durante o movimento) --
     * esticar os dois pro MESMO quadrado fazia o personagem da animação (mais "pequeno" dentro
     * de um canvas maior) parecer menor na tela que o idle (que já ocupa quase todo o quadrado
     * 64x64). A correção: em vez de um retângulo de destino fixo, aplica um fator de escala
     * ÚNICO (`HERO_SPRITE_DRAW_SIZE / HERO_SPRITE_REFERENCE_PX`) ao tamanho NATIVO de cada
     * bitmap -- aí um canvas 84x84 desenha proporcionalmente maior que um 64x64, mas o
     * PERSONAGEM dentro de cada um (que é do mesmo tamanho real nos dois) acaba saindo do mesmo
     * tamanho na tela, consistente entre parado/andando/golpe/dano/derrota. */
    private fun drawHeroBitmap(
        canvas: Canvas,
        res: Int,
        cx: Float,
        footY: Float,
        facingRight: Boolean,
        // Rodada 15, parte 45 (04/10/2026): generalizado pra aceitar tamanho/sink
        // configuraveis -- o Bulldozer (primeiro INIMIGO com sprite) reusa esta mesma
        // funcao de desenho com valores proprios, em vez de duplicar a logica.
        drawSize: Float = HERO_SPRITE_DRAW_SIZE,
        verticalSink: Float = HERO_SPRITE_VERTICAL_SINK
    ) {
        val bmp = heroBitmap(res) ?: return
        val scale = drawSize / HERO_SPRITE_REFERENCE_PX
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale
        // Rodada 15, parte 43 (04/10/2026): pedido explicito do usuario -- depois do aumento de
        // tamanho da parte 42, a cabeca do heroi passou a invadir a parte de cima do cenario
        // (predio/fundo) mais do que devia ("o personagem ta voltando a subir mais pro cenario
        // doq deveria"). A sprite real (alta, formato humano) estica bem mais pra cima do ponto
        // de ancoragem do que a bolinha antiga (circulo simetrico). Em vez de encolher o espaco
        // de andar (floorTopY()/PLAYER_VISUAL_RADIUS, em BattleEngine.kt -- que tambem define
        // quanto os INIMIGOS podem andar, e mexer lá reduziria o espaco pro vilao/brutamontes
        // tambem, o oposto do que foi pedido), so o DESENHO da sprite é empurrado pra baixo por
        // HERO_SPRITE_VERTICAL_SINK -- devolve a invasao pro mesmo tanto de antes do aumento de
        // tamanho (~88px acima da linha do chao, igual nas partes 36-41), sem tocar em nenhuma
        // fisica/colisao.
        val adjustedFootY = footY + verticalSink
        val dst = android.graphics.RectF(cx - drawW / 2f, adjustedFootY - drawH, cx + drawW / 2f, adjustedFootY)
        canvas.save()
        if (!facingRight) {
            canvas.scale(-1f, 1f, cx, footY)
        }
        canvas.drawBitmap(bmp, null, dst, heroSpritePaint)
        canvas.restore()
    }

    /** Desenha a sprite (se `res` existir) OU cai pra bolinha de sempre (personagem ainda sem
     * arte, ou sprite que falhou ao decodificar) -- usado tanto no jogo normal quanto nas 3
     * fases da transição de morte, pra não perder o fallback em nenhuma delas. `footY` já é a
     * posição ANCORADA (base do pé); a bolinha usa o mesmo ponto, só convertendo pro próprio
     * centro (raio 32 -- mesma distância de `HERO_SPRITE_FOOT_OFFSET`, de propósito). */
    private fun drawFallenOrActive(canvas: Canvas, res: Int?, cx: Float, footY: Float, facingRight: Boolean) {
        if (res != null) {
            drawHeroBitmap(canvas, res, cx, footY, facingRight)
        } else {
            val cy = footY - HERO_SPRITE_FOOT_OFFSET
            canvas.drawCircle(cx, cy, 32f, playerPaint)
            canvas.drawCircle(cx, cy, 32f, playerStrokePaint)
        }
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
        val engineSnapshot = engine
        val shakeActive = engineSnapshot?.bossIntroActive == true
        if (shakeActive) {
            canvas.save()
            canvas.translate(
                (shakeRandom.nextFloat() - 0.5f) * SCREEN_SHAKE_AMPLITUDE * 2f,
                (shakeRandom.nextFloat() - 0.5f) * SCREEN_SHAKE_AMPLITUDE * 2f
            )
        }

        drawScenery(canvas)

        val e = engineSnapshot
        if (e == null || !started) {
            if (shakeActive) canvas.restore()
            return
        }
        val camX = e.cameraX
        val nowMs = System.currentTimeMillis()

        drawSceneryProps(canvas, e)

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

        e.enemies.filter { en -> en.isAlive || (nowMs - en.deathAnimStartMs < (ENEMY_DEATH_LIE_MS + ENEMY_DEATH_BLINK_MS) && enemySpriteSetFor(en.displayName)?.death?.isNotEmpty() == true) }.forEach { enemy ->
            val sx = enemy.x - camX
            if (sx < -90f || sx > width + 90f) return@forEach
            // Rodada 15, parte 42 (04/10/2026): os 3 raios subiram, na mesma leva do aumento
            // do heroi (HERO_SPRITE_DRAW_SIZE, 88->128) -- pedido explicito do usuario pra
            // deixar os personagens maiores sem perder a hierarquia visual: chefao >
            // brutamontes/miniboss > heroi > capanga comum, nessa ordem (o boss ja era sempre o
            // maior circulo, a intencao so ficou mais clara agora).
            // Rodada 15, parte 43 (04/10/2026): reduzidos de novo -- pedido explicito do usuario
            // ("diminui uns 15px de todos os personagens, o vilao quase nem cabe no espaco
            // permitido pra andar de acordo com o cenario"). ~15px de diametro a menos em cada
            // um (raio -7/-8), mantendo a mesma hierarquia de antes. Esses raios continuam
            // valendo mesmo pra inimigos COM sprite (ver abaixo) -- so viram a ancora do pe e a
            // altura da barra de vida, nao mudam de proposito a hierarquia ja calibrada.
            val radius = when {
                enemy.isBoss -> 85f
                enemy.isMiniboss -> 60f
                else -> 28f
            }
            // Rodada 15, parte 45 (04/10/2026): primeiro inimigo com sprite de verdade
            // (Bulldozer) -- se achar um conjunto cadastrado, desenha a sprite real; senao,
            // cai pro circulo colorido de sempre (igual todo o resto dos inimigos ainda sem
            // arte propria).
            // Rodada 15, parte 53 (04/10/2026): TODO inimigo agora passa pela mesma ideia de
            // transicao do heroi ao morrer -- fica "caido" um tempo e depois pisca antes de
            // sumir de vez, em vez de só sumir na hora. Pedido explicito do usuario ("a msm
            // animacao que o personagem do usuario tem quando morre todo mundo tem que ter").
            // Quem tem sprite de queda (Bulldozer/Carnificina) toca os frames de verdade; quem
            // ainda não tem arte (capanga comum) usa a MESMA bolinha de sempre, só parada e
            // depois piscando -- sem precisar de nenhuma arte nova.
            val enemySprite = enemySpriteSetFor(enemy.displayName)
            // Rodada 15, parte 61: CORRIGIDO -- antes todo inimigo com sprite que não fosse
            // chefe caía automaticamente no tamanho de MINIBOSS (só o Bulldozer tinha sprite até
            // agora, então nunca dava pra notar). Com Wrecker/Piledriver/Thunderball (capangas
            // comuns) ganhando sprite nesta parte, eles ficariam do MESMO tamanho do Bulldozer --
            // errado, a hierarquia (ver comentário acima, "brutamontes/miniboss > heroi > capanga
            // comum") precisa de um terceiro tamanho, menor que o do herói.
            val drawSize = when {
                enemy.isBoss -> BOSS_SPRITE_DRAW_SIZE
                enemy.isMiniboss -> ENEMY_MINIBOSS_SPRITE_DRAW_SIZE
                else -> ENEMY_SPRITE_DRAW_SIZE
            }
            if (!enemy.isAlive) {
                val sinceDeath = nowMs - enemy.deathAnimStartMs
                val blinkPhase = sinceDeath >= ENEMY_DEATH_LIE_MS
                val blinkOn = ((sinceDeath / ENEMY_DEATH_BLINK_TOGGLE_MS) % 2L) == 0L
                if (!blinkPhase || blinkOn) {
                    if (enemySprite != null && enemySprite.death.isNotEmpty()) {
                        val footY = enemy.y + radius
                        val frames = enemySprite.death
                        val idx = (sinceDeath / HERO_DEATH_FRAME_MS).toInt().coerceIn(0, frames.size - 1)
                        drawHeroBitmap(canvas, frames[idx], sx, footY, enemy.facingRight, drawSize = drawSize, verticalSink = 0f)
                    } else {
                        val paint = when {
                            enemy.isBoss -> bossPaint
                            enemy.isMiniboss -> minibossPaint
                            else -> enemyPaint
                        }
                        canvas.drawCircle(sx, enemy.y, radius, paint)
                        if (enemy.isBoss) canvas.drawCircle(sx, enemy.y, radius, bossStrokePaint)
                        if (enemy.isMiniboss) canvas.drawCircle(sx, enemy.y, radius, minibossStrokePaint)
                    }
                }
            } else if (enemySprite != null) {
                val footY = enemy.y + radius
                val sinceAttack = nowMs - enemy.lastAttackAtMs
                val sinceHurt = nowMs - enemy.lastHurtAtMs
                val res = when {
                    sinceAttack in 0 until HERO_PUNCH_WINDOW_MS && enemySprite.punch.isNotEmpty() ->
                        enemySprite.punch[(sinceAttack / HERO_PUNCH_FRAME_MS).toInt().coerceIn(0, enemySprite.punch.size - 1)]
                    sinceHurt in 0 until HERO_HURT_WINDOW_MS && enemySprite.hurt.isNotEmpty() ->
                        enemySprite.hurt[(sinceHurt / HERO_HURT_FRAME_MS).toInt().coerceIn(0, enemySprite.hurt.size - 1)]
                    else ->
                        enemySprite.walk[((nowMs / HERO_WALK_FRAME_MS) % enemySprite.walk.size).toInt()]
                }
                drawHeroBitmap(canvas, res, sx, footY, enemy.facingRight, drawSize = drawSize, verticalSink = 0f)
            } else {
                val paint = when {
                    enemy.isBoss -> bossPaint
                    enemy.isMiniboss -> minibossPaint
                    else -> enemyPaint
                }
                canvas.drawCircle(sx, enemy.y, radius, paint)
                if (enemy.isBoss) canvas.drawCircle(sx, enemy.y, radius, bossStrokePaint)
                if (enemy.isMiniboss) canvas.drawCircle(sx, enemy.y, radius, minibossStrokePaint)
            }
        }

        // Rodada 15, parte 37 (04/10/2026): o desenho do jogador agora depende da fase de
        // transição de morte (ver BattleEngine.DeathTransitionPhase) -- pedido explícito do
        // usuário pra uma troca de personagem mais lenta/dramática em vez de instantânea.
        val player = e.player
        val psx = player.x - camX
        val heroSprite = heroSpriteSetFor(player.character.name)
        when (e.deathTransitionPhase) {
            DeathTransitionPhase.DYING -> {
                // Toca a animação de queda UMA VEZ (não repete) e segura no último frame
                // (personagem deitado) até a fase acabar -- ver updateDeathTransition() no
                // engine, que já cuida do tempo total.
                val frames = heroSprite?.death
                val res = if (frames != null && frames.isNotEmpty()) {
                    val elapsed = nowMs - e.deathPhaseStartAtMs
                    val idx = (elapsed / HERO_DEATH_FRAME_MS).toInt().coerceIn(0, frames.size - 1)
                    frames[idx]
                } else null
                drawFallenOrActive(canvas, res, psx, player.y + HERO_SPRITE_FOOT_OFFSET, player.facingRight)
            }
            DeathTransitionPhase.BLINKING -> {
                // Pisca rápido (liga/desliga) em cima do último frame da queda -- efeito
                // clássico de troca de personagem, pedido explícito do usuário.
                val elapsed = nowMs - e.deathPhaseStartAtMs
                val visible = (elapsed / HERO_BLINK_TOGGLE_MS) % 2L == 0L
                if (visible) {
                    val res = heroSprite?.death?.lastOrNull()
                    drawFallenOrActive(canvas, res, psx, player.y + HERO_SPRITE_FOOT_OFFSET, player.facingRight)
                }
            }
            DeathTransitionPhase.DROPPING_IN -> {
                // `e.player` já É o PRÓXIMO personagem aqui (o engine troca ao entrar nesta
                // fase) -- só falta ele visualmente "cair" de cima até a posição de sempre.
                // Easing de aceleração (1 - t²): começa devagar, acelera, como uma queda real.
                val t = e.dropInProgress
                val offsetY = -HERO_DROP_IN_HEIGHT * (1f - t * t)
                drawFallenOrActive(canvas, heroSprite?.idle, psx, player.y + HERO_SPRITE_FOOT_OFFSET + offsetY, player.facingRight)
            }
            DeathTransitionPhase.NONE -> {
                val moving = e.moveX != 0f || e.moveY != 0f
                val sinceHurt = nowMs - e.playerHurtAtMs
                val sinceAttack = nowMs - player.lastAttackAtMs
                val res: Int? = when {
                    heroSprite == null -> null
                    // Rodada 15, parte 38 (04/10/2026): pedido explícito do usuário -- não dá pra
                    // mostrar as duas animações ao mesmo tempo (golpe e levando dano), então
                    // GOLPE tem prioridade. Antes era o contrário (dano por cima de golpe); agora,
                    // se as duas janelas caírem juntas, mostra o personagem atacando.
                    sinceAttack in 0 until HERO_PUNCH_WINDOW_MS && heroSprite.punch.isNotEmpty() -> {
                        val idx = (sinceAttack / HERO_PUNCH_FRAME_MS).toInt().coerceIn(0, heroSprite.punch.size - 1)
                        heroSprite.punch[idx]
                    }
                    sinceHurt in 0 until HERO_HURT_WINDOW_MS && heroSprite.hurt.isNotEmpty() -> {
                        val idx = (sinceHurt / HERO_HURT_FRAME_MS).toInt().coerceIn(0, heroSprite.hurt.size - 1)
                        heroSprite.hurt[idx]
                    }
                    moving -> {
                        val idx = ((nowMs / HERO_WALK_FRAME_MS) % heroSprite.walk.size).toInt()
                        heroSprite.walk[idx]
                    }
                    else -> heroSprite.idle
                }
                drawFallenOrActive(canvas, res, psx, player.y + HERO_SPRITE_FOOT_OFFSET, player.facingRight)
                drawSpecialBurst(canvas, psx, player.y, e.specialEffectAtMs)
                if (e.showAdvanceHint) drawAdvanceArrow(canvas, nowMs)
            }
        }

        if (shakeActive) canvas.restore()
    }

    /** Cenário real (pack CraftPix, uma cena única por variante), ladrilhado e deslizado em
     * loop -- ver comentário de `drawLoopingBackground()` -- conforme `cameraX`, pra acompanhar
     * o mundo largo e contínuo da batalha (seção "fluxo contínuo"). */
    private fun drawScenery(canvas: Canvas) {
        if (width <= 0 || height <= 0) {
            canvas.drawColor(Color.parseColor("#101A35"))
            return
        }
        val e = engine
        ensureSceneryLoaded(e?.currentVariant() ?: 0)
        ensurePropSpritesLoaded()
        val bg = backgroundBitmap

        if (bg != null) {
            drawLoopingBackground(canvas, bg, e)
        } else {
            drawFallbackSky(canvas, height.toFloat())
        }
    }

    /** Desenha [bmp] (a cena inteira de uma variante) ladrilhada horizontalmente, deslizando
     * 1:1 com `cameraX` -- é o próprio chão por onde os personagens andam, não uma camada de
     * parallax distante, então não faz sentido deslizar mais devagar que o mundo. Quando uma
     * cópia sai da tela pela esquerda, a próxima (a MESMA imagem de novo) já entra colada pela
     * direita -- é o loop "começa de novo do início" que o usuário pediu, e ao ladrilhar (em vez
     * de desenhar uma vez só ampliada, como a parte 18 fazia) a tela fica sempre coberta por
     * inteiro, não importa a proporção do aparelho (ver comentário da classe, parte 19). */
    private fun drawLoopingBackground(canvas: Canvas, bmp: Bitmap, e: BattleEngine?) {
        if (bmp.height <= 0 || bmp.width <= 0) return
        val scale = height / bmp.height.toFloat()
        val scaledW = bmp.width * scale
        if (scaledW <= 0f) return

        val cameraX = (e?.cameraX ?: 0f).coerceAtLeast(0f)
        // cameraX já é sempre >= 0 (clampada em BattleEngine.updateCamera), então o módulo
        // Kotlin cai certinho entre 0 (inclusive) e scaledW (exclusivo) -- sem precisar tratar caso negativo.
        val startOffset = -(cameraX % scaledW)

        canvas.save()
        canvas.clipRect(0, 0, width, height)
        var x = startOffset
        while (x < width) {
            sceneryMatrix.reset()
            sceneryMatrix.setScale(scale, scale)
            sceneryMatrix.postTranslate(x, 0f)
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

    /** Desenha [bmp] escalado (mantendo a proporção original, sem distorcer) até ficar com
     * [targetHeight] de altura, CENTRADO em ([sx], [sy]) -- mesma convenção que já valia pros
     * círculos de jogador/inimigo (`canvas.drawCircle(sx, y, ...)`), pra tudo continuar alinhado
     * na mesma faixa de chão sem um objeto "flutuar" acima do outro por causa de âncoras
     * diferentes. Reaproveitada tanto por `drawDestructible()` quanto por `drawSceneryProps()`.
     *
     * Rodada 15, parte 19 (ajuste do mesmo dia): [bmp] nulo agora desenha [fallbackPaint] num
     * retângulo simples do mesmo tamanho, em vez de simplesmente não desenhar nada -- o objeto
     * NUNCA mais fica invisível, mesmo se o sprite falhar ao decodificar (ex: recurso novo que um
     * build incremental não empacotou).
     *
     * Rodada 15, parte 20 (30/09/2026): [sy] agora é o PÉ do objeto (base onde ele encosta no
     * chão), não mais o centro -- antes o sprite era centralizado em [sy], então um sprite alto
     * perto do fundo da faixa andável (que ficou bem mais estreita na parte 20) tinha a metade
     * de baixo cortada pela borda da própria View (Canvas recorta tudo que sai do retângulo da
     * View). Ancorando pelo pé, o sprite só cresce pra CIMA a partir do ponto de chão -- nunca
     * mais passa do [sy], então nunca mais estoura a borda de baixo. Pedido do usuário: "tem
     * vezes que estao cortadas". */
    private fun drawGroundSprite(
        canvas: Canvas,
        bmp: Bitmap?,
        sx: Float,
        sy: Float,
        targetHeight: Float,
        fallbackPaint: Paint
    ) {
        if (bmp == null || bmp.width <= 0 || bmp.height <= 0) {
            val halfW = targetHeight * 0.55f
            canvas.drawRoundRect(sx - halfW, sy - targetHeight, sx + halfW, sy, 6f, 6f, fallbackPaint)
            return
        }
        val scale = targetHeight / bmp.height.toFloat()
        val w = bmp.width * scale
        propMatrix.reset()
        propMatrix.setScale(scale, scale)
        propMatrix.postTranslate(sx - w / 2f, sy - targetHeight)
        canvas.drawBitmap(bmp, propMatrix, null)
    }

    /** Caixa/barril (rodada 15, parte 19, 30/09/2026): eram formas geométricas desenhadas na mão
     * -- pedido do usuário ("na pasta que te anexei tem um modelo de caixas, usa esses pequenos
     * png") trocou o corpo pelos sprites reais do pack CraftPix (`ensurePropSpritesLoaded()`).
     * BARRIL usa a pilha de pneus do pack (não tem barril de madeira nas cenas baixadas) -- lê
     * como objeto cilíndrico destrutível igual um barril leria, só que com identidade visual
     * própria (borracha escura), o que também ajuda a diferenciar os dois tipos à distância.
     * Recebe [sx] já convertido pra coordenada de TELA (mundo menos a câmera). */
    private fun drawDestructible(canvas: Canvas, sx: Float, sy: Float, kind: DestructibleObject.Kind) {
        val bmp = if (kind == DestructibleObject.Kind.CAIXA) cratePropBitmap else barrelPropBitmap
        val fallback = if (kind == DestructibleObject.Kind.CAIXA) cratePropFallbackPaint else barrelPropFallbackPaint
        drawGroundSprite(canvas, bmp, sx, sy, DESTRUCTIBLE_SPRITE_HEIGHT, fallback)
    }

    /** Decoração fixa do cenário (rodada 15, parte 19) -- sem colisão, só visual, pra quebrar a
     * sensação de repetição do fundo em loop (pedido do usuário: "deixar o cenario menos
     * possivel infinito... pra nao ficar cansativo jogar"). Culling simples por posição de tela,
     * igual já era feito pra destrutíveis/inimigos. */
    private fun drawSceneryProps(canvas: Canvas, e: BattleEngine) {
        val bitmaps = decoBitmaps
        val camX = e.cameraX
        e.sceneryProps.forEach { prop ->
            val sx = prop.x - camX
            if (sx < -140f || sx > width + 140f) return@forEach
            val bmp = bitmaps?.getOrNull(prop.kind)
            val targetHeight = DECO_SPRITE_HEIGHTS.getOrElse(prop.kind) { DECO_SPRITE_HEIGHTS[0] }
            drawGroundSprite(canvas, bmp, sx, prop.y, targetHeight, decoPropFallbackPaint)
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

    /** Rodada 15, parte 33 (03/10/2026): agora pisca (liga/desliga a cada ~420ms, baseado em
     * `nowMs`) -- pedido explícito do usuário ("seta meio que amarela piscando"). Continua
     * amarela, sem texto, tamanho médio estilo Streets of Rage (só uma seta simples apontando
     * pra direita) -- só o gatilho (`showAdvanceHint`, calculado em BattleEngine) que mudou, de
     * delay fixo pra "jogador parado". */
    private fun drawAdvanceArrow(canvas: Canvas, nowMs: Long) {
        val blinkOn = (nowMs / 420L) % 2L == 0L
        if (!blinkOn) return
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
        private const val SPECIAL_EFFECT_DURATION_MS = 420L

        // Animação de quebra de caixa/barril (rodada 13): tempo tremendo + tempo estilhaçando.
        private const val SHAKE_DURATION_MS = 110L
        private const val BREAK_ANIM_DURATION_MS = 480L

        // Rodada 15, parte 19 (30/09/2026): altura-alvo (em px de tela) dos sprites de
        // caixa/barril e de decoração -- ver drawGroundSprite(). Destrutíveis ficam perto do
        // diâmetro do círculo de personagem (64px, raio 32) pra não dominar a leitura da cena;
        // decoração pode ser um pouco maior, já que fica mais "atrás", no papel de cenário.
        // Rodada 15, parte 20 (ajuste do mesmo dia): aumentado -- pedido do usuário ("o tamanho
        // das coisas ta mto pequeno em relação a proporção com o cenario").
        private const val DESTRUCTIBLE_SPRITE_HEIGHT = 112f
        // Rodada 15, parte 20: virou um valor por tipo (índice = SceneryProp.kind, mesma ordem
        // de `decoRes` acima) em vez de uma altura única -- lojinha/cabine policial/fonte são
        // estruturas grandes no sprite original, hidrante/mesinha são pequenos; um valor só
        // deixava um dos dois grupos errado.
        private val DECO_SPRITE_HEIGHTS = floatArrayOf(
            96f,  // 0 hidrante
            168f, // 1 cabine telefônica (callbox)
            120f, // 2 mesinha de café
            176f, // 3 cabine policial
            156f  // 4 fonte
        )

        // Rodada 15, parte 34 (03/10/2026): amplitude (px) do tremor de tela enquanto o chefe
        // desliza de fora da tela -- pedido explícito do usuário.
        private const val SCREEN_SHAKE_AMPLITUDE = 8f

        // Rodada 15, parte 36 (03/10/2026): tamanho de desenho (px de tela) da sprite de
        // personagem -- um pouco maior que o diâmetro da bolinha antiga (64px) pra dar presença
        // visual, já que agora é um personagem de verdade e não só um círculo de cor.
        // Rodada 15, parte 42 (04/10/2026): aumentado de 88 pra 128 -- pedido explicito do
        // usuario ("os personagens no campo de batalha deveriam ser maior, ta parecendo bebe
        // comparado com o cenario"). Os raios dos inimigos (ver radius no when de onDraw(), mais
        // acima no arquivo) subiram na mesma leva, de proposito mantendo brutamontes/chefao
        // maiores que o heroi (pedido explicito: "deixa de um tamanho que o vilao e brutamontes
        // ainda fiquem maior mas sem quebrar a tela de tao grandes").
        // Rodada 15, parte 43 (04/10/2026): reduzido de 128 pra 113 (~15px a menos) -- pedido
        // explicito do usuario, mesma leva da reducao dos raios dos inimigos acima.
        private const val HERO_SPRITE_DRAW_SIZE = 113f
        // Rodada 15, parte 45 (04/10/2026): tamanho de desenho do primeiro INIMIGO com
        // sprite de verdade (Bulldozer/miniboss) -- mantem a mesma referencia que o circulo
        // antigo tinha (raio 60f = diametro 120px), preservando a hierarquia visual ja
        // pedida nas partes 42/43 (chefao > miniboss/brutamontes > heroi > capanga comum).
        // Rodada 15, parte 50 (04/10/2026): aumentado de 120 pra 145 -- pedido explicito do
        // usuario pra garantir que o brutamontes fique visivelmente maior que o heroi (113f) --
        // antes a diferenca era pequena demais pra notar de verdade.
        private const val ENEMY_MINIBOSS_SPRITE_DRAW_SIZE = 145f
        // Rodada 15, parte 61: capanga comum com sprite -- menor que o herói (113f) de
        // propósito, pra manter a hierarquia de tamanho já calibrada.
        private const val ENEMY_SPRITE_DRAW_SIZE = 100f
        // Chefao (Carnificina) -- mesma referencia que o circulo antigo tinha (raio 85f =
        // diametro 170px), preservando a hierarquia chefao > miniboss > heroi > capanga.
        private const val BOSS_SPRITE_DRAW_SIZE = 175f  // Rodada 15, parte 54: +5px a pedido do usuario
        // Rodada 15, parte 53 (04/10/2026): trocado o unico ENEMY_DEATH_ANIM_MS por duas fases
        // (caido + piscando), igual em espirito ao DEATH_LIE_MS/DEATH_BLINK_MS do heroi --
        // pedido explicito do usuario pra todo mundo ter a mesma animacao de morte.
        private const val ENEMY_DEATH_LIE_MS = 650L
        private const val ENEMY_DEATH_BLINK_MS = 480L
        private const val ENEMY_DEATH_BLINK_TOGGLE_MS = 90L
        // Mesma ideia de ancoragem pelo PÉ do resto da arena -- a bolinha antiga tinha raio 32,
        // então a base dela ficava em `player.y + 32`; a sprite usa o mesmo deslocamento pra não
        // mudar a posição percebida do personagem no chão.
        private const val HERO_SPRITE_FOOT_OFFSET = 32f
        // Rodada 15, parte 43 (04/10/2026): quanto o DESENHO da sprite (só drawHeroBitmap, não
        // a ancora logica do pé acima) é empurrado pra baixo em relação a footY -- ver o
        // comentário grande em drawHeroBitmap() pro motivo (devolver a invasão da cabeça no
        // cenário pro nível de antes da parte 42, sem encolher a área de andar de ninguém).
        private const val HERO_SPRITE_VERTICAL_SINK = 25f
        // Troca de frame do ciclo de andar (8 frames) a cada 90ms -- cadência rápida o bastante
        // pra não parecer travado, sem ficar "tremido" demais.
        private const val HERO_WALK_FRAME_MS = 90L

        // Rodada 15, parte 37 (04/10/2026): canvas nominal que o PixelLab usa pro Idle (64x64) --
        // ver a nota grande em drawHeroBitmap() sobre por que a escala é relativa a ESTE número,
        // e não um retângulo de destino fixo (era a causa do personagem parado parecer maior
        // que correndo).
        private const val HERO_SPRITE_REFERENCE_PX = 64f

        // Golpe (Lead_Jab, 3 frames) e levando dano (Taking_Punch, 6 frames): cadência de frame e
        // janela total (frames * cadência) em que a animação correspondente substitui parado/
        // andando -- ver onDraw(). Levar dano tem prioridade sobre golpe (mais importante ler
        // visualmente que acabou de ser atingido do que o próprio golpe que talvez já tenha
        // acertado antes).
        private const val HERO_PUNCH_FRAME_MS = 90L
        private const val HERO_PUNCH_WINDOW_MS = 270L
        private const val HERO_HURT_FRAME_MS = 90L
        private const val HERO_HURT_WINDOW_MS = 540L

        // Derrota (Falling_Back_Death, 7 frames) -- mesma cadência das outras animações.
        private const val HERO_DEATH_FRAME_MS = 90L
        // Pisca-pisca da fase BLINKING (transição de morte) -- liga/desliga a cada 90ms.
        private const val HERO_BLINK_TOGGLE_MS = 90L
        // Altura (px de tela) de onde o próximo personagem "cai" na fase DROPPING_IN.
        private const val HERO_DROP_IN_HEIGHT = 260f
    }
}
