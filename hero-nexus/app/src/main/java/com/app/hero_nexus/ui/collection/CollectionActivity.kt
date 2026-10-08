package com.app.hero_nexus.ui.collection

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.app.hero_nexus.data.model.Rarity
import com.app.hero_nexus.databinding.ActivityCollectionBinding
import com.app.hero_nexus.ui.common.MainNavActivity
import com.app.hero_nexus.ui.detail.CharacterDetailActivity
import com.app.hero_nexus.util.Resource
import com.app.hero_nexus.util.dpToPx
import com.app.hero_nexus.util.visibleIf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class FilterType {
    ALL, UNLOCKED, LOCKED, HEROES, ANTIHEROES, VILLAINS,
    RARITY_COMUM, RARITY_RARO, RARITY_EPICO, RARITY_LENDARIO
}

private const val LOAD_MORE_THRESHOLD = 6

private const val SEARCH_REMOTE_DEBOUNCE_MS = 600L

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
    private var searchQuery = ""
    private var allCharacters: List<Character> = emptyList()

    private var loadMoreIsLoading = false
    private var isNearListEnd = false

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

        binding.recyclerCharacters.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {

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

        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                R.id.chipUnlocked -> FilterType.UNLOCKED
                R.id.chipLocked -> FilterType.LOCKED
                R.id.chipHeroes -> FilterType.HEROES
                R.id.chipAntiHeroes -> FilterType.ANTIHEROES
                R.id.chipVillains -> FilterType.VILLAINS
                R.id.chipRarityComum -> FilterType.RARITY_COMUM
                R.id.chipRarityRaro -> FilterType.RARITY_RARO
                R.id.chipRarityEpico -> FilterType.RARITY_EPICO
                R.id.chipRarityLendario -> FilterType.RARITY_LENDARIO
                else -> FilterType.ALL
            }
            applyFiltersAndRender()
        }

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

    private fun scheduleRemoteSearchIfNeeded() {
        searchDebounceJob?.cancel()
        val query = searchQuery.trim()
        if (query.length < MIN_REMOTE_SEARCH_QUERY_LENGTH) return
        searchDebounceJob = lifecycleScope.launch {
            delay(SEARCH_REMOTE_DEBOUNCE_MS)
            if (searchQuery.trim() != query) return@launch

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
            FilterType.RARITY_COMUM -> list.filter { it.rarity == Rarity.COMUM }
            FilterType.RARITY_RARO -> list.filter { it.rarity == Rarity.RARO }
            FilterType.RARITY_EPICO -> list.filter { it.rarity == Rarity.EPICO }
            FilterType.RARITY_LENDARIO -> list.filter { it.rarity == Rarity.LENDARIO }
        }

        if (searchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        list = list.sortedByDescending { it.stats.overallPower }

        adapter.submitList(list)
        binding.textEmpty.visibleIf(list.isEmpty())
    }
}
