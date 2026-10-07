package com.app.hero_nexus.ui.battle

import com.app.hero_nexus.data.model.BattleResult
import com.app.hero_nexus.data.model.ChestType
import com.app.hero_nexus.data.model.Character
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.random.Random

enum class BattleState { RUNNING, VICTORY, DEFEAT }

/** Rodada 15, parte 37 (04/10/2026): fases da transição de troca de personagem (ver campos em
 * BattleEngine) -- NONE quando não tem transição rolando. */
enum class DeathTransitionPhase { NONE, DYING, BLINKING, DROPPING_IN }

interface BattleListener {
    fun onCharacterDefeated(name: String)
    fun onNextCharacterEnters(name: String)
    fun onBossIncoming(name: String)
    fun onBossDefeated(name: String)
    /** Miniboss (capanga nomeado, mais forte) derrotado no meio de uma leva -- dispara um flash
     * de tela preta rápido (seção "transição nostálgica", feedback 01/09: "sabe em golden axe?
     * vc mata um vilão, aparece uma tela preta mas ainda continua na batalha"), mas a luta NÃO
     * termina, diferente de onBossDefeated. */
    fun onMinibossDefeated(name: String)
    fun onCratesOrCoins() // sinaliza para o HUD atualizar contadores
    /** Comida quebrada de uma caixa/barril -- agora só GUARDA no slot (não cura na hora, seção
     * "loot v2": "não necessariamente eu peguei comida que eu quero comer na hora"). */
    fun onFoodPickup(amount: Int)
    /** O jogador decidiu comer o que tava guardado no slot -- aí sim cura. */
    fun onFoodConsumed(amount: Int)
    /** O drop raríssimo (0,2%) -- personagem bônus, desbloqueado de verdade no fim da batalha. */
    fun onBonusCharacterPickup()
    /** Trecho da arena limpo (todos os inimigos daquela leva derrotados) -- mostra a seta de avançar
     * (mas não bloqueia o jogador de já seguir andando, seção "fluxo contínuo"). */
    fun onWaveCleared(wave: Int, totalWaves: Int)
    /** Jogador cruzou pro próximo trecho -- some a seta, mostra um banner de progresso. */
    fun onWaveAdvanced(wave: Int, totalWaves: Int)
    /** Especial usado -- pra tocar um efeito/banner na tela. */
    fun onSpecialUsed()
    fun onBattleEnded(result: BattleResult)
}

/**
 * Lógica pura da batalha (sem depender de Android View), estilo beat 'em up (seção 12):
 * o jogador controla um personagem por vez, inimigos avançam, objetos do cenário podem ser
 * quebrados, e quando o personagem atual morre o próximo do time entra (seção 13).
 *
 * Seção "fluxo contínuo" (feedback do usuário 31/08 -- "igual Golden Axe e Streets of Rage, você
 * anda e a câmera segue, não é pra spawnar de novo na tela só que em outra seção"): a arena inteira
 * agora é um MUNDO largo e contínuo (`worldWidth`), maior que a tela. `x` de todo mundo (jogador,
 * inimigos, caixas) é coordenada de MUNDO. `cameraX` é a janela de visão que acompanha o jogador;
 * a BattleView é quem subtrai `cameraX` na hora de desenhar pra saber a posição na TELA. Trocar de
 * personagem (time acabou de vida) não teleporta pra outro lugar -- o próximo entra bem onde o
 * anterior caiu, o fluxo nunca "recomeça" visualmente.
 */
