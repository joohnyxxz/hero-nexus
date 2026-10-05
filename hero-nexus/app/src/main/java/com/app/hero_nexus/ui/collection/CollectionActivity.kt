package com.app.hero_nexus.ui.collection

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.hero_nexus.R
import com.app.hero_nexus.data.model.Character
import com.app.hero_nexus.data.model.CharacterCategory
import com.app.hero_nexus.databinding.ActivityCollectionBinding
import com.app.hero_nexus.ui.common.MainNavActivity
import com.app.hero_nexus.ui.detail.CharacterDetailActivity
import com.app.hero_nexus.util.Resource
import com.app.hero_nexus.util.dpToPx
import com.app.hero_nexus.util.visibleIf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class FilterType { ALL, UNLOCKED, LOCKED, HEROES, ANTIHEROES, VILLAINS }
private enum class SortType { POWER_DESC, POWER_ASC, NAME }

/** Quantos itens de folga antes do fim da lista visível já disparam a busca da próxima leva. */
private const val LOAD_MORE_THRESHOLD = 6

/** Rodada 15, parte 40 (04/10/2026): tempo que a barra de pesquisa espera depois da última
 * tecla digitada antes de considerar buscar ao vivo na Comic Vine -- evita disparar uma
 * requisição de rede a cada letra enquanto o usuário ainda está digitando. */
private const val SEARCH_REMOTE_DEBOUNCE_MS = 600L

/** Buscas com menos letras que isso nunca vão pra rede -- "h" ou "a" sozinhos bateriam na API
 * sem necessidade e trariam resultado ambíguo demais pra valer a pena. */
private const val MIN_REMOTE_SEARCH_QUERY_LENGTH = 2

class CollectionActivity : MainNavActivity() {

    private lateinit var binding: ActivityCollectionBinding
    private lateinit var adapter: CharacterAdapter
    private lateinit var layoutManager: GridLayoutManager

    private val viewModel: CollectionViewModel by viewModels {
        viewModelFactory {
            initializer { CollectionViewModel(app.characterRepository, app.userRepository) }
        }
    }

    private var currentFilter = FilterType.ALL
    private var currentSort = SortType.POWER_DESC
    private var searchQuery = ""
    private var allCharacters: List<Character> = emptyList()

    // Rodada 15, parte 16 (30/09/2026): a ProgressBar de "carregar mais" só deve aparecer
    // enquanto as DUAS coisas forem verdade ao mesmo tempo -- uma leva está de fato carregando E
    // o usuário está perto do fim da lista. Sem isso, se o usuário rolar de volta pra cima
    // enquanto uma leva anterior ainda está em andamento, a barra (fixada no rodapé do
    // FrameLayout, não presa a uma posição da lista) continuava visível, flutuando por cima dos
    // cards já vistos -- feio e sem sentido, apontado pelo usuário.
    private var loadMoreIsLoading = false
    private var isNearListEnd = false

    /** Rodada 15, parte 40: job do debounce da busca ao vivo -- cancelado e reagendado a cada
     * tecla digitada (ver scheduleRemoteSearchIfNeeded()). */
    private var searchDebounceJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar(
            binding.topBar.textScreenTitle,
            binding.topBar.textCoins,
            binding.topBar.textLevel,
            binding.topBar.buttonLogout,
            R.string.collection_title
        )
        setupBottomNav(binding.bottomNav, R.id.nav_collection)

        adapter = CharacterAdapter { character ->
            startActivity(
                Intent(this, CharacterDetailActivity::class.java)
                    .putExtra(CharacterDetailActivity.EXTRA_CHARACTER_ID, character.id)
            )
        }
        val spanCount = (resources.displayMetrics.widthPixels / dpToPx(150f)).coerceAtLeast(2)
        layoutManager = GridLayoutManager(this, spanCount)
        binding.recyclerCharacters.layoutManager = layoutManager
        binding.recyclerCharacters.adapter = adapter

