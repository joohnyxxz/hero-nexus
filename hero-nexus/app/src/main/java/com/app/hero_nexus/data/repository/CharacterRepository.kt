package com.app.hero_nexus.data.repository

import com.app.hero_nexus.data.local.CharacterDao
import com.app.hero_nexus.data.local.CharacterEntity
import com.app.hero_nexus.data.remote.CharacterDto
import com.app.hero_nexus.data.remote.ComicVineApi
import com.app.hero_nexus.util.CharacterCategorizer
import com.app.hero_nexus.util.Constants
import com.app.hero_nexus.util.PowerCalculator
import com.app.hero_nexus.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fonte única dos dados de personagens: busca na Comic Vine e guarda em cache local (Room),
 * seguindo a seção 26 do documento ("evitar consultar a API repetidamente sem necessidade").
 *
 * Rodada 15, parte 13 (30/09/2026): listagem "por partes" de novo (pedido do usuário, depois de
 * confirmar que a base simples sem paginação funciona), desta vez com 2 decisões deliberadas pra
 * não repetir os problemas das partes 5-11:
 * 1. O cursor de paginação ([nextOffset]/[totalResults]) é só uma variável em memória, NÃO
 *    persistida em SharedPreferences -- foi exatamente um parâmetro extra de SharedPreferences no
 *    construtor que ficou fora de sincronia com quem cria este repositório (HeroNexusApp.kt) e
 *    quebrou a compilação nas partes 5-11 sem ninguém perceber (ver histórico). Reabrir o app
 *    recomeça a paginação do zero -- é uma perda pequena (o catálogo da Comic Vine quase não muda)
 *    e elimina essa classe inteira de bug.
 * 2. Nenhuma UI (adapter/RecyclerView) é mexida por este repositório -- ele só grava no Room. Quem
 *    mostra "carregando mais" é uma ProgressBar comum no layout (ver CollectionActivity), não um
 *    item dentro da lista -- elimina a outra causa raiz real já achada (IllegalStateException por
 *    mexer no adapter dentro de um callback de scroll).
 */
