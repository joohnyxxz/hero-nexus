package com.app.hero_nexus.data.model

data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val xp: Int = 0,
    val coins: Int = 0,
    val level: Int = 1,

    val heroChests: Int = 0,
    val specialChests: Int = 0
) {
    companion object {
        const val XP_PER_LEVEL = 1000
        fun levelForXp(xp: Int): Int = (xp / XP_PER_LEVEL) + 1
    }

    val xpProgressInLevel: Float
        get() {
            val xpIntoLevel = xp % XP_PER_LEVEL
            return xpIntoLevel / XP_PER_LEVEL.toFloat()
        }
}