        // Rodada 15, parte 13 (30/09/2026): listagem "por partes" de novo -- desta vez o
        // indicador de "carregando mais" é só uma ProgressBar comum no layout
        // (progressLoadMore, em activity_collection.xml), nunca um item dentro do próprio
        // RecyclerView. Isso evita de vez a causa raiz real já achada numa rodada anterior
        // (IllegalStateException por inserir/remover item do adapter no meio de um callback de
        // scroll) -- aqui o listener só chama viewModel.loadMore(), que só muda uma LiveData;
        // quem decide o que desenhar na tela é sempre o observer normal, nunca o callback em si.
        //
        // Rodada 15, parte 15 (30/09/2026): usuário reportou que a ProgressBar de "carregar
        // mais" não ficava firme durante o carregamento -- piscava/sumia sem motivo aparente.
        // Causa: viewModelScope.launch usa Dispatchers.Main.immediate por padrão, e
        // onScrolled() já roda na thread principal -- então a primeira linha de
        // CollectionViewModel.loadMore() (_loadMoreState.value = Resource.Loading) executava de
        // forma SÍNCRONA, ainda dentro do próprio callback de scroll, mudando a visibilidade da
        // ProgressBar no meio de um passe de layout/scroll em andamento (mesma classe de bug já
        // vista com o adapter na parte 8, agora afetando uma view comum em vez do adapter).
        // Adiado com recyclerView.post{} -- viewModel.loadMore() (e a emissão da LiveData que
        // ele dispara) só roda depois que o passe de scroll atual termina, nunca no meio dele.
        binding.recyclerCharacters.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Atualiza "perto do fim" em QUALQUER scroll (não só descendo) -- é o que
                // permite esconder a barra de novo se o usuário rolar de volta pra cima.
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val nearEnd = lastVisible >= adapter.itemCount - LOAD_MORE_THRESHOLD
                setNearListEnd(nearEnd)
                if (dy > 0 && nearEnd) {
                    recyclerView.post { viewModel.loadMore() }
                }
            }
        })

        binding.swipeRefresh.setOnRefreshListener {
            app.userRepository.currentUid?.let { viewModel.refresh(it) }
        }

        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                applyFiltersAndRender()
                scheduleRemoteSearchIfNeeded()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        // Rodada 15, parte 43 (04/10/2026): a tentativa da parte 42 de dar funcao propria pro
        // icone de busca do teclado (disparar a busca na hora, sem esperar o debounce) foi
        // removida por pedido explicito do usuario -- "tira essa parte de ter que clicar no
        // icone de pesquisar... faz automatico quando percebe alteracao na barra de pesquisa".
        // A busca remota continua 100% automatica: scheduleRemoteSearchIfNeeded() (debounce de
        // 600ms, chamado a cada tecla pelo TextWatcher acima) ja dispara sozinha sem precisar
        // de nenhum clique em nada.

        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                R.id.chipUnlocked -> FilterType.UNLOCKED
                R.id.chipLocked -> FilterType.LOCKED
                R.id.chipHeroes -> FilterType.HEROES
                R.id.chipAntiHeroes -> FilterType.ANTIHEROES
                R.id.chipVillains -> FilterType.VILLAINS
                else -> FilterType.ALL
            }
            applyFiltersAndRender()
        }

        binding.buttonSort.setOnClickListener { showSortMenu() }

        viewModel.characters.observe(this) { list ->
            allCharacters = list
            applyFiltersAndRender()
        }

        viewModel.refreshState.observe(this) { state ->
            binding.swipeRefresh.isRefreshing = state is Resource.Loading
            if (state is Resource.Error) {
                Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.loadMoreState.observe(this) { state ->
            loadMoreIsLoading = state is Resource.Loading
            updateLoadMoreIndicator()
            if (state is Resource.Error) {
                Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
            }
        }

        // Rodada 15, parte 40 (04/10/2026): resultado da busca ao vivo (ver
        // scheduleRemoteSearchIfNeeded()). Quando acha algo, a tela já atualiza sozinha --
        // o novo personagem entra no Room e o Flow observado por viewModel.characters (acima)
        // já refiltra com a mesma searchQuery automaticamente, sem precisar de nada aqui.
        // Só avisamos o usuário nos 2 casos em que NADA aparece sozinho: erro de rede, ou busca
        // que de fato não achou nenhum Marvel com esse nome no catálogo da Comic Vine.
        viewModel.remoteSearchState.observe(this) { state ->
            when {
                state is Resource.Error ->
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                state is Resource.Success && state.data == 0 ->
                    Toast.makeText(this, getString(R.string.collection_search_remote_empty), Toast.LENGTH_SHORT).show()
            }
        }

        app.userRepository.currentUid?.let { viewModel.start(it) }
    }

    override fun onResume() {
        super.onResume()
        // Refaz o estado do jogador (ex: personagem desbloqueado num baú) sem gastar chamada à Comic Vine.
        app.userRepository.currentUid?.let { viewModel.refreshUserStatesOnly(it) }
        refreshProfileHeader(binding.topBar.textCoins, binding.topBar.textLevel)
    }

    private fun setNearListEnd(nearEnd: Boolean) {
        isNearListEnd = nearEnd
        updateLoadMoreIndicator()
    }

    private fun updateLoadMoreIndicator() {
        binding.progressLoadMore.visibleIf(loadMoreIsLoading && isNearListEnd)
    }

    private fun showSortMenu() {
        val popup = PopupMenu(this, binding.buttonSort)
        popup.menu.add(0, 0, 0, R.string.sort_power_desc)
        popup.menu.add(0, 1, 1, R.string.sort_power_asc)
        popup.menu.add(0, 2, 2, R.string.sort_name)
        popup.setOnMenuItemClickListener { item ->
            currentSort = when (item.itemId) {
                1 -> SortType.POWER_ASC
                2 -> SortType.NAME
                else -> SortType.POWER_DESC
            }
            applyFiltersAndRender()
            true
        }
        popup.show()
    }

    /**
     * Rodada 15, parte 40 (04/10/2026): se a busca local (sobre o que já está em cache) não
     * achou NENHUM personagem com esse nome, espera um pouco (debounce) e então busca de
     * verdade na Comic Vine por esse nome -- resolve o caso relatado de personagens que existem
     * de verdade na Marvel (ex: Hulk, Viúva Negra) mas ainda não calharam de entrar na leva por
     * popularidade que alimenta a lista (ver comentário em CharacterRepository.fetchBatch()).
     *
     * Checa de novo "existe localmente?" DEPOIS do delay (não só antes) -- o usuário pode ter
     * digitado mais letras ou apagado tudo nesse meio tempo, e não queremos buscar uma query que
     * já não é mais a atual.
     */
    private fun scheduleRemoteSearchIfNeeded() {
        searchDebounceJob?.cancel()
        val query = searchQuery.trim()
        if (query.length < MIN_REMOTE_SEARCH_QUERY_LENGTH) return
        searchDebounceJob = lifecycleScope.launch {
            delay(SEARCH_REMOTE_DEBOUNCE_MS)
            if (searchQuery.trim() != query) return@launch
            // Rodada 15, parte 42 (04/10/2026): antes, bastava ALGUM personagem no cache ter o
            // texto buscado como SUBSTRING do nome pra ja desistir de buscar na Comic Vine --
            // bug real relatado pelo usuario: "She-Hulk" ja em cache contem "hulk", entao
            // pesquisar "Hulk" nunca chegava a bater na API, mesmo o Hulk (personagem
            // DIFERENTE) nao estando no cache. Agora so pula a busca remota se o nome ja em
            // cache for EXATAMENTE igual ao texto buscado (ignorando caixa) -- ai sim ja temos
            // esse personagem especifico, sem gastar uma chamada de rede a toa.
            val hasExactLocalMatch = allCharacters.any { it.name.equals(query, ignoreCase = true) }
            if (!hasExactLocalMatch) viewModel.searchRemote(query)
        }
    }

    private fun applyFiltersAndRender() {
        var list = allCharacters

        list = when (currentFilter) {
            FilterType.ALL -> list
            FilterType.UNLOCKED -> list.filter { it.unlocked }
            FilterType.LOCKED -> list.filter { !it.unlocked }
            FilterType.HEROES -> list.filter { it.category == CharacterCategory.HEROI }
            FilterType.ANTIHEROES -> list.filter { it.category == CharacterCategory.ANTI_HEROI }
            FilterType.VILLAINS -> list.filter { it.category == CharacterCategory.VILAO }
        }

        if (searchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        list = when (currentSort) {
            SortType.POWER_DESC -> list.sortedByDescending { it.stats.overallPower }
            SortType.POWER_ASC -> list.sortedBy { it.stats.overallPower }
            SortType.NAME -> list.sortedBy { it.name }
        }

        adapter.submitList(list)
        binding.textEmpty.visibleIf(list.isEmpty())
    }
}