class CharacterRepository(
    private val api: ComicVineApi,
    private val dao: CharacterDao
) {
    private var nextOffset = 0
    private var totalResults = Int.MAX_VALUE
    private var exhausted = false

    /** Ainda vale a pena tentar buscar mais personagens Marvel (scroll/"carregar mais")? */
    val hasMore: Boolean get() = !exhausted

    /** Fluxo reativo da coleção em cache — a UI observa isso e é atualizada sozinha após um carregamento. */
    fun observeCollection(): Flow<List<CharacterEntity>> = dao.observeAll()

    suspend fun getCached(id: Int): CharacterEntity? = dao.getById(id)

    suspend fun getAllCached(): List<CharacterEntity> = dao.getAllOnce()

    /**
     * Garante que a PRIMEIRA leva ([Constants.INITIAL_PAGE_TARGET] personagens Marvel) esteja
     * pronta -- rápido de propósito, pra splash/tela pintarem logo com dado de verdade. Só bate
     * na rede se o cache estiver vazio ou "velho" (> [Constants.CACHE_TTL_MILLIS]).
     */
    suspend fun ensureFirstBatch(): Resource<Unit> {
        val count = dao.count()
        val lastCached = dao.lastCachedAt() ?: 0L
        val isStale = System.currentTimeMillis() - lastCached > Constants.CACHE_TTL_MILLIS
        if (count > 0 && !isStale) return Resource.Success(Unit)
        return fetchBatch(target = Constants.INITIAL_PAGE_TARGET, replace = true)
    }

    /**
     * Puxar-para-atualizar: busca a primeira leva de novo do zero e só troca o cache se a rede
     * responder com sucesso -- se falhar, o que já estava na coleção continua exatamente como
     * estava, sem piscar pra tela vazia.
     */
    suspend fun refreshFromNetwork(): Resource<Unit> =
        fetchBatch(target = Constants.INITIAL_PAGE_TARGET, replace = true)

    /**
     * "Por partes": busca mais uma leva ([Constants.LOAD_MORE_BATCH_TARGET] personagens Marvel
     * novos) e ACRESCENTA ao cache existente -- a lista na tela cresce, nada é substituído.
     */
    suspend fun loadMore(): Resource<Unit> {
        if (exhausted) return Resource.Success(Unit)
        return fetchBatch(target = Constants.LOAD_MORE_BATCH_TARGET, replace = false)
    }

    private suspend fun fetchBatch(target: Int, replace: Boolean): Resource<Unit> {
        if (replace) {
            nextOffset = 0
            totalResults = Int.MAX_VALUE
            exhausted = false
        }

        // Rodada 15, parte 14 (30/09/2026): correção de um bug real achado por leitura de
        // código (não chute) -- o teto de tempo do CONJUNTO inteiro (BATCH_LOAD_TIMEOUT_MILLIS)
        // era menor que o teto de UMA chamada só (readTimeout/connectTimeout do OkHttp, 20s
        // cada, em NetworkModule.kt). Numa rede mais lenta, uma única chamada já estourava o
        // teto do conjunto todo e o código antigo descartava TUDO que já tinha sido buscado até
        // ali, mostrando erro mesmo quando já existiam personagens novos prontos -- exatamente o
        // sintoma relatado ("carregar mais" sempre falhando, embora "puxar pra atualizar"
        // funcionasse, porque essa primeira leva normalmente precisa de bem menos chamadas).
        //
        // `result`/`requests` agora vivem FORA do bloco de timeout: se o tempo acabar (ou uma
        // chamada específica falhar) no meio do laço, o que já foi buscado nas chamadas
        // anteriores continua valendo e é salvo -- só devolvemos erro de verdade quando NADA foi
        // conseguido.
        val result = mutableListOf<CharacterDto>()
        var requests = 0

        try {
            withTimeoutOrNull(Constants.BATCH_LOAD_TIMEOUT_MILLIS) {
                // A Comic Vine costuma IGNORAR o filter=publisher:X no recurso /characters/
                // (falha conhecida da API, documentada desde a segunda rodada) -- por isso
                // SEMPRE filtramos por publisher no cliente também, o que pode descartar boa
                // parte de cada página; daí o teto ser de REQUISIÇÕES por leva, não só de
                // personagens reunidos.
                while (result.size < target &&
                    nextOffset < totalResults &&
                    requests < Constants.MAX_REQUESTS_PER_BATCH
                ) {
                    val response = api.getCharacters(offset = nextOffset)
                    requests++
                    totalResults = response.numberOfTotalResults
                    if (response.results.isEmpty()) break
                    result += response.results.filter { it.publisher?.id == Constants.MARVEL_PUBLISHER_ID }
                    nextOffset += Constants.CHARACTERS_PAGE_SIZE
                }
            }
        } catch (e: Exception) {
            // Uma exceção de verdade (não timeout) no meio do laço: o que já foi reunido nas
            // chamadas anteriores continua em `result` e será salvo do mesmo jeito abaixo. Só
            // devolvemos erro se não sobrou NADA pra mostrar.
            if (result.isEmpty()) {
                return Resource.Error(e.message ?: "Falha ao buscar personagens da Comic Vine", e)
            }
        }

        if (result.isEmpty() && requests == 0) {
            return Resource.Error("A Comic Vine demorou demais pra responder. Tente de novo em instantes.")
        }

        // Rodada 15, parte 17 (30/09/2026): removida a trava automática que existia aqui
        // ("uma leva que não achou nenhum Marvel novo marca exhausted = true"). Ela causava um
        // bug real: como o filtro de publisher da Comic Vine é furado, é normal e ESPERADO que
        // uma leva de "carregar mais" ocasionalmente não encontre nenhum Marvel dentro do
        // orçamento de MAX_REQUESTS_PER_BATCH (uma faixa mais pobre do catálogo) sem isso
        // significar que o catálogo acabou -- mas a trava marcava exhausted = true mesmo assim,
        // e como CollectionViewModel.loadMore() checa `hasMore` ANTES de sequer emitir
        // Resource.Loading, todo scroll seguinte virava um no-op silencioso: nenhuma requisição,
        // nenhuma barra de carregamento, nenhum toast, nenhum log -- exatamente o "para de
        // carregar sem erro nenhum" relatado. `exhausted` agora só fica `true` quando a Comic
        // Vine de fato disse que não há mais nada (nextOffset >= totalResults, o total BRUTO de
        // todos os publishers) -- o único caso em que insistir de verdade não adiantaria nada.
        // Uma leva "vazia" nessa faixa apenas devolve sucesso com 0 personagens novos; o usuário
        // pode simplesmente rolar mais um pouco pra tentar a próxima faixa do catálogo.
        if (nextOffset >= totalResults) exhausted = true

        val entities = result
            .distinctBy { it.id }
            .filter { !it.name.isNullOrBlank() }
            .map { it.toEntity() }

        if (replace) {
            dao.replaceAll(entities)
        } else if (entities.isNotEmpty()) {
            dao.insertAll(entities)
        }
        return Resource.Success(Unit)
    }

    private fun CharacterDto.toEntity(): CharacterEntity {
        val appearances = countOfIssueAppearances ?: 0
        val category = CharacterCategorizer.categorize(name ?: "")
        val stats = PowerCalculator.calculateStats(id, appearances)
        return CharacterEntity(
            comicVineId = id,
            name = name ?: "Desconhecido",
            realName = realName,
            imageUrl = image?.mediumUrl ?: image?.originalUrl,
            deck = deck,
            description = description,
            powers = powers?.mapNotNull { it.name } ?: emptyList(),
            publisherName = publisher?.name,
            countOfIssueAppearances = appearances,
            siteDetailUrl = siteDetailUrl,
            category = category.name,
            strength = stats.strength,
            speed = stats.speed,
            intelligence = stats.intelligence,
            durability = stats.durability,
            power = stats.power,
            combat = stats.combat,
            cachedAtMillis = System.currentTimeMillis()
        )
    }
}