class BattleEngine(
    private val team: List<Character>,
    private val boss: Character?,
    private val listener: BattleListener
) {
    var arenaWidth: Float = 1000f
    var arenaHeight: Float = 600f

    private var teamIndex = 0
    lateinit var player: PlayerFighter
        private set

    val enemies = mutableListOf<EnemyFighter>()
    val destructibles = mutableListOf<DestructibleObject>()

    /** Decoração fixa e não-interativa do cenário (rodada 15, parte 19, 30/09/2026 -- pedido do
     * usuário: "usa esses pequenos png pra deixar o cenario menos possivel infinito... pra nao
     * ficar cansativo jogar"). Sorteada UMA vez pro mundo inteiro em `start()`, nunca muda --
     * só existe pra a BattleView desenhar algo diferente a cada trecho da rua, já que o fundo
     * agora é uma imagem única repetida em loop (ver BattleView.drawLoopingBackground()) e sem
     * decoração ficaria óbvio/repetitivo. `kind` é só um índice pro array de sprites de
     * decoração na BattleView -- esta classe não sabe (nem precisa saber) o que cada índice
     * desenha. */
    data class SceneryProp(val x: Float, val y: Float, val kind: Int)
    val sceneryProps = mutableListOf<SceneryProp>()

    var state: BattleState = BattleState.RUNNING
        private set

    /** false até start() rodar (a BattleView só chama start() depois de conhecer o tamanho da tela). */
    var started: Boolean = false
        private set

    var enemiesDefeated = 0
        private set
    var cratesBroken = 0
        private set
    var coinsCollected = 0
        private set
    var xpCollected = 0
        private set
    var bonusCharacterDrops = 0
        private set

    private val lootRandom = Random(System.nanoTime())

    private var bossSpawned = false
    private var bossDefeated = false
    private var minibossIndex = 0
    private var spawnedInWave = 0

    // Rodada 15, parte 34 (03/10/2026): entrada cinematográfica do chefe -- pedido explícito do
    // usuário ("faz ele vindo de algum lugar... sai devagar... tremer a tela"). `bossPending` é o
    // intervalo curto de tensão entre chegar na parte do chefe e ele de fato aparecer;
    // `bossIntroActive` é o período em que ele está deslizando de fora da tela até a posição
    // final -- a BattleView lê esse segundo campo pra saber quando tremer a tela. Ver
    // spawnBossEntity()/updateBossIntro().
    private var bossPending = false
    private var bossTriggerAtMs = 0L
    var bossIntroActive = false
    // Rodada 15, parte 54 (04/10/2026): trava o jogador durante a entrada do chefe (pedido do
    // usuario: "trava o personagem onde ele ficou, mas tendo certeza que ele vai conseguir ver
    // o vilao entrando"). Ligado em advanceWave() (que tambem reposiciona o jogador pra garantir
    // que a entrada fique dentro da camera) e desligado em updateBossIntro() quando o deslize acaba.
    var playerLocked = false
        private set
    // Rodada 15, parte 55 (04/10/2026): nao teleporta mais o jogador pra um ponto calculado --
    // pedido explicito ("fixa onde ele tava e nao isso de deixar ele bem no meinho mesmo ele nao
    // estando la"). Agora so espera ele chegar andando, por conta propria, num x minimo de onde
    // a camera JA mostra a entrada do chefe -- so ai trava ele ali mesmo (sem mover nada).
    private var bossAwaitingProximity = false
    private var bossProximityThresholdX = 0f
    private var bossIntroStartAtMs = 0L
    private var bossIntroStartX = 0f
    private var bossTargetX = 0f

    /** Trecho atual da arena (1-based). */
    var currentWave = 1
        private set
    val totalWaves = TOTAL_WAVES

    /** Largura total do "mundo" da batalha -- um trecho por leva + 1 extra reservado pro chefe. */
    var worldWidth: Float = 0f
        private set

    /** Janela de visão da câmera (canto esquerdo, em coordenada de mundo) -- acompanha o jogador
     * dentro dos limites do mundo, como em Golden Axe/Streets of Rage. */
    var cameraX: Float = 0f
        private set

    /** Variante de cenário (0..SCENERY_VARIANT_COUNT-1) sorteada UMA vez por batalha e mantida
     * fixa até o fim -- Rodada 15, parte 29 (30/09/2026): revertido de volta pra "uma só pra
     * batalha inteira" (Gemini tinha trocado pra uma lista sorteada por trecho na parte 27, o
     * que fazia o fundo mudar visivelmente conforme o jogador avançava; pedido explícito do
     * usuário: "não é mais contínuo o background, ele muda, o que não quero"). */
    private var sceneryVariant: Int = 0

    /** Cosmético só: NÃO trava o jogador -- ele já pode ter cruzado a marca de avanço antes
     * disso (feedback 31/08: "a seta esperar 5 segundos, o usuário pode ir antes, igual Street
     * of Rage"). Rodada 15, parte 33 (03/10/2026): gatilho trocado de "tempo fixo depois da leva
     * limpa" pra "jogador parado há 4-5s" (ver PLAYER_IDLE_HINT_MS/lastMovedAtMs e
     * updateWaveFlow()) -- pedido explícito do usuário. */
    var showAdvanceHint: Boolean = false
        private set
    private var waveClearedAtMs = 0L

    // Input do jogador (joystick virtual + botões), atualizado pela BattleView.
    var moveX: Float = 0f
    var moveY: Float = 0f
    var attackRequested: Boolean = false

    /** Instante (ms) do último frame em que o jogador moveu o analógico -- Rodada 15, parte 33
     * (03/10/2026): usado pra saber há quanto tempo ele está parado, e então mostrar a seta de
     * avanço (ver updateWaveFlow()). Pedido explícito do usuário: a seta não deve mais depender
     * de um delay fixo depois da leva limpa, e sim de o jogador ficar parado por 4-5s. */
    private var lastMovedAtMs: Long = 0L

    /** Pulso de 1 tick -- setado true num toque de botão, consumido (e voltado a false) no
     * próximo update(), pra não repetir enquanto o dedo fica em cima do botão. */
    var specialRequested: Boolean = false
    var useFoodRequested: Boolean = false

    /** Instante (ms) do último especial usado -- a BattleView usa isso pra desenhar o efeito
     * visual (anel se expandindo) por um tempinho depois do golpe. */
    var specialEffectAtMs: Long = 0L
        private set

    /** Instante (ms) em que o jogador levou o último golpe de um inimigo -- Rodada 15, parte 37
     * (04/10/2026): a BattleView usa isso pra mostrar a animação de "levando dano" (sprite real,
     * por enquanto só o Homem-Aranha) por uma janela curta depois do golpe, em vez do personagem
     * ficar impassível recebendo dano. */
    var playerHurtAtMs: Long = 0L
        private set

    // ---------------------------------------------------------------------- Transição de morte
    /** Rodada 15, parte 37 (04/10/2026): pedido explícito do usuário -- a troca de personagem
     * (quando o atual morre) deixou de ser instantânea. 3 fases, cada uma com seu próprio
     * cronômetro (`deathPhaseStartAtMs`): DYING (anima a queda, depois fica deitado), BLINKING
     * (pisca rápido algumas vezes -- efeito clássico de troca de personagem) e DROPPING_IN (o
     * próximo já entra caindo de cima até o chão, não andando pela lateral). Enquanto qualquer
     * uma está ativa, `update()` pausa input/IA/colisão -- é uma pausa dramática, igual a entrada
     * do chefe (ver `updateBossIntro`). */
    var deathTransitionPhase: DeathTransitionPhase = DeathTransitionPhase.NONE
        private set
    val deathTransitionActive: Boolean get() = deathTransitionPhase != DeathTransitionPhase.NONE
    var deathPhaseStartAtMs: Long = 0L
        private set
    /** 0f..1f -- progresso da queda do PRÓXIMO personagem na fase DROPPING_IN (a BattleView usa
     * isso pra desenhar ele vindo de cima, sem precisar recalcular o easing). */
    var dropInProgress: Float = 0f
        private set
    private var deathHasNextCharacter: Boolean = false

    private var lastKnownPlayerX: Float = 0f

    // Rodada 15, parte 53 (04/10/2026): respiro no comeco da leva 1 -- ver start()/update().
    private var wave1SpawnPending: Boolean = false
    private var wave1SpawnAtMs: Long = 0L

    fun start(nowMs: Long) {
        worldWidth = arenaWidth * (totalWaves + 1)
        sceneryVariant = Random.nextInt(SCENERY_VARIANT_COUNT)
        spawnPlayer()
        spawnCratesForSection(currentWave)
        // Rodada 15, parte 53 (04/10/2026): a leva 1 nao nasce mais junto com o inicio da luta --
        // pedido explicito do usuario ("ja cair na tela de batalha com 5 capangas fica meio
        // chato de comecar"). So um respiro no comeco; da leva 2 em diante continua nascendo
        // tudo de uma vez assim que o trecho comeca, sem mudanca.
        wave1SpawnPending = true
        wave1SpawnAtMs = nowMs + WAVE1_SPAWN_DELAY_MS
        spawnSceneryProps()
        lastMovedAtMs = nowMs
        started = true
    }

    /** Início (coordenada de mundo) do trecho N -- cada trecho tem exatamente `arenaWidth`. */
    private fun sectionStart(section: Int): Float = (section - 1) * arenaWidth

    /** Variante de cenário da batalha atual (fixa, sorteada uma vez em start()) -- usada pela
     * BattleView pra saber qual bitmap de fundo carregar. Nome mantido (`currentVariant`) pra
     * não precisar mexer em BattleView.kt. */
    fun currentVariant(): Int = sceneryVariant

    /** Faixa andável (chão) -- calçada da cena de fundo.
     *
     * Rodada 15, parte 29 (30/09/2026): restaurados os valores calibrados de
     * `FLOOR_TOP_RATIOS`/`FLOOR_BOTTOM_MARGIN` (a parte 27/28 do Gemini tinha zerado a
     * calibração, deixando os limites "todos cagados"). Mantido, só no topo, o conceito de
     * ancorar pelo PÉ (base da bolinha, y + `PLAYER_VISUAL_RADIUS`) em vez do CENTRO -- pedido
     * explícito do usuário: "considerar o limite o pontinho mais abaixo da bolinha... pra dar
     * um efeito de dimensão podendo passar da calçada da imagem pra cima". Ou seja, o centro
     * pode subir até `limite_da_calcada - PLAYER_VISUAL_RADIUS`, o que deixa o pé exatamente em
     * cima da linha da calçada e a metade de cima do corpo "entrando" visualmente na área do
     * prédio/fundo -- o efeito de profundidade pedido. O limite de baixo não tem esse conceito
     * de profundidade (é só a borda mais perto da câmera), então continua ancorado pelo centro,
     * igual já estava aprovado antes do Gemini mexer. */
    private fun floorTopY(): Float = arenaHeight * FLOOR_TOP_RATIOS[currentVariant()] - PLAYER_VISUAL_RADIUS

    private fun floorBottomY(): Float = arenaHeight * FLOOR_BOTTOM_RATIOS[currentVariant()]

    private fun floorMidY(): Float = (floorTopY() + floorBottomY()) / 2f

    private fun randomFloorY(rnd: Random): Float {
        val top = floorTopY()
        val bottom = floorBottomY()
        return top + rnd.nextFloat() * (bottom - top).coerceAtLeast(1f)
    }

    private fun spawnPlayer() {
        // Primeiro personagem da luta entra no início do trecho 1. Troca de personagem (o anterior
        // morreu, seção 13) NÃO reseta a posição -- o próximo entra exatamente onde o time estava,
        // pra manter o fluxo contínuo (não "recomeça" o cenário).
        val startX = if (!started) sectionStart(1) + arenaWidth * 0.15f else lastKnownPlayerX
        lastKnownPlayerX = startX
        player = PlayerFighter(team[teamIndex], startX, floorMidY())
    }

    /** Rodada 15, parte 20 (ajuste do mesmo dia): reduzido de 6 pra `CRATES_PER_SECTION` (4) e
     * trocado de posição 100% aleatória pra "estratificada" (o trecho de spawn é dividido em
     * `CRATES_PER_SECTION` fatias iguais, um objeto por fatia, com jitter só dentro da própria
     * fatia) -- pedido do usuário ("tem hora que tem mta dessas coisas"): com posição 100%
     * aleatória, nada impedia 2-3 caixas sorteadas bem próximas umas das outras por acaso,
     * parecendo um amontoado. Estratificar garante espaçamento mínimo sem ficar num grid óbvio
     * (o jitter dentro da fatia já quebra a regularidade). */
    private fun spawnCratesForSection(section: Int) {
        val rnd = Random(System.currentTimeMillis())
        val base = sectionStart(section)
        val zoneStart = 0.30f
        val zoneEnd = 0.92f
        val sliceWidth = (zoneEnd - zoneStart) / CRATES_PER_SECTION
        repeat(CRATES_PER_SECTION) { i ->
            val kind = if (i % 2 == 0) DestructibleObject.Kind.CAIXA else DestructibleObject.Kind.BARRIL
            val sliceStart = zoneStart + sliceWidth * i
            val x = base + arenaWidth * (sliceStart + rnd.nextFloat() * sliceWidth)
            val y = randomFloorY(rnd)
            destructibles += DestructibleObject(x, y, kind)
        }
    }

    /** Espalha decoração fixa (sem colisão, só visual) pelo mundo INTEIRO de uma vez, não por
     * trecho -- ao contrário das caixas/barris (que são recriados por seção), a decoração é do
     * cenário como um todo, então precisa existir desde já em todo o `worldWidth`, senão o
     * jogador chegaria em trechos ainda "vazios" antes da próxima leva ser spawnada. Densidade:
     * ~1 item a cada `SCENERY_PROP_SPACING` unidades de mundo, com jitter de posição pra não
     * ficar num grid óbvio; `kind` sorteado entre `SCENERY_PROP_KIND_COUNT` sprites diferentes
     * (ver BattleView) -- é essa VARIEDADE que ajuda a quebrar a sensação de repetição do fundo
     * em loop (pedido do usuário, parte 19). */
    private fun spawnSceneryProps() {
        val rnd = Random(System.currentTimeMillis() xor 0x5EEDL)
        sceneryProps.clear()
        var x = SCENERY_PROP_SPACING * 0.5f
        while (x < worldWidth) {
            val jitterX = x + (rnd.nextFloat() - 0.5f) * SCENERY_PROP_SPACING * 0.6f
            val y = randomFloorY(rnd)
            // Rodada 15, parte 29 (30/09/2026): decoração é 100% visual agora (sem colisão --
            // ver movePlayer()), então voltou a ser sorteio uniforme entre todos os tipos, sem
            // peso e sem lógica de afastamento mínimo (não existe mais risco de "corredor sem
            // passagem" porque nada mais bloqueia).
            val kind = rnd.nextInt(SCENERY_PROP_KIND_COUNT)
            val prop = SceneryProp(jitterX.coerceIn(0f, worldWidth), y, kind)
            sceneryProps += prop
            x += SCENERY_PROP_SPACING
        }
    }

    fun update(nowMs: Long, deltaSeconds: Float) {
        if (state != BattleState.RUNNING) return

        // Rodada 15, parte 37 (04/10/2026): enquanto a transição de morte está rolando (fase
        // DYING/BLINKING/DROPPING_IN), ninguém se move/ataca -- é uma pausa dramática só pra essa
        // sequência, igual já existia pra entrada do chefe.
        if (deathTransitionActive) {
            updateDeathTransition(nowMs)
            updateCamera(deltaSeconds)
            return
        }

        // Rodada 15, parte 53: respiro do comeco (ver start()) -- so spawna a leva 1 depois do
        // delay, enquanto isso o jogador anda livre sem ninguem na tela.
        if (wave1SpawnPending && nowMs >= wave1SpawnAtMs) {
            wave1SpawnPending = false
            spawnWaveEnemies()
        }

        // Rodada 15, parte 55: so trava o jogador (e dispara o tremor/entrada do chefe) quando
        // ele mesmo chegar perto o bastante -- nada de teleporte, fixa ele onde estiver nesse
        // instante.
        if (bossAwaitingProximity && player.x >= bossProximityThresholdX) {
            bossAwaitingProximity = false
            playerLocked = true
            bossPending = true
            bossTriggerAtMs = nowMs
        }

        if (moveX != 0f || moveY != 0f) lastMovedAtMs = nowMs
        movePlayer(deltaSeconds)
        updateCamera(deltaSeconds)
        handlePlayerAttack(nowMs)
        handleSpecialAttack(nowMs)
        handleFoodUse()
        updateEnemies(nowMs, deltaSeconds)
        updateBossIntro(nowMs)
        updateWaveFlow(nowMs)
        checkPlayerDeath(nowMs)
    }

    /** Rodada 15, parte 55/56 (04/10/2026): trava de leva -- true enquanto a leva atual tiver
     * inimigo vivo. Usada tanto pra travar o avanço do jogador (movePlayer) quanto a câmera
     * (updateCamera) -- um só lugar pra não desincronizar as duas. Não vale na fase do chefe
     * (bossSpawned) -- lá é uma arena só, sem "mais pra frente" nem "sala" fixa pra travar.
     */
    private fun isWaveLocked(): Boolean = enemies.any { it.isAlive } && !bossSpawned

    private fun updateCamera(deltaSeconds: Float) {
        val maxCamera = (worldWidth - arenaWidth).coerceAtLeast(0f)
        // Rodada 15, parte 56: enquanto a leva atual tiver inimigo vivo, a câmera trava no
        // "quadro" fixo daquele trecho (a seção toda, que já tem exatamente a largura da tela) --
        // pedido explícito ("parar ele no meio da tela e ele consegue ver mais cenario é ruim...
        // limita até onde as bordas do celular vao"). Sem isso a câmera, seguindo o jogador
        // livremente, deixava sobrar cenário visível além do ponto onde ele já tava bloqueado de
        // andar -- virava uma "parede invisível no meio da tela". Volta a acompanhar livremente
        // assim que a leva morre.
        // Rodada 15, parte 57 (04/10/2026): CORRIGIDO -- a versão anterior travava a câmera
        // num valor fixo na hora, direto, o que cortava a rolagem contínua (ficava parecendo um
        // corte seco entre "salas"). Pedido explícito: "perde o efeito de continuidade, deixa o
        // cenario igual antes que parecia infinito". Agora a câmera continua seguindo o jogador
        // suavemente, só com um teto mais baixo quando a leva tá travada -- trava mesmo (e só
        // então as duas bordas ficam fixas) na hora que o jogador chega perto o bastante do teto,
        // não antes.
        val camCap = if (isWaveLocked()) sectionStart(currentWave).coerceIn(0f, maxCamera) else maxCamera
        val targetCameraX = (player.x - arenaWidth / 2f).coerceIn(0f, camCap)
        // Rodada 15, parte 58 (04/10/2026): quando a leva morre e o teto (`camCap`) sobe de
        // repente, o alvo da câmera pode saltar bem pra frente na hora -- o "susto" de cenário
        // mudando de lugar que o usuário reportou. Pedido explícito: "nao da esse susto... pelo
        // menos faz uma transicao de estar andando o cenario". Em vez de aplicar o alvo direto,
        // a câmera agora persegue ele suavemente (mesma técnica usada em praticamente todo jogo
        // com câmera que segue o personagem) -- fica imperceptível no dia a dia (alcança o alvo
        // rápido, em frações de segundo) e só fica visível como um "andar de cenário" justamente
        // nesse momento de destravar, que é a transição suave que foi pedida.
        val smoothing = (CAMERA_FOLLOW_SPEED * deltaSeconds).coerceIn(0f, 1f)
        cameraX += (targetCameraX - cameraX) * smoothing
    }

    /** Spawna a leva atual, e uma vez limpa, libera a passagem pro próximo trecho -- sem travar
     * o jogador esperando (seção "fluxo contínuo", feedback 31/08). A seta (`showAdvanceHint`) é
     * só um indicativo visual que aparece um pouco depois; o cruzamento de fato já é permitido
     * desde o instante em que a leva foi limpa. */
    private fun updateWaveFlow(nowMs: Long) {
        if (bossSpawned) return
        // Rodada 15, parte 53: enquanto a leva 1 ainda não nasceu (ver wave1SpawnPending), a
        // lista `enemies` está vazia -- sem essa guarda, `enemies.none { it.isAlive }` logo
        // abaixo seria trivialmente true e a leva seria considerada "limpa" antes de começar.
        if (spawnedInWave == 0) return

        // Rodada 15, parte 34 (03/10/2026): a leva inteira já nasce de uma vez em
        // spawnWaveEnemies() (chamada em start()/advanceWave()), não tem mais trickle aqui --
        // só falta esperar todo mundo morrer.
        if (enemies.none { it.isAlive }) {
            if (waveClearedAtMs == 0L) {
                waveClearedAtMs = nowMs
                listener.onWaveCleared(currentWave, totalWaves)
            }
            // Rodada 15, parte 33 (03/10/2026): trocado de "delay fixo depois da leva
            // limpa" pra "jogador parado por N segundos" -- pedido explícito do usuário.
            // Recalculado a cada frame (não só vira true uma vez) pra, se o jogador andar de
            // novo depois de ficar parado, a seta sumir e só reaparecer se ele ficar parado mais
            // 4-5s de novo -- igual o comportamento de "dica" de Streets of Rage.
            showAdvanceHint = nowMs - lastMovedAtMs >= PLAYER_IDLE_HINT_MS
            val triggerX = sectionStart(currentWave) + arenaWidth * ADVANCE_TRIGGER_X_RATIO
            if (player.x >= triggerX) {
                advanceWave(nowMs)
            }
        }
    }

    private fun advanceWave(nowMs: Long) {
        showAdvanceHint = false
        waveClearedAtMs = 0L
        if (currentWave >= totalWaves) {
            if (boss != null) {
                // Rodada 15, parte 34: não spawna mais o chefe na hora -- ver updateBossIntro().
                bossSpawned = true
                // Rodada 15, parte 55: so trava e dispara o tremor quando o jogador chegar, andando
                // sozinho, perto o suficiente pra camera já enquadrar onde o chefe vai deslizar até
                // (ver checagem de bossAwaitingProximity em update()). bossPending/bossTriggerAtMs
                // só são setados lá, no momento real da trava.
                bossAwaitingProximity = true
                bossProximityThresholdX = sectionStart(totalWaves + 1) + arenaWidth * 0.25f
            } else {
                // Sem vilão pra desafiar (edge case raro) -- não trava o jogador esperando pra sempre.
                endBattle(victory = true)
            }
        } else {
            currentWave++
            spawnedInWave = 0
            spawnCratesForSection(currentWave)
            spawnWaveEnemies()
            listener.onWaveAdvanced(currentWave, totalWaves)
        }
    }

    private fun movePlayer(deltaSeconds: Float) {
        // Rodada 15, parte 54: travado durante a entrada do chefe (ver advanceWave/updateBossIntro).
        if (playerLocked) return
        val speed = player.moveSpeed * 60f * deltaSeconds
        if (moveX != 0f || moveY != 0f) {
            player.facingRight = moveX >= 0f
        }
        // Limite agora é o mundo inteiro (não mais a tela) -- o jogador pode andar por todos os
        // trechos já liberados, a câmera que acompanha.
        val rawNextX = (player.x + moveX * speed).coerceIn(PLAYER_MARGIN_X, worldWidth - PLAYER_MARGIN_X)
        // Rodada 15, parte 56: enquanto a leva atual tiver inimigo vivo, o jogador fica livre
        // SÓ dentro da sala travada (ver updateCamera()) -- pedido explícito ("deixando ele
        // chegar ate a ponta que o celular aguenta daquela leva... pega o espaco daquele
        // estagio de capangas e limita ate onde as bordas do celular vao"). Antes o limite era
        // uma fração arbitrária da seção, bem antes da borda real da câmera travada -- agora é a
        // sala inteira, de ponta a ponta, exatamente onde a tela já para de mostrar mais cenário.
        // Rodada 15, parte 59 (04/10/2026): CORRIGIDO -- a versão anterior também tinha um piso
        // (coerceIn com limite mínimo). Quando a leva avançava (currentWave++ em advanceWave()),
        // esse piso saltava pra frente na hora (pro começo da seção NOVA), e como o jogador ainda
        // estava um pouco atrás dele (o gatilho de avanço dispara a ~86% da seção antiga, não nos
        // 100%), ele era empurrado pra frente de repente -- o teleporte/"pisca" reportado. A
        // câmera já persegue suavemente pra qualquer lado (ver updateCamera()), não precisa de
        // piso aqui -- só o teto (não deixa passar da leva enquanto tiver inimigo vivo) importa.
        val nextX = if (isWaveLocked()) {
            rawNextX.coerceAtMost(sectionStart(currentWave) + arenaWidth - PLAYER_MARGIN_X)
        } else {
            rawNextX
        }
        // Só anda na faixa de chão -- não entra no céu/skyline lá em cima.
        val nextY = (player.y + moveY * speed).coerceIn(floorTopY(), floorBottomY())

        // Rodada 15, parte 29 (30/09/2026): removida de vez a colisão sólida com decoração
        // (paredes invisíveis) -- pedido explícito do usuário: "desisti das paredes invisíveis,
        // só tava piorando muito passar nas coisas, tinha hora que nem conseguia passar".
        // Decoração agora é 100% visual, sem bloqueio, como era antes da parte 21.
        player.x = nextX
        player.y = nextY

        lastKnownPlayerX = player.x
    }

    private fun handlePlayerAttack(nowMs: Long) {
        if (!attackRequested) return
        if (!player.canAttack(nowMs)) return
        player.lastAttackAtMs = nowMs

        // Golpe em área curta na frente do jogador: acerta até 2 inimigos e caixas próximas.
        val targets = enemies.filter { it.isAlive && !(it.isBoss && bossIntroActive) && player.distanceTo(it) <= player.attackRange }
            .sortedBy { player.distanceTo(it) }
            .take(2)
        targets.forEach { enemy ->
            enemy.takeDamage(player.attackDamage, nowMs)
            // Especial travado em 2 usos por personagem, sem recarga em combate: o jogador já
            // começa a batalha com as 2 cargas prontas (ver PlayerFighter.specialCharge) e elas
            // só diminuem ao usar -- não enchem de novo batendo em inimigos.
            if (!enemy.isAlive) onEnemyDefeated(enemy)
        }

        destructibles.filter { !it.broken && distance(player.x, player.y, it.x, it.y) <= player.attackRange }
            .forEach { crate ->
                val wasBroken = crate.broken
                crate.hit(player.attackDamage, lootRandom, nowMs)
                if (crate.broken && !wasBroken) {
                    cratesBroken++
                    when (crate.rewardKind) {
                        DestructibleObject.RewardKind.COIN -> {
                            coinsCollected += crate.rewardCoins
                            listener.onCratesOrCoins()
                        }
                        DestructibleObject.RewardKind.FOOD -> {
                            // Guarda no slot em vez de curar na hora (feedback 31/08: "não
                            // necessariamente eu peguei comida que eu quero comer na hora,
                            // deveria ter slot"). Rodada 13: slot virou pilha de 3 (feedback
                            // 01/09) -- se já tava cheio, essa vira moedas bônus em vez de se
                            // perder.
                            if (player.storeFood()) {
                                listener.onFoodPickup(FOOD_HEAL_AMOUNT)
                            } else {
                                coinsCollected += crate.rewardCoins
                                listener.onCratesOrCoins()
                            }
                        }
                        DestructibleObject.RewardKind.CHARACTER -> {
                            bonusCharacterDrops++
                            listener.onBonusCharacterPickup()
                        }
                    }
                }
            }
    }

    /** Especial (seção "especial", feedback 31/08): consome 1 carga cheia e acerta em área todos
     * os inimigos perto do jogador com dano multiplicado. Híbrido Golden Axe/Streets of Rage --
     * carrega com dano (Golden Axe) mas guarda até 2 usos prontos pra disparar na hora (Streets
     * of Rage), decidido aqui porque combina melhor com o ritmo de "levas" da arena. */
    private fun handleSpecialAttack(nowMs: Long) {
        if (!specialRequested) return
        specialRequested = false
        if (!player.consumeSpecialCharge()) return

        val targets = enemies.filter { it.isAlive && !(it.isBoss && bossIntroActive) && player.distanceTo(it) <= SPECIAL_RANGE }
        targets.forEach { enemy ->
            enemy.takeDamage(player.attackDamage * SPECIAL_DAMAGE_MULTIPLIER, nowMs)
            if (!enemy.isAlive) onEnemyDefeated(enemy)
        }
        specialEffectAtMs = nowMs
        listener.onSpecialUsed()
    }

    /** Come o que tá guardado no slot, se houver (o jogador decide a hora, seção "loot v2"). */
    private fun handleFoodUse() {
        if (!useFoodRequested) return
        useFoodRequested = false
        val amount = player.consumeFood(FOOD_HEAL_AMOUNT)
        if (amount > 0) listener.onFoodConsumed(amount)
    }

    /** Rodada 15, parte 55 (04/10/2026): IA "Streets of Rage" -- pedido explícito do usuário
     * ("tem muitos o resto espera e os que ta batendo tenta secar o usuario, igual streets of
     * rage"). Antes todo inimigo vivo ia direto pro jogador, formando uma "bolinha" só em cima
     * dele. Agora só `MAX_ENGAGED_ENEMIES` por vez avançam pra valer e atacam (`isEngaging`,
     * promovido aqui por ordem de distância, liberado sozinho quando o engajado morre -- sem
     * "roubo" de vez em quando pra não ficar trocando de atacante toda hora/piscando). O resto
     * espera numa distância seguro fora do alcance de ataque, com um shuffle lateral leve pra
     * não parecer estátua. */
    private fun updateEnemies(nowMs: Long, deltaSeconds: Float) {
        val aliveEnemies = enemies.filter { it.isAlive && !(it.isBoss && bossIntroActive) }
        promoteEngagers(aliveEnemies)
        aliveEnemies.forEach { enemy ->
            val dx = player.x - enemy.x
            val dy = player.y - enemy.y
            val dist = distance(enemy.x, enemy.y, player.x, player.y)
            if (enemy.isEngaging) {
                if (dist > enemy.attackRange * 0.8f) {
                    val speed = enemy.moveSpeed * 60f * deltaSeconds
                    val nx = dx / max(dist, 0.001f)
                    val ny = dy / max(dist, 0.001f)
                    enemy.x += nx * speed
                    enemy.y = (enemy.y + ny * speed).coerceIn(floorTopY(), floorBottomY())
                    enemy.facingRight = dx >= 0f
                } else if (enemy.canAttack(nowMs)) {
                    enemy.lastAttackAtMs = nowMs
                    player.takeDamage(enemy.attackDamage)
                    // Rodada 15, parte 37: marca o instante do golpe pra BattleView mostrar a
                    // animação de "levando dano" por uma janela curta.
                    playerHurtAtMs = nowMs
                }
            } else {
                // Rodada 15, parte 57: quem espera não converge mais todo pro mesmo ponto (virava
                // "uma bolinha" só de espera, pedido explícito pra separar mais) -- cada um tem um
                // slot fixo (`waitSlot`, dado no spawn) distribuído num anel em volta do jogador.
                val waitDistance = enemy.attackRange * 2.4f
                val angle = (enemy.waitSlot.toFloat() / ENEMIES_PER_WAVE) * (2f * PI.toFloat())
                val slotX = player.x + cos(angle) * waitDistance
                val slotY = (player.y + sin(angle) * waitDistance * 0.4f).coerceIn(floorTopY(), floorBottomY())
                val toSlotX = slotX - enemy.x
                val toSlotY = slotY - enemy.y
                val slotDist = distance(enemy.x, enemy.y, slotX, slotY)
                if (slotDist > 12f) {
                    val speed = enemy.moveSpeed * 60f * deltaSeconds
                    val nx = toSlotX / max(slotDist, 0.001f)
                    val ny = toSlotY / max(slotDist, 0.001f)
                    enemy.x += nx * speed
                    enemy.y = (enemy.y + ny * speed).coerceIn(floorTopY(), floorBottomY())
                } else {
                    val phase = nowMs * 0.0025f + enemy.x * 0.05f
                    val shuffleSpeed = enemy.moveSpeed * 60f * deltaSeconds * 0.35f
                    val px = -dy / max(dist, 0.001f)
                    val py = dx / max(dist, 0.001f)
                    val dir = sin(phase)
                    enemy.x += px * dir * shuffleSpeed
                    enemy.y = (enemy.y + py * dir * shuffleSpeed).coerceIn(floorTopY(), floorBottomY())
                }
                enemy.facingRight = dx >= 0f
            }
        }
    }

    /** Rodada 15, parte 57 (04/10/2026): promove quem vai engajar (até `MAX_ENGAGED_ENEMIES`),
     * tentando sempre balancear 1 de cada lado do jogador -- pedido explícito ("coloca dois
     * capangas pra bater por vez, um de cada lado"). Antes pegava só os 2 mais próximos, que
     * podiam vir os dois do mesmo lado. Nunca "rouba" a vaga de quem já tá engajando -- só
     * preenche vaga vazia (ver updateEnemies()). */
    private fun promoteEngagers(aliveEnemies: List<EnemyFighter>) {
        var engagingCount = aliveEnemies.count { it.isEngaging }
        while (engagingCount < MAX_ENGAGED_ENEMIES) {
            val candidates = aliveEnemies.filter { !it.isEngaging }
            if (candidates.isEmpty()) break
            val engagedFromLeft = aliveEnemies.any { it.isEngaging && it.x < player.x }
            val engagedFromRight = aliveEnemies.any { it.isEngaging && it.x >= player.x }
            val wantSide = when {
                !engagedFromLeft && engagedFromRight -> candidates.filter { it.x < player.x }
                !engagedFromRight && engagedFromLeft -> candidates.filter { it.x >= player.x }
                else -> candidates
            }
            val pick = (if (wantSide.isNotEmpty()) wantSide else candidates)
                .minByOrNull { distance(it.x, it.y, player.x, player.y) } ?: break
            pick.isEngaging = true
            engagingCount++
        }
    }

    private fun onEnemyDefeated(enemy: EnemyFighter) {
        enemiesDefeated++
        xpCollected += enemy.rewardXp
        coinsCollected += enemy.rewardCoins
        when {
            enemy.isBoss -> {
                bossDefeated = true
                listener.onBossDefeated(enemy.displayName)
                listener.onCratesOrCoins()
                endBattle(victory = true)
            }
            enemy.isMiniboss -> {
                listener.onCratesOrCoins()
                // Só o flash -- a luta segue rolando por baixo, ninguém trava esperando (mesma
                // filosofia de "fluxo contínuo" do resto da arena).
                listener.onMinibossDefeated(enemy.displayName)
            }
            else -> listener.onCratesOrCoins()
        }
    }

    /** Rodada 15, parte 34 (03/10/2026): a leva inteira nasce de uma vez, espalhada pelo
     * trecho inteiro -- não mais um capanga a cada SPAWN_INTERVAL_MS surgindo bem na borda
     * direita da tela. Pedido explícito do usuário: "fica muito feio vc ta andando e o bixo
     * spawna depois" (o "pop" no meio da tela enquanto ele já estava andando) e "vem da esquerda
     * tbm, fica mais semelhante a golden axe". Com todo mundo já posicionado desde o início do
     * trecho -- parte perto de onde o jogador entra (que vai ficando "pra trás"/à esquerda dele
     * conforme ele anda, podendo vir atacar por trás igual Golden Axe) e parte perto da saída (à
     * frente) -- ninguém mais aparece do nada no meio da tela. Nem todo mundo vem pra cima do
     * jogador de uma vez (o usuário concorda que isso é esperado/bom) -- isso já acontece
     * sozinho, por causa da distância: quem nasce mais longe demora mais pra chegar perto o
     * bastante pra perseguir de verdade. */
    private fun spawnWaveEnemies() {
        val rnd = Random(System.nanoTime())
        // Rodada 15, parte 56 (04/10/2026): removido o spawn pela esquerda -- pedido explícito
        // ("ainda sinto que esta mais facil o jogo que antes quando só vinha personagem da
        // direita, acho uma boa esquecer o pessoal que vem no comeco do mapa"). Com a câmera
        // agora travada no quadro da leva (ver updateCamera()), quem nascia fora da borda
        // esquerda demorava muito pra entrar em cena e parecia bugado. Volta a vir só da
        // direita -- mas ainda fora da sala travada (nunca dentro do que já é visível), usando
        // a própria borda da sala (sectionStart+arenaWidth), que é exatamente onde a câmera para.
        val roomRight = sectionStart(currentWave) + arenaWidth
        val edgeMargin = arenaWidth * 0.1f
        for (i in 0 until ENEMIES_PER_WAVE) {
            val jitter = rnd.nextFloat() * arenaWidth * 0.15f
            val x = (roomRight + edgeMargin + jitter).coerceAtMost(worldWidth - PLAYER_MARGIN_X)
            val y = randomFloorY(rnd)
            // Rodada 13 (feedback 01/09, referência Golden Axe): o último capanga de certas
            // levas vira um MINIBOSS nomeado -- mais forte, dá tela preta rápida ao cair, mas
            // não acaba a luta. Só 2 minibosses numa batalha de 6 levas -- desafio extra sem
            // virar rotina.
            if (currentWave in MINIBOSS_WAVES && i == ENEMIES_PER_WAVE - 1) {
                spawnMiniboss(x, y, i)
            } else {
                val regularName = REGULAR_ENEMY_NAMES[(currentWave + i) % REGULAR_ENEMY_NAMES.size]
                enemies += EnemyFighter(regularName, x, y).also { it.waitSlot = i }
            }
        }
        spawnedInWave = ENEMIES_PER_WAVE
    }

    private fun spawnMiniboss(x: Float, y: Float, waitSlot: Int = 0) {
        val avgPlayerLevel = team.map { it.level }.average().takeIf { !it.isNaN() }?.toInt() ?: 1
        // Correção pós-validação rodada 13: como cada batalha só spawna 2 minibosses (WAVES 2 e
        // 4) e uma BattleEngine nova nasce a cada luta, um índice começando sempre em 0 nunca
        // alcançava o 3º nome do banco (sempre [0] depois [1], eternamente). Desloca o ponto de
        // partida pelo id do vilão da luta (mesma ideia da variante de cenário logo abaixo), então
        // batalhas diferentes começam em nomes diferentes e o banco todo circula com o tempo.
        val offset = abs(boss?.id ?: 0)
        val name = MINIBOSS_NAMES[(offset + minibossIndex) % MINIBOSS_NAMES.size]
        minibossIndex++
        enemies += EnemyFighter(
            displayName = name,
            startX = x,
            startY = y,
            isMiniboss = true,
            playerLevel = avgPlayerLevel.coerceAtLeast(1)
        ).also { it.waitSlot = waitSlot }
    }

    /** Chamada só depois do delay de tensão (`bossPending`, ver updateBossIntro()) -- o chefe
     * nasce bem além da borda direita da câmera (fora da tela, "no limbo") e começa a deslizar
     * pra posição final; `bossIntroActive` fica true até o deslize terminar, e é isso que a
     * BattleView usa pra tremer a tela enquanto ele aparece. */
    private fun spawnBossEntity(nowMs: Long) {
        val bossChar = boss ?: return
        listener.onBossIncoming(bossChar.name)
        // Nível médio do time no momento da luta -- usado pra escalar o chefe (seção
        // "balanceamento": revanches contra o mesmo vilão não podem ficar fáceis pra sempre).
        val avgPlayerLevel = team.map { it.level }.average().takeIf { !it.isNaN() }?.toInt() ?: 1
        bossTargetX = sectionStart(totalWaves + 1) + arenaWidth * 0.65f
        bossIntroStartX = bossTargetX + arenaWidth * 0.4f
        bossIntroStartAtMs = nowMs
        bossIntroActive = true
        val bossFighter = EnemyFighter(
            displayName = bossChar.name,
            startX = bossIntroStartX,
            startY = floorMidY(),
            isBoss = true,
            bossCharacterId = bossChar.id,
            bossPower = bossChar.stats.overallPower,
            playerLevel = avgPlayerLevel.coerceAtLeast(1)
        )
        // Rodada 15, parte 54: o chefe desliza de bossIntroStartX (direita) pra bossTargetX
        // (esquerda) -- sem isso ele nascia de frente (facingRight default=true) e parecia andar
        // de ré (moonwalk) enquanto a posição ia pra esquerda.
        bossFighter.facingRight = false
        enemies += bossFighter
    }

    /** Avança o estado da entrada do chefe: primeiro o delay curto de tensão (`bossPending`),
     * depois o deslize suave de fora da tela até `bossTargetX` (easing "ease-out" -- rápido no
     * começo, desacelerando até encaixar na posição final, pra não parecer um corte seco). */
    private fun updateBossIntro(nowMs: Long) {
        if (bossPending) {
            if (nowMs - bossTriggerAtMs >= BOSS_INTRO_DELAY_MS) {
                bossPending = false
                spawnBossEntity(nowMs)
            }
            return
        }
        if (!bossIntroActive) return
        val bossFighter = enemies.lastOrNull { it.isBoss }
        if (bossFighter == null) {
            bossIntroActive = false
            playerLocked = false
            return
        }
        val t = ((nowMs - bossIntroStartAtMs).toFloat() / BOSS_INTRO_SLIDE_MS).coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t)
        bossFighter.x = bossIntroStartX + (bossTargetX - bossIntroStartX) * eased
        if (t >= 1f) {
            bossIntroActive = false
            playerLocked = false
        }
    }

    /** Rodada 15, parte 37 (04/10/2026): não troca mais de personagem na hora -- só dispara a
     * transição (ver `updateDeathTransition`), que decide o resto depois de passar pelas fases
     * de queda/pisca. */
    private fun checkPlayerDeath(nowMs: Long) {
        if (player.isAlive) return
        listener.onCharacterDefeated(player.character.name)
        deathHasNextCharacter = teamIndex < team.size - 1
        deathTransitionPhase = DeathTransitionPhase.DYING
        deathPhaseStartAtMs = nowMs
    }

    /** Avança as 3 fases da transição de morte (ver campos acima). DYING e BLINKING só esperam
     * o próprio tempo passar; ao fim de BLINKING é que de fato troca de personagem (ou termina a
     * batalha, se não tinha mais ninguém no time) -- a BattleView usa `player`/`deathPhaseStartAtMs`
     * pra saber o que desenhar em cada fase. */
    private fun updateDeathTransition(nowMs: Long) {
        val elapsed = nowMs - deathPhaseStartAtMs
        when (deathTransitionPhase) {
            DeathTransitionPhase.DYING -> if (elapsed >= DEATH_LIE_MS) {
                deathTransitionPhase = DeathTransitionPhase.BLINKING
                deathPhaseStartAtMs = nowMs
            }
            DeathTransitionPhase.BLINKING -> if (elapsed >= DEATH_BLINK_MS) {
                if (deathHasNextCharacter) {
                    teamIndex++
                    spawnPlayer()
                    listener.onNextCharacterEnters(player.character.name)
                    dropInProgress = 0f
                    deathTransitionPhase = DeathTransitionPhase.DROPPING_IN
                    deathPhaseStartAtMs = nowMs
                } else {
                    deathTransitionPhase = DeathTransitionPhase.NONE
                    endBattle(victory = false)
                }
            }
            DeathTransitionPhase.DROPPING_IN -> {
                val t = (elapsed.toFloat() / DROP_IN_MS).coerceIn(0f, 1f)
                dropInProgress = t
                if (t >= 1f) {
                    deathTransitionPhase = DeathTransitionPhase.NONE
                }
            }
            DeathTransitionPhase.NONE -> Unit
        }
    }

    private fun endBattle(victory: Boolean) {
        state = if (victory) BattleState.VICTORY else BattleState.DEFEAT
        val result = BattleResult(
            victory = victory,
            enemiesDefeated = enemiesDefeated,
            cratesBroken = cratesBroken,
            bossName = if (bossDefeated) boss?.name else null,
            bossCharacterId = if (bossDefeated) boss?.id else null,
            xpGained = xpCollected,
            coinsGained = coinsCollected,
            chestAwarded = if (victory) (if (bossDefeated) ChestType.ESPECIAL else ChestType.HEROI) else null,
            bonusCharacterDrops = bonusCharacterDrops
        )
        listener.onBattleEnded(result)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    companion object {
        // Balanceamento rodada 13 (feedback 01/09: "só um vilão e 15 capangas nao desafia
        // tanto... acho que deveria ter mais luta"): 30 capangas no total, 6 levas de 5, spawn
        // um pouco mais rápido -- luta mais longa e mais gente na tela ao mesmo tempo.
        private const val TOTAL_WAVES = 6
        private const val ENEMIES_PER_WAVE = 5
        // Rodada 15, parte 55: quantos inimigos podem avançar/atacar ao mesmo tempo (ver updateEnemies()).
        private const val MAX_ENGAGED_ENEMIES = 2
        // Rodada 15, parte 58: velocidade com que a câmera persegue o alvo (ver updateCamera()).
        private const val CAMERA_FOLLOW_SPEED = 3f  // Rodada 15, parte 59: mais lento, pedido explicito ("diminue a velocidade")
        // Rodada 15, parte 34 (03/10/2026): posição (fração do trecho) de cada um dos 5 inimigos
        // da leva, espalhados da entrada à saída -- substitui o antigo spawn 1-a-1 cronometrado
        // (MAX_CONCURRENT_ENEMIES/SPAWN_INTERVAL_MS, removidos, não existe mais pacing por
        // tempo). Os 2 primeiros ficam mais perto de onde o jogador entra no trecho (acabam
        // "atrás"/à esquerda dele conforme anda -- pode vir atacar por trás, igual Golden Axe),
        // os 2 últimos mais perto da saída (à frente); tamanho tem que bater com
        // ENEMIES_PER_WAVE.
        private val WAVE_SPAWN_FRACTIONS = floatArrayOf(0.26f, 0.42f, 0.58f, 0.74f, 0.90f)
        // Rodada 15, parte 53 (04/10/2026): tempo que o jogador anda livre, sem ninguem na
        // tela, antes da leva 1 nascer -- pedido explicito do usuario.
        private const val WAVE1_SPAWN_DELAY_MS = 3000L

        // Cura fixa por carga de comida consumida (o slot virou pilha de 3, seção "loot v2").
        const val FOOD_HEAL_AMOUNT = 22

        // Minibosses (seção "transição nostálgica", feedback 01/09): em quais levas o último
        // capanga vira um miniboss nomeado, e o banco de nomes usado (cíclico).
        //
        // Rodada 15, parte 41 (04/10/2026): "Brutamontes Blindado" (genérico) virou "Bulldozer"
        // -- pedido explícito do usuário, que escolheu o Bulldozer (Wrecking Crew) pra esse
        // papel. IMPORTANTE (avisado ao usuário): esse nome não aparece em NENHUM lugar da tela
        // hoje -- os banners de texto e o nome escrito no flash de miniboss foram desligados nas
        // partes 33/35 (ver onMinibossDefeated/flashMinibossDefeated). É só o dado interno, não
        // um efeito visual novo.
        private val MINIBOSS_WAVES = setOf(2, 4)
        // Rodada 15, parte 51 (04/10/2026): so o Bulldozer tem sprite de verdade -- os
        // outros 2 nomes (removidos daqui, so comentados) ainda caiam pra bolinha roxa, o que
        // o usuario reportou como bug. Ate 'Chefe da Gangue'/'Capanga de Elite' ganharem arte
        // tambem, o miniboss e sempre Bulldozer.
        private val MINIBOSS_NAMES = listOf("Bulldozer")

        // Rodada 15, parte 41 (04/10/2026): os 3 capangas "comuns" restantes do Wrecking Crew
        // (o 4º, Bulldozer, virou o Brutamontes acima) -- mesma ressalva: nenhuma UI mostra esse
        // nome hoje, é só identidade interna pra quando/se ganharem sprite ou indicador próprio.
        private val REGULAR_ENEMY_NAMES = listOf("Wrecker", "Piledriver", "Thunderball")
        // Fração do trecho atual que o jogador precisa alcançar, com a leva limpa, pra liberar o
        // próximo -- pode cruzar isso ANTES da seta aparecer (seção "fluxo contínuo").
        private const val ADVANCE_TRIGGER_X_RATIO = 0.86f
        // Rodada 15, parte 33 (03/10/2026): não é mais "tempo fixo depois da leva limpa" --
        // agora é "quanto tempo o jogador precisa ficar PARADO" pra seta aparecer (pedido
        // explícito do usuário, no lugar do antigo ADVANCE_HINT_DELAY_MS). Ainda só cosmético,
        // não bloqueia o avanço -- ver updateWaveFlow().
        private const val PLAYER_IDLE_HINT_MS = 4500L

        // Rodada 15, parte 34 (03/10/2026): entrada cinematográfica do chefe -- pedido explícito
        // do usuário ("demora bem pouquinho pra dar aquela tensao... sai devagar... tremer a
        // tela"). Delay curto antes dele começar a aparecer, depois duração do deslize de fora
        // da tela até a posição final -- ver spawnBossEntity()/updateBossIntro().
        // Rodada 15, parte 35 (03/10/2026): 1.5s de deslize ficou rápido demais pra ler como
        // "ele tá saindo devagar do limbo" -- feedback direto do usuário ("a animação do vilao
        // ta muito rapida"). Mais que dobrado (1.5s -> 3.2s), e o delay de tensão também um
        // pouco maior (0.7s -> 0.9s) pra dar mais aquele instante de "suspense" antes dele
        // aparecer.
        private const val BOSS_INTRO_DELAY_MS = 900L
        private const val BOSS_INTRO_SLIDE_MS = 3200L

        // Rodada 15, parte 37 (04/10/2026): tempos da transição de troca de personagem (pedido
        // explícito do usuário -- "faz uma transicao mais devagar... pisca ele algumas vezes...
        // depois coloca o outro personagem como se tivesse caindo no campo de batalha"). DYING
        // cobre a animação de queda (7 frames) + uma pausa curta deitado; BLINKING é o pisca-
        // pisca; DROPPING_IN é o próximo caindo de cima até o chão.
        private const val DEATH_LIE_MS = 900L
        private const val DEATH_BLINK_MS = 700L
        private const val DROP_IN_MS = 550L

        // Especial: quanto a barra carrega por ponto de dano causado, e o quanto multiplica o
        // dano normal quando disparado em área.
        private const val SPECIAL_CHARGE_PER_DAMAGE = 1f / 80f
        private const val SPECIAL_RANGE = 240f
        private const val SPECIAL_DAMAGE_MULTIPLIER = 3

        // Quantas variantes de cenário existem pra sortear por vilão.
        // Rodada 15, parte 18 (30/09/2026): trocado o pack de cenário -- 4 variantes de rua
        // (pack CraftPix "Free Pixel Art Street 2D Backgrounds", licença gratuita, sem exigência
        // de atribuição) no lugar das 3 antigas (castelo/templo/floresta, Kenney). Ver
        // BattleView.kt pro motivo de cada uma agora ser uma imagem ÚNICA e fixa, não mais um
        // par de camadas ladrilhadas infinitamente.
        // Rodada 15, parte 32 (02/10/2026): mais 8 variantes, de 2 packs novos CraftPix que o
        // usuário baixou e conectou ("Pixel Art Battlegrounds" -- ruínas/salão do trono com
        // dragão/selva/cripta -- e "Postapocalypse Backgrounds" -- 4 cenas pós-apocalípticas).
        // Índices 0-3 continuam sendo as 4 ruas (City1-4); 4-7 são Battleground1-4
        // (ruins/throne/jungle/crypt); 8-10 são Apoc1/Apoc2/Apoc4 -- ver comentário em
        // BattleView.ensureSceneryLoaded() pro mapeamento de recurso de cada índice.
        // Rodada 15, parte 56 (07/10/2026): Apoc3 (parque de diversões abandonado -- roda-gigante
        // quebrada, cabeça de palhaço, correntes penduradas) removido da rotação a pedido
        // explícito do usuário. Arquivo img_battle_scenery_apoc3.png continua no disco, só órfão,
        // igual já foi feito com outros sprites tirados de rotação (ver remoção da lojinha logo
        // abaixo). 12 -> 11 variantes; Apoc4 (antigo índice 11) virou 10.
        const val SCENERY_VARIANT_COUNT = 11

        // Rodada 15, parte 19 (30/09/2026): faixa da calçada por variante de cenário (fração
        // da altura da arena), medida direto nas 4 imagens reais do pack CraftPix (amostragem de
        // pixel -- onde a cor da calçada muda pro meio-fio, e onde os objetos/prédios de fundo
        // encostam no chão). Índice = variante (0=City1, 1=City2, 2=City3, 3=City4).
        // Substitui o antigo `HORIZON_Y_RATIO` único (0.32, nunca recalibrado pro novo cenário --
        // causava o bug "personagens flutuando" reportado pelo usuário).
        // Rodada 15, parte 29 (30/09/2026): restaurados os valores calibrados por amostragem de
        // pixel nas 4 imagens reais do pack CraftPix (parte 19), que o Gemini tinha resetado pro
        // 0.32 antigo/genérico (bug reportado: "a limitação de altura ta toda cagada").
        // Rodada 15, parte 32 (02/10/2026): 8 ratios novos (índices 4-11), medidos do mesmo jeito
        // que os 4 originais (inspeção visual + amostragem de pixel) nas imagens reais dos 2
        // packs novos -- ver comentário de SCENERY_VARIANT_COUNT acima pra ordem/nome de cada
        // cena. Diferente das ruas (chão raso, perto do topo da imagem), essas cenas têm o chão
        // bem mais embaixo (ruínas/cripta por volta da metade da tela, cenas pós-apocalípticas
        // perto do rodapé) -- por isso os valores variam bem mais entre si que os 4 originais.
        private val FLOOR_TOP_RATIOS = floatArrayOf(
            0.80f, 0.82f, 0.80f, 0.84f, // 0-3: City1-4
            0.42f, 0.50f, 0.60f, 0.46f, // 4-7: Ruins1, Throne2, Jungle3, Crypt4
            0.72f, 0.81f, 0.78f  // 8-10: Apoc1, Apoc2, Apoc4 (Apoc3 removido, parte 56)
        )
        // Rodada 15, parte 33 (03/10/2026): Throne2 ajustado de 0.48 pra 0.50 -- reinspeção de
        // perto (grid sobreposto na imagem, zoom na metade de baixo) mostrou que 0.48 ainda
        // pegava uma fatia da base da estátua do dragão/sombra antes do chão de ladrilho
        // começar de fato.

        // Rodada 15, parte 33 (03/10/2026): limite de BAIXO deixou de ser uma margem única
        // genérica (`FLOOR_BOTTOM_MARGIN`, 44px pra todas as 12 variantes) -- pedido explícito do
        // usuário: "cada cenario tem um tamanho especifico... ao inves de generalizar, veja o
        // tamanho de cada tela". Reinspecionei as 12 imagens de novo (overlay de grade de 5% +
        // zoom na metade de baixo de cada uma, visto por cima da faixa de chão inteira) IGUAL foi
        // feito pro limite de cima na parte 19/32. Resultado: em 11 das 12, o chão realmente
        // segue liso e sem interrupção até a borda de baixo da imagem (rua, grama, terra
        // rachada, areia -- sem buraco/penhasco/água escondido em nenhuma), então o valor real
        // calibrado bate perto do antigo 44px em todas elas. A exceção investigada de perto foi
        // Throne2 (salão do trono): tem uma faixa de tapete vermelho no meio do chão de ladrilho
        // (não é parede nem buraco, só textura diferente) -- com o zoom, confirmei que é tapete
        // sobre o MESMO chão andável, então o limite de baixo dela também pode ir até perto da
        // borda, sem precisar cortar antes. Ou seja: a arquitetura agora é por variante (não mais
        // 1 constante pra todas), e os valores vieram de olhar cada imagem de novo -- só que a
        // maioria genuinamente se pareceu entre si porque o chão genuinamente vai até o fim da
        // imagem em quase todas.
        private val FLOOR_BOTTOM_RATIOS = floatArrayOf(
            0.97f, 0.97f, 0.97f, 0.97f, // 0-3: City1-4 (rua/calçada seguem até o fim da imagem)
            0.96f, 0.97f, 0.97f, 0.97f, // 4-7: Ruins1 (grama), Throne2 (ladrilho+tapete), Jungle3 (terra), Crypt4 (pedra rachada)
            0.97f, 0.97f, 0.97f  // 8-10: Apoc1, Apoc2, Apoc4 (terra rachada/areia, sem quebra até o fim)
        )
        private const val PLAYER_MARGIN_X = 40f
        private const val PLAYER_VISUAL_RADIUS = 32f

        // Rodada 15, parte 20: reduzido de 6 pra 4 caixas/barris por trecho, e a distribuição
        // virou "fatiada" (uma por fatia igual da zona), não mais totalmente aleatória -- pedido
        // do usuário ("tem hora que tem mta dessas coisas"), evita aglomerar tudo perto.
        private const val CRATES_PER_SECTION = 4

        // Decoração fixa do cenário (rodada 15, parte 19) -- ver spawnSceneryProps()/SceneryProp.
        // Rodada 15, parte 20: espaçamento aumentado de 420 pra 900 (mesmo motivo do
        // CRATES_PER_SECTION acima -- reduzir a quantidade de decoração espalhada pelo mundo).
        private const val SCENERY_PROP_SPACING = 900f
        // Rodada 15, parte 33 (03/10/2026): 6 -> 5 -- removida a "lojinha" (kiosk) da rotação de
        // decoração. Pedido explícito do usuário: "tira a imagem de uma lojinha, ela nao combina
        // com quase nenhum cenario" (o sprite é claramente de rua de cidade real, destoava nas
        // variantes de ruínas/selva/pós-apocalipse). Ver BattleView.kt (decoRes/
        // DECO_SPRITE_HEIGHTS) pro resto da remoção -- o arquivo img_battle_deco_kiosk.png
        // continua no disco, só órfão, igual já é feito com outros sprites removidos da rotação.
        const val SCENERY_PROP_KIND_COUNT = 5

        // Rodada 15, parte 29 (30/09/2026): removida de vez a feature de colisão sólida com
        // decoração (SOLID_PROP_KINDS, MIN_SOLID_PROP_GAP, pesos de sorteio, caixas de colisão
        // por prop) -- pedido explícito do usuário: "desisti das paredes invisíveis, só tava
        // piorando muito passar nas coisas, tinha hora que nem conseguia passar". Decoração
        // voltou a ser puramente visual, como nas partes 19-20.
    }
}
