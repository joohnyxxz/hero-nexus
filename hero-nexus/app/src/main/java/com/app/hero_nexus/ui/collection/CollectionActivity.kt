package com.app.hero_nexus.ui.collection

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.viewModels
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

private enum class FilterType { ALL, UNLOCKED, LOCKED, HEROES, ANTIHEROES, VILLAINS }
private enum class SortType { POWER_DESC, POWER_ASC, NAME }

/** Quantos itens de folga antes do fim da lista visível já disparam a busca da próxima leva. */
private const val LOAD_MORE_THRESHOLD = 6

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
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

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
