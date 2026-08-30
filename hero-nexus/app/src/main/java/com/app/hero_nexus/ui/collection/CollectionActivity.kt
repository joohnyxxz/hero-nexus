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

class CollectionActivity : MainNavActivity() {

    private lateinit var binding: ActivityCollectionBinding
    private lateinit var adapter: CharacterAdapter

    private val viewModel: CollectionViewModel by viewModels {
        viewModelFactory {
            initializer { CollectionViewModel(app.characterRepository, app.userRepository) }
        }
    }

    private var currentFilter = FilterType.ALL
    private var currentSort = SortType.POWER_DESC
    private var searchQuery = ""
    private var allCharacters: List<Character> = emptyList()

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
        binding.recyclerCharacters.layoutManager = GridLayoutManager(this, spanCount)
        binding.recyclerCharacters.adapter = adapter

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

        app.userRepository.currentUid?.let { viewModel.start(it) }
    }

    override fun onResume() {
        super.onResume()
        // Refaz o estado do jogador (ex: personagem desbloqueado num baú) sem gastar chamada à Comic Vine.
        app.userRepository.currentUid?.let { viewModel.refreshUserStatesOnly(it) }
        refreshProfileHeader(binding.topBar.textCoins, binding.topBar.textLevel)
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
