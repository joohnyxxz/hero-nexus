package com.app.hero_nexus.data.model

import java.util.Calendar

data class MissionDefinition(
    val id: String = "",
    val title: String = "",
    val target: Int = 0,
    val rewardXp: Int = 0,
    val rewardCoins: Int = 0,
    val category: String = "daily"
)

data class MissionProgress(
    val id: String = "",
    val progress: Int = 0,
    val completed: Boolean = false,
    val claimed: Boolean = false,
    val lastResetAt: Long = 0
)

data class Mission(
    val definition: MissionDefinition,
    val progress: MissionProgress
)

object MissionCatalog {
    val DEFAULTS = listOf(

        MissionDefinition("daily_kill_enemies_t1", "Eliminador I: Derrote 15 inimigos", target = 15, rewardXp = 80, rewardCoins = 40, category = "daily"),
        MissionDefinition("daily_break_crates_t1", "Saqueador I: Quebre 8 caixas", target = 8, rewardXp = 40, rewardCoins = 80, category = "daily"),
        MissionDefinition("daily_complete_stage_t1", "Explorador I: Complete 1 fase", target = 1, rewardXp = 100, rewardCoins = 50, category = "daily"),

        MissionDefinition("daily_kill_enemies_t2", "Eliminador II: Derrote 40 inimigos", target = 40, rewardXp = 200, rewardCoins = 100, category = "daily"),
        MissionDefinition("daily_break_crates_t2", "Saqueador II: Quebre 20 caixas", target = 20, rewardXp = 100, rewardCoins = 200, category = "daily"),

        MissionDefinition("weekly_boss_hunter_t1", "Caçador de Lendas I: Derrote 3 chefões", target = 3, rewardXp = 800, rewardCoins = 400, category = "weekly"),
        MissionDefinition("weekly_mass_slayer_t1", "Guerreiro I: Derrote 150 inimigos", target = 150, rewardXp = 600, rewardCoins = 300, category = "weekly"),

        MissionDefinition("weekly_boss_hunter_t2", "Caçador de Lendas II: Derrote 10 chefões", target = 10, rewardXp = 2000, rewardCoins = 1000, category = "weekly"),
        MissionDefinition("weekly_mass_slayer_t2", "Guerreiro II: Derrote 500 inimigos", target = 500, rewardXp = 1500, rewardCoins = 750, category = "weekly"),
        MissionDefinition("weekly_treasure_seeker_t1", "Caça-Tesouros: Quebre 80 caixas", target = 80, rewardXp = 400, rewardCoins = 800, category = "weekly")
    )

    fun isDifferentDay(last: Long, now: Long): Boolean {
        if (last == 0L) return false
        val calLast = Calendar.getInstance().apply { timeInMillis = last }
        val calNow = Calendar.getInstance().apply { timeInMillis = now }
        return calLast.get(Calendar.DAY_OF_YEAR) != calNow.get(Calendar.DAY_OF_YEAR) ||
                calLast.get(Calendar.YEAR) != calNow.get(Calendar.YEAR)
    }

    fun isDifferentWeek(last: Long, now: Long): Boolean {
        if (last == 0L) return false
        val calLast = Calendar.getInstance().apply { timeInMillis = last }
        val calNow = Calendar.getInstance().apply { timeInMillis = now }
        return calLast.get(Calendar.WEEK_OF_YEAR) != calNow.get(Calendar.WEEK_OF_YEAR) ||
                calLast.get(Calendar.YEAR) != calNow.get(Calendar.YEAR)
    }
}
