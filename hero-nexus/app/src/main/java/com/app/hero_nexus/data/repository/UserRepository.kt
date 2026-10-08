package com.app.hero_nexus.data.repository

import android.util.Log
import com.app.hero_nexus.data.model.BattleResult
import com.app.hero_nexus.data.model.ChestType
import com.app.hero_nexus.data.model.MissionCatalog
import com.app.hero_nexus.data.model.MissionDefinition
import com.app.hero_nexus.data.model.MissionProgress
import com.app.hero_nexus.data.model.Team
import com.app.hero_nexus.data.model.UserCharacterState
import com.app.hero_nexus.data.model.UserProfile
import com.app.hero_nexus.util.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    val currentUid: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    companion object {
        private const val FLAG_VILLAIN_RESET_V1 = "villainResetV1Applied"
    }

    private fun userDoc(uid: String) = firestore.collection(Constants.COL_USERS).document(uid)
    private fun charactersCol(uid: String) = userDoc(uid).collection(Constants.COL_USER_CHARACTERS)
    private fun deckDoc(uid: String) = userDoc(uid).collection(Constants.COL_DECK).document("current")
    private fun missionsCol(uid: String) = userDoc(uid).collection(Constants.COL_MISSIONS)
    private fun missionsCatalogCol() = firestore.collection(Constants.COL_MISSIONS_CATALOG)
    private fun skinsCol(uid: String) = userDoc(uid).collection(Constants.COL_SKINS)
    private fun battleHistoryCol(uid: String) = userDoc(uid).collection(Constants.COL_BATTLE_HISTORY)
    private fun usernamesCol() = firestore.collection("usernames")

    suspend fun register(username: String, email: String, password: String): Result<Unit> = runCatching {
        val usernameKey = username.trim().lowercase()
        val takenDoc = usernamesCol().document(usernameKey).get().await()
        require(!takenDoc.exists()) { "Esse nome de usuário já está em uso." }

        val authResult = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val uid = authResult.user?.uid ?: error("Falha ao criar usuário.")

        val profile = UserProfile(uid = uid, username = username.trim(), email = email.trim())
        userDoc(uid).set(profile).await()
        usernamesCol().document(usernameKey).set(mapOf("uid" to uid, "email" to email.trim())).await()
    }

    suspend fun login(emailOrUsername: String, password: String): Result<Unit> = runCatching {
        val input = emailOrUsername.trim()
        val email = if (input.contains("@")) {
            input
        } else {
            val doc = usernamesCol().document(input.lowercase()).get().await()
            (doc.get("email") as? String) ?: error("Usuário não encontrado.")
        }
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    fun logout() = auth.signOut()

    suspend fun getProfile(uid: String): UserProfile? =
        userDoc(uid).get().await().toObject(UserProfile::class.java)

    suspend fun addXpAndCoins(uid: String, xpDelta: Int, coinsDelta: Int): UserProfile {
        return firestore.runTransaction { tx ->
            val snap = tx.get(userDoc(uid))
            val current = snap.toObject(UserProfile::class.java) ?: UserProfile(uid = uid)
            val newXp = current.xp + xpDelta
            val newCoins = (current.coins + coinsDelta).coerceAtLeast(0)
            val newLevel = UserProfile.levelForXp(newXp)

            tx.set(
                userDoc(uid),
                mapOf(
                    "uid" to uid,
                    "xp" to newXp,
                    "coins" to newCoins,
                    "level" to newLevel
                ),
                SetOptions.merge()
            )
            current.copy(xp = newXp, coins = newCoins, level = newLevel)
        }.await()
    }

    suspend fun spendCoins(uid: String, amount: Int): Boolean {
        return firestore.runTransaction { tx ->
            val snap = tx.get(userDoc(uid))
            val current = snap.toObject(UserProfile::class.java) ?: UserProfile(uid = uid)
            if (current.coins < amount) {
                false
            } else {

                tx.set(userDoc(uid), mapOf("uid" to uid, "coins" to (current.coins - amount)), SetOptions.merge())
                true
            }
        }.await()
    }

    private fun chestField(type: ChestType) = if (type == ChestType.HEROI) "heroChests" else "specialChests"

    suspend fun awardChest(uid: String, type: ChestType) {

        userDoc(uid).set(
            mapOf("uid" to uid, chestField(type) to com.google.firebase.firestore.FieldValue.increment(1)),
            SetOptions.merge()
        ).await()
    }

    suspend fun tryConsumeChest(uid: String, type: ChestType): Boolean {
        return firestore.runTransaction { tx ->
            val snap = tx.get(userDoc(uid))
            val current = snap.toObject(UserProfile::class.java) ?: UserProfile(uid = uid)
            val count = if (type == ChestType.HEROI) current.heroChests else current.specialChests
            if (count <= 0) {
                false
            } else {
                tx.set(userDoc(uid), mapOf("uid" to uid, chestField(type) to (count - 1)), SetOptions.merge())
                true
            }
        }.await()
    }

    suspend fun getCharacterStates(uid: String): Map<Int, UserCharacterState> {
        val snapshot = charactersCol(uid).get().await()
        return snapshot.documents.associate { doc ->
            (doc.id.toIntOrNull() ?: -1) to (doc.toObject(UserCharacterState::class.java) ?: UserCharacterState())
        }.filterKeys { it != -1 }
    }

    suspend fun getCharacterState(uid: String, characterId: Int): UserCharacterState =
        charactersCol(uid).document(characterId.toString()).get().await()
            .toObject(UserCharacterState::class.java) ?: UserCharacterState()

    suspend fun unlockCharacter(uid: String, characterId: Int) {
        charactersCol(uid).document(characterId.toString())
            .set(mapOf("unlocked" to true), SetOptions.merge())
            .await()
    }

    suspend fun ensureStarterCharacters(uid: String, starterIds: List<Int>) {
        if (starterIds.isEmpty()) return

        val existing = charactersCol(uid).get().await()
        val alreadyHasUnlocked = existing.documents.any { it.getBoolean("unlocked") == true }
        if (alreadyHasUnlocked) return

        val batch = firestore.batch()
        val idsToUse = starterIds.take(Constants.STARTER_CHARACTER_COUNT)

        idsToUse.forEach { id ->
            batch.set(charactersCol(uid).document(id.toString()), mapOf("unlocked" to true), SetOptions.merge())
        }

        batch.set(deckDoc(uid), Team(idsToUse))

        batch.commit().await()
    }

    suspend fun equipSkin(uid: String, characterId: Int, skinId: String?) {
        charactersCol(uid).document(characterId.toString())
            .set(mapOf("equippedSkinId" to skinId), SetOptions.merge())
            .await()
    }

    suspend fun runVillainResetOnceIfNeeded(uid: String, villainIds: List<Int>) {
        if (villainIds.isEmpty()) return
        val snap = userDoc(uid).get().await()
        if (snap.getBoolean(FLAG_VILLAIN_RESET_V1) == true) return
        val batch = firestore.batch()
        villainIds.forEach { id ->
            batch.set(charactersCol(uid).document(id.toString()), mapOf("unlocked" to false), SetOptions.merge())
        }
        batch.set(userDoc(uid), mapOf("uid" to uid, FLAG_VILLAIN_RESET_V1 to true), SetOptions.merge())
        batch.commit().await()
    }

    suspend fun getTeam(uid: String): Team =
        deckDoc(uid).get().await().toObject(Team::class.java) ?: Team()

    suspend fun saveTeam(uid: String, characterIds: List<Int>) {
        deckDoc(uid).set(Team(characterIds.take(Constants.MAX_TEAM_SIZE))).await()
    }

    suspend fun getMissionCatalog(): List<MissionDefinition> {
        val snapshot = missionsCatalogCol().get().await()
        val existing = snapshot.documents.mapNotNull { doc -> doc.toObject(MissionDefinition::class.java) }
        val existingIds = existing.map { it.id }.toSet()
        val added = upsertMissingMissionDefs(existingIds)
        return existing + added
    }

    private suspend fun upsertMissingMissionDefs(existingIds: Set<String>): List<MissionDefinition> {
        val missing = MissionCatalog.DEFAULTS.filter { it.id !in existingIds }
        if (missing.isEmpty()) return emptyList()
        val batch = firestore.batch()
        missing.forEach { def -> batch.set(missionsCatalogCol().document(def.id), def) }
        batch.commit().await()
        return missing
    }

    suspend fun getMissionProgress(uid: String): Map<String, MissionProgress> {
        val snapshot = missionsCol(uid).get().await()
        return snapshot.documents.associate { doc ->
            doc.id to (doc.toObject(MissionProgress::class.java) ?: MissionProgress(id = doc.id))
        }
    }

    suspend fun incrementMissionProgress(uid: String, missionId: String, amount: Int) {
        val def = getMissionCatalog().firstOrNull { it.id == missionId }
        if (def == null) {
            Log.e("UserRepository", "incrementMissionProgress: missionId desconhecido no catálogo: $missionId")
            return
        }
        val target = def.target
        val now = System.currentTimeMillis()

        firestore.runTransaction { tx ->
            val ref = missionsCol(uid).document(missionId)
            val snap = tx.get(ref)
            val current = snap.toObject(MissionProgress::class.java) ?: MissionProgress(id = missionId)

            val shouldReset = when (def.category) {
                "daily" -> MissionCatalog.isDifferentDay(current.lastResetAt, now)
                "weekly" -> MissionCatalog.isDifferentWeek(current.lastResetAt, now)
                else -> false
            }

            val baseProgress = if (shouldReset) 0 else current.progress
            val baseCompleted = if (shouldReset) false else current.completed
            val baseClaimed = if (shouldReset) false else current.claimed

            val finalLastResetAt = if (shouldReset || current.lastResetAt == 0L) now else current.lastResetAt

            if (baseCompleted && !shouldReset) return@runTransaction

            val newProgress = (baseProgress + amount).coerceAtMost(target)
            tx.set(
                ref, current.copy(
                    id = missionId,
                    progress = newProgress,
                    completed = newProgress >= target,
                    claimed = baseClaimed,
                    lastResetAt = finalLastResetAt
                )
            )
        }.await()
    }

    suspend fun claimMission(uid: String, missionId: String) {
        firestore.runTransaction { tx ->
            val ref = missionsCol(uid).document(missionId)
            val current = tx.get(ref).toObject(MissionProgress::class.java) ?: MissionProgress(id = missionId)
            if (!current.completed) {
                throw IllegalStateException("Essa missão ainda não foi concluída.")
            }
            if (current.claimed) {
                throw IllegalStateException("Essa recompensa já foi resgatada.")
            }
            tx.set(ref, mapOf("claimed" to true), SetOptions.merge())
        }.await()
    }

    suspend fun getOwnedSkinIds(uid: String): Set<String> =
        skinsCol(uid).get().await().documents.map { it.id }.toSet()

    suspend fun purchaseSkin(uid: String, skinId: String, priceCoins: Int): Boolean {
        if (priceCoins <= 0) {
            skinsCol(uid).document(skinId).set(mapOf("purchasedAt" to System.currentTimeMillis())).await()
            return true
        }
        val paid = spendCoins(uid, priceCoins)
        if (paid) {
            skinsCol(uid).document(skinId).set(mapOf("purchasedAt" to System.currentTimeMillis())).await()
        }
        return paid
    }

    suspend fun recordBattleResult(uid: String, result: BattleResult) {
        battleHistoryCol(uid).add(
            mapOf(
                "victory" to result.victory,
                "enemiesDefeated" to result.enemiesDefeated,
                "bossName" to result.bossName,
                "xpGained" to result.xpGained,
                "coinsGained" to result.coinsGained,
                "timestamp" to System.currentTimeMillis()
            )
        ).await()
    }
}
