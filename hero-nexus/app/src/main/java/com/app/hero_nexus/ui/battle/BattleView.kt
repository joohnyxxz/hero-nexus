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

class BattleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var engine: BattleEngine? = null
    private var started = false
    private var running = false
    private var lastFrameNs = 0L

    private val shardLightPaint = Paint().apply { color = Color.parseColor("#C98F52"); isAntiAlias = true }
    private val shardDarkPaint = Paint().apply { color = Color.parseColor("#2B1B0E"); isAntiAlias = true }

    private val cratePropFallbackPaint = Paint().apply { color = Color.parseColor("#C98F52"); isAntiAlias = true }
    private val barrelPropFallbackPaint = Paint().apply { color = Color.parseColor("#3A3A3A"); isAntiAlias = true }
    private val decoPropFallbackPaint = Paint().apply { color = Color.parseColor("#6B6B6B"); isAntiAlias = true }
    private val playerPaint = Paint().apply { color = Color.parseColor("#FFC400") }
    private val playerStrokePaint = Paint().apply {
        color = Color.parseColor("#7A1414"); style = Paint.Style.STROKE; strokeWidth = 5f
    }

    private val heroSpritePaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val heroBitmapCache = HashMap<Int, Bitmap?>()
    private val enemyPaint = Paint().apply { color = Color.parseColor("#E62429") }
    private val bossPaint = Paint().apply { color = Color.parseColor("#8C1418") }
    private val bossStrokePaint = Paint().apply {
        color = Color.parseColor("#FFC400"); style = Paint.Style.STROKE; strokeWidth = 6f
    }

    private val minibossPaint = Paint().apply { color = Color.parseColor("#5E2E8C") }
    private val minibossStrokePaint = Paint().apply {
        color = Color.parseColor("#B14BF4"); style = Paint.Style.STROKE; strokeWidth = 4.5f
    }

    private val shakeRandom = Random(System.nanoTime())
    private val healthBgPaint = Paint().apply { color = Color.parseColor("#40000000") }
    private val healthFgPaint = Paint().apply { color = Color.parseColor("#3CCB6E") }
    private val healthFgLowPaint = Paint().apply { color = Color.parseColor("#E62429") }

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

    private var backgroundBitmap: Bitmap? = null
    private var loadedVariant = -1
    private val sceneryMatrix = Matrix()
    private val propMatrix = Matrix()

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
            10 -> R.drawable.img_battle_scenery_apoc4
            else -> R.drawable.img_battle_scenery_city1
        }
        backgroundBitmap = runCatching { BitmapFactory.decodeResource(resources, res) }.getOrNull()
    }

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

    private val DEFAULT_HERO_SPRITE = HeroSpriteSet(
        idle = R.drawable.img_hero_default_idle,
        walk = intArrayOf(
            R.drawable.img_hero_default_walk_0,
            R.drawable.img_hero_default_walk_1,
            R.drawable.img_hero_default_walk_2,
            R.drawable.img_hero_default_walk_3,
            R.drawable.img_hero_default_walk_4,
            R.drawable.img_hero_default_walk_5,
            R.drawable.img_hero_default_walk_6,
            R.drawable.img_hero_default_walk_7
        ),
        punch = intArrayOf(
            R.drawable.img_hero_default_punch_0,
            R.drawable.img_hero_default_punch_1,
            R.drawable.img_hero_default_punch_2
        ),
        hurt = intArrayOf(
            R.drawable.img_hero_default_hurt_0,
            R.drawable.img_hero_default_hurt_1,
            R.drawable.img_hero_default_hurt_2,
            R.drawable.img_hero_default_hurt_3,
            R.drawable.img_hero_default_hurt_4,
            R.drawable.img_hero_default_hurt_5
        ),
        death = intArrayOf(
            R.drawable.img_hero_default_death_0,
            R.drawable.img_hero_default_death_1,
            R.drawable.img_hero_default_death_2,
            R.drawable.img_hero_default_death_3,
            R.drawable.img_hero_default_death_4,
            R.drawable.img_hero_default_death_5,
            R.drawable.img_hero_default_death_6
        )
    )

    private val SKIN_SPRITES: List<Pair<String, HeroSpriteSet>> = listOf(
        "_symbiote" to HeroSpriteSet(
            idle = R.drawable.img_skin_spidermansymbiote_idle,
            walk = intArrayOf(
                R.drawable.img_skin_spidermansymbiote_walk_0,
                R.drawable.img_skin_spidermansymbiote_walk_1,
                R.drawable.img_skin_spidermansymbiote_walk_2,
                R.drawable.img_skin_spidermansymbiote_walk_3,
                R.drawable.img_skin_spidermansymbiote_walk_4,
                R.drawable.img_skin_spidermansymbiote_walk_5,
                R.drawable.img_skin_spidermansymbiote_walk_6,
                R.drawable.img_skin_spidermansymbiote_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_skin_spidermansymbiote_punch_0,
                R.drawable.img_skin_spidermansymbiote_punch_1,
                R.drawable.img_skin_spidermansymbiote_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_skin_spidermansymbiote_hurt_0,
                R.drawable.img_skin_spidermansymbiote_hurt_1,
                R.drawable.img_skin_spidermansymbiote_hurt_2,
                R.drawable.img_skin_spidermansymbiote_hurt_3,
                R.drawable.img_skin_spidermansymbiote_hurt_4,
                R.drawable.img_skin_spidermansymbiote_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_skin_spidermansymbiote_death_0,
                R.drawable.img_skin_spidermansymbiote_death_1,
                R.drawable.img_skin_spidermansymbiote_death_2,
                R.drawable.img_skin_spidermansymbiote_death_3,
                R.drawable.img_skin_spidermansymbiote_death_4,
                R.drawable.img_skin_spidermansymbiote_death_5,
                R.drawable.img_skin_spidermansymbiote_death_6
            )
        ),
        "_zombie" to HeroSpriteSet(
            idle = R.drawable.img_skin_capamericazombie_idle,
            walk = intArrayOf(
                R.drawable.img_skin_capamericazombie_walk_0,
                R.drawable.img_skin_capamericazombie_walk_1,
                R.drawable.img_skin_capamericazombie_walk_2,
                R.drawable.img_skin_capamericazombie_walk_3,
                R.drawable.img_skin_capamericazombie_walk_4,
                R.drawable.img_skin_capamericazombie_walk_5,
                R.drawable.img_skin_capamericazombie_walk_6,
                R.drawable.img_skin_capamericazombie_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_skin_capamericazombie_punch_0,
                R.drawable.img_skin_capamericazombie_punch_1,
                R.drawable.img_skin_capamericazombie_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_skin_capamericazombie_hurt_0,
                R.drawable.img_skin_capamericazombie_hurt_1,
                R.drawable.img_skin_capamericazombie_hurt_2,
                R.drawable.img_skin_capamericazombie_hurt_3,
                R.drawable.img_skin_capamericazombie_hurt_4,
                R.drawable.img_skin_capamericazombie_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_skin_capamericazombie_death_0,
                R.drawable.img_skin_capamericazombie_death_1,
                R.drawable.img_skin_capamericazombie_death_2,
                R.drawable.img_skin_capamericazombie_death_3,
                R.drawable.img_skin_capamericazombie_death_4,
                R.drawable.img_skin_capamericazombie_death_5,
                R.drawable.img_skin_capamericazombie_death_6
            )
        ),
        "_mark1" to HeroSpriteSet(
            idle = R.drawable.img_skin_ironmanmark1_idle,
            walk = intArrayOf(
                R.drawable.img_skin_ironmanmark1_walk_0,
                R.drawable.img_skin_ironmanmark1_walk_1,
                R.drawable.img_skin_ironmanmark1_walk_2,
                R.drawable.img_skin_ironmanmark1_walk_3,
                R.drawable.img_skin_ironmanmark1_walk_4,
                R.drawable.img_skin_ironmanmark1_walk_5,
                R.drawable.img_skin_ironmanmark1_walk_6,
                R.drawable.img_skin_ironmanmark1_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_skin_ironmanmark1_punch_0,
                R.drawable.img_skin_ironmanmark1_punch_1,
                R.drawable.img_skin_ironmanmark1_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_skin_ironmanmark1_hurt_0,
                R.drawable.img_skin_ironmanmark1_hurt_1,
                R.drawable.img_skin_ironmanmark1_hurt_2,
                R.drawable.img_skin_ironmanmark1_hurt_3,
                R.drawable.img_skin_ironmanmark1_hurt_4,
                R.drawable.img_skin_ironmanmark1_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_skin_ironmanmark1_death_0,
                R.drawable.img_skin_ironmanmark1_death_1,
                R.drawable.img_skin_ironmanmark1_death_2,
                R.drawable.img_skin_ironmanmark1_death_3,
                R.drawable.img_skin_ironmanmark1_death_4,
                R.drawable.img_skin_ironmanmark1_death_5,
                R.drawable.img_skin_ironmanmark1_death_6
            )
        )
    )

    private fun heroSpriteSetFor(name: String, equippedSkinId: String? = null): HeroSpriteSet? {

        equippedSkinId?.let { id ->
            SKIN_SPRITES.firstOrNull { (suffix, _) -> id.endsWith(suffix) }?.let { return it.second }
        }
        val key = name.lowercase()
        HERO_SPRITES.firstOrNull { (aliases, _) -> aliases.any { key.contains(it) } }?.let { return it.second }

        val enemySet = ENEMY_SPRITES.firstOrNull { (aliases, _) -> aliases.any { key.contains(it) } }?.second
        if (enemySet != null) {
            return HeroSpriteSet(enemySet.idle, enemySet.walk, enemySet.punch, enemySet.hurt, enemySet.death)
        }

        return DEFAULT_HERO_SPRITE
    }

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

        listOf("thanos") to EnemySpriteSet(
            idle = R.drawable.img_enemy_thanos_idle,
            walk = intArrayOf(
                R.drawable.img_enemy_thanos_walk_0,
                R.drawable.img_enemy_thanos_walk_1,
                R.drawable.img_enemy_thanos_walk_2,
                R.drawable.img_enemy_thanos_walk_3,
                R.drawable.img_enemy_thanos_walk_4,
                R.drawable.img_enemy_thanos_walk_5,
                R.drawable.img_enemy_thanos_walk_6,
                R.drawable.img_enemy_thanos_walk_7
            ),
            punch = intArrayOf(
                R.drawable.img_enemy_thanos_punch_0,
                R.drawable.img_enemy_thanos_punch_1,
                R.drawable.img_enemy_thanos_punch_2
            ),
            hurt = intArrayOf(
                R.drawable.img_enemy_thanos_hurt_0,
                R.drawable.img_enemy_thanos_hurt_1,
                R.drawable.img_enemy_thanos_hurt_2,
                R.drawable.img_enemy_thanos_hurt_3,
                R.drawable.img_enemy_thanos_hurt_4,
                R.drawable.img_enemy_thanos_hurt_5
            ),
            death = intArrayOf(
                R.drawable.img_enemy_thanos_death_0,
                R.drawable.img_enemy_thanos_death_1,
                R.drawable.img_enemy_thanos_death_2,
                R.drawable.img_enemy_thanos_death_3,
                R.drawable.img_enemy_thanos_death_4,
                R.drawable.img_enemy_thanos_death_5,
                R.drawable.img_enemy_thanos_death_6
            )
        ),
    )

    private val DEFAULT_ENEMY_SPRITE = EnemySpriteSet(
        idle = R.drawable.img_enemy_default_idle,
        walk = intArrayOf(
            R.drawable.img_enemy_default_walk_0,
            R.drawable.img_enemy_default_walk_1,
            R.drawable.img_enemy_default_walk_2,
            R.drawable.img_enemy_default_walk_3,
            R.drawable.img_enemy_default_walk_4,
            R.drawable.img_enemy_default_walk_5,
            R.drawable.img_enemy_default_walk_6,
            R.drawable.img_enemy_default_walk_7
        ),
        punch = intArrayOf(
            R.drawable.img_enemy_default_punch_0,
            R.drawable.img_enemy_default_punch_1,
            R.drawable.img_enemy_default_punch_2
        ),
        hurt = intArrayOf(
            R.drawable.img_enemy_default_hurt_0,
            R.drawable.img_enemy_default_hurt_1,
            R.drawable.img_enemy_default_hurt_2,
            R.drawable.img_enemy_default_hurt_3,
            R.drawable.img_enemy_default_hurt_4,
            R.drawable.img_enemy_default_hurt_5
        ),
        death = intArrayOf(
            R.drawable.img_enemy_default_death_0,
            R.drawable.img_enemy_default_death_1,
            R.drawable.img_enemy_default_death_2,
            R.drawable.img_enemy_default_death_3,
            R.drawable.img_enemy_default_death_4,
            R.drawable.img_enemy_default_death_5,
            R.drawable.img_enemy_default_death_6
        )
    )

    private fun enemySpriteSetFor(name: String, useDefaultFallback: Boolean = false): EnemySpriteSet? {
        val key = name.lowercase()
        val found = ENEMY_SPRITES.firstOrNull { (aliases, _) -> aliases.any { key.contains(it) } }?.second
        if (found != null) return found
        return if (useDefaultFallback) DEFAULT_ENEMY_SPRITE else null
    }

    private fun heroBitmap(res: Int): Bitmap? = heroBitmapCache.getOrPut(res) {
        runCatching { BitmapFactory.decodeResource(resources, res) }.getOrNull()
    }

    private fun drawHeroBitmap(
        canvas: Canvas,
        res: Int,
        cx: Float,
        footY: Float,
        facingRight: Boolean,

        drawSize: Float = HERO_SPRITE_DRAW_SIZE,
        verticalSink: Float = HERO_SPRITE_VERTICAL_SINK
    ) {
        val bmp = heroBitmap(res) ?: return
        val scale = drawSize / HERO_SPRITE_REFERENCE_PX
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale

        val adjustedFootY = footY + verticalSink
        val dst = android.graphics.RectF(cx - drawW / 2f, adjustedFootY - drawH, cx + drawW / 2f, adjustedFootY)
        canvas.save()
        if (!facingRight) {
            canvas.scale(-1f, 1f, cx, footY)
        }
        canvas.drawBitmap(bmp, null, dst, heroSpritePaint)
        canvas.restore()
    }

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

    fun triggerSpecial() {
        engine?.specialRequested = true
    }

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

            val radius = when {
                enemy.isBoss -> 85f
                enemy.isMiniboss -> 60f
                else -> 28f
            }

            val enemySprite = enemySpriteSetFor(enemy.displayName, useDefaultFallback = enemy.isBoss)

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

        val player = e.player
        val psx = player.x - camX
        val heroSprite = heroSpriteSetFor(player.character.name, player.character.equippedSkinId)
        when (e.deathTransitionPhase) {
            DeathTransitionPhase.DYING -> {

                val frames = heroSprite?.death
                val res = if (frames != null && frames.isNotEmpty()) {
                    val elapsed = nowMs - e.deathPhaseStartAtMs
                    val idx = (elapsed / HERO_DEATH_FRAME_MS).toInt().coerceIn(0, frames.size - 1)
                    frames[idx]
                } else null
                drawFallenOrActive(canvas, res, psx, player.y + HERO_SPRITE_FOOT_OFFSET, player.facingRight)
            }
            DeathTransitionPhase.BLINKING -> {

                val elapsed = nowMs - e.deathPhaseStartAtMs
                val visible = (elapsed / HERO_BLINK_TOGGLE_MS) % 2L == 0L
                if (visible) {
                    val res = heroSprite?.death?.lastOrNull()
                    drawFallenOrActive(canvas, res, psx, player.y + HERO_SPRITE_FOOT_OFFSET, player.facingRight)
                }
            }
            DeathTransitionPhase.DROPPING_IN -> {

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

    private fun drawLoopingBackground(canvas: Canvas, bmp: Bitmap, e: BattleEngine?) {
        if (bmp.height <= 0 || bmp.width <= 0) return
        val scale = height / bmp.height.toFloat()
        val scaledW = bmp.width * scale
        if (scaledW <= 0f) return

        val cameraX = (e?.cameraX ?: 0f).coerceAtLeast(0f)

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

    private fun drawDestructible(canvas: Canvas, sx: Float, sy: Float, kind: DestructibleObject.Kind) {
        val bmp = if (kind == DestructibleObject.Kind.CAIXA) cratePropBitmap else barrelPropBitmap
        val fallback = if (kind == DestructibleObject.Kind.CAIXA) cratePropFallbackPaint else barrelPropFallbackPaint
        drawGroundSprite(canvas, bmp, sx, sy, DESTRUCTIBLE_SPRITE_HEIGHT, fallback)
    }

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
            val py = sy + (Math.sin(rad) * dist).toFloat() + t * t * 50f
            val paint = if (i % 2 == 0) shardLightPaint else shardDarkPaint
            paint.alpha = alpha
            canvas.save()
            canvas.rotate(shard.spin * t, px, py)
            canvas.drawRect(px - shard.size / 2f, py - shard.size / 2f, px + shard.size / 2f, py + shard.size / 2f, paint)
            canvas.restore()
        }
    }

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

        private const val SHAKE_DURATION_MS = 110L
        private const val BREAK_ANIM_DURATION_MS = 480L

        private const val DESTRUCTIBLE_SPRITE_HEIGHT = 112f

        private val DECO_SPRITE_HEIGHTS = floatArrayOf(
            96f,
            168f,
            120f,
            176f,
            156f
        )

        private const val SCREEN_SHAKE_AMPLITUDE = 8f

        private const val HERO_SPRITE_DRAW_SIZE = 113f

        private const val ENEMY_MINIBOSS_SPRITE_DRAW_SIZE = 145f

        private const val ENEMY_SPRITE_DRAW_SIZE = 100f

        private const val BOSS_SPRITE_DRAW_SIZE = 175f

        private const val ENEMY_DEATH_LIE_MS = 650L
        private const val ENEMY_DEATH_BLINK_MS = 480L
        private const val ENEMY_DEATH_BLINK_TOGGLE_MS = 90L

        private const val HERO_SPRITE_FOOT_OFFSET = 32f

        private const val HERO_SPRITE_VERTICAL_SINK = 25f

        private const val HERO_WALK_FRAME_MS = 90L

        private const val HERO_SPRITE_REFERENCE_PX = 64f

        private const val HERO_PUNCH_FRAME_MS = 90L
        private const val HERO_PUNCH_WINDOW_MS = 270L
        private const val HERO_HURT_FRAME_MS = 90L
        private const val HERO_HURT_WINDOW_MS = 540L

        private const val HERO_DEATH_FRAME_MS = 90L

        private const val HERO_BLINK_TOGGLE_MS = 90L

        private const val HERO_DROP_IN_HEIGHT = 260f

        private val CHARACTERS_WITH_SPECIFIC_SPRITE = setOf(
            "spider-man", "spiderman",
            "captain america", "capitao america", "capitão america",
            "wolverine",
            "carnage", "carnificina",
            "iron man", "homem de ferro",
            "bulldozer",
            "wrecker",
            "piledriver",
            "thunderball",
            "lizard", "lagarto",
            "doom", "destino",
            "thanos"
        )

        fun hasSpecificSprite(name: String): Boolean {
            val key = name.lowercase()
            return CHARACTERS_WITH_SPECIFIC_SPRITE.any { key.contains(it) }
        }
    }
}
