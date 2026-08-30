package com.app.hero_nexus.data.repository

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

/**
 * Tudo que é "progresso do jogador" (seção 5 - Arquitetura de dados / seção 25 - Banco de dados).
 * Autenticação via Firebase Auth; progresso via Firestore, sob users/{uid}/...
 */
class UserRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    val currentUid: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    private fun userDoc(uid: String) = firestore.collection(Constants.COL_USERS).document(uid)
    private fun charactersCol(uid: String) = userDoc(uid).collection(Constants.COL_USER_CHARACTERS)
    private fun deckDoc(uid: String) = userDoc(uid).collection(Constants.COL_DECK).document("current")
    private fun missionsCol(uid: String) = userDoc(uid).collection(Constants.COL_MISSIONS)
    private fun missionsCatalogCol() = firestore.collection(Constants.COL_MISSIONS_CATALOG)
    private fun skinsCol(uid: String) = userDoc(uid).collection(Constants.COL_SKINS)
    private fun battleHistoryCol(uid: String) = userDoc(uid).collection(Constants.COL_BATTLE_HISTORY)
    private fun usernamesCol() = firestore.collection("usernames")

    // ---------------------------------------------------------------- Cadastro / login (seção 6)

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

    // ---------------------------------------------------------------------------- Perfil (XP/moedas/nível)

    suspend fun getProfile(uid: String): UserProfile? =
        userDoc(uid).get().await().toObject(UserProfile::class.java)

    /** Soma XP e moedas de forma atômica e recalcula o nível (seção 19/20). */
    suspend fun addXpAndCoins(uid: String, xpDelta: Int, coinsDelta: Int): UserProfile {
        return firestore.runTransaction { tx ->
            val snap = tx.get(userDoc(uid))
            val current = snap.toObject(UserProfile::class.java) ?: UserProfile(uid = uid)
            val newXp = current.xp + xpDelta
            val newCoins = (current.coins + coinsDelta).coerceAtLeast(0)
            val updated = current.copy(xp = newXp, coins = newCoins, level = UserProfile.levelForXp(newXp))
            tx.set(userDoc(uid), updated)
            updated
        }.await()
    }

    suspend fun spendCoins(uid: String, amount: Int): Boolean {
        return firestore.runTransaction { tx ->
            val snap = tx.get(userDoc(uid))
            val current = snap.toObject(UserProfile::class.java) ?: UserProfile(uid = uid)
            if (current.coins < amount) {
                false
            } else {
                tx.update(userDoc(uid), "coins", current.coins - amount)
                true
            }
        }.await()
    }

    private fun chestField(type: ChestType) = if (type == ChestType.HEROI) "heroChests" else "specialChests"

    /** Chamado ao final de uma batalha vitoriosa (seção 18/23). */
    suspend fun awardChest(uid: String, type: ChestType) {
        userDoc(uid).update(chestField(type), com.google.firebase.firestore.FieldValue.increment(1)).await()
    }

    /** Consome um baú disponível de forma atômica; retorna false se não havia nenhum. */
    suspend fun tryConsumeChest(uid: String, type: ChestType): Boolean {
        return firestore.runTransaction { tx ->
            val snap = tx.get(userDoc(uid))
            val current = snap.toObject(UserProfile::class.java) ?: UserProfile(uid = uid)
            val count = if (type == ChestType.HEROI) current.heroChests else current.specialChests
            if (count <= 0) {
                false
            } else {
                tx.update(userDoc(uid), chestField(type), count - 1)
                true
            }
        }.await()
    }

    // --------------------------------------------------------------------- Personagens do jogador

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

    /**
     * Jogador novo precisa começar com personagens suficientes pra montar 1 time (seção 6/11) —
     * sem isso ele nunca conseguiria bater na aba Time/Batalha. Só mexe em nada se o jogador já
     * tiver QUALQUER personagem registrado (ou seja, roda no máximo uma vez por conta).
     */
    suspend fun ensureStarterCharacters(uid: String, starterIds: List<Int>) {
        if (starterIds.isEmpty()) return
        val existing = charactersCol(uid).limit(1).get().await()
        if (!existing.isEmpty) return
        val batch = firestore.batch()
        starterIds.take(Constants.STARTER_CHARACTER_COUNT).forEach { id ->
            batch.set(charactersCol(uid).document(id.toString()), mapOf("unlocked" to true), SetOptions.merge())
        }
        batch.commit().await()
    }

    suspend fun equipSkin(uid: String, characterId: Int, skinId: String?) {
        charactersCol(uid).document(characterId.toString())
            .set(mapOf("equippedSkinId" to skinId), SetOptions.merge())
            .await()
    }

    // --------------------------------------------------------------------------------- Time / deck

    suspend fun getTeam(uid: String): Team =
        deckDoc(uid).get().await().toObject(Team::class.java) ?: Team()

    suspend fun saveTeam(uid: String, characterIds: List<Int>) {
        deckDoc(uid).set(Team(characterIds.take(Constants.MAX_TEAM_SIZE))).await()
    }

    // ------------------------------------------------------------------------------------ Missões
    // O CATÁLOGO das missões (título/meta/recompensa) mora no Firestore, não fica mocado no app —
    // "missions_catalog" é uma coleção global (igual pra todo mundo). Na primeira leitura, se ela
    // ainda estiver vazia (projeto Firebase novo), semeamos com MissionCatalog.DEFAULTS uma única vez.

    suspend fun getMissionCatalog(): List<MissionDefinition> {
        val snapshot = missionsCatalogCol().get().await()
        if (snapshot.isEmpty) {
            seedMissionCatalog()
            return MissionCatalog.DEFAULTS
        }
        return snapshot.documents.mapNotNull { doc -> doc.toObject(MissionDefinition::class.java) }
    }

    private suspend fun seedMissionCatalog() {
        val batch = firestore.batch()
        MissionCatalog.DEFAULTS.forEach { def -> batch.set(missionsCatalogCol().document(def.id), def) }
        batch.commit().await()
    }

    suspend fun getMissionProgress(uid: String): Map<String, MissionProgress> {
        val snapshot = missionsCol(uid).get().await()
        return snapshot.documents.associate { doc ->
            doc.id to (doc.toObject(MissionProgress::class.java) ?: MissionProgress(id = doc.id))
        }
    }

    /** A meta (target) vem do catálogo no banco — quem chama não precisa mais saber esse número. */
    suspend fun incrementMissionProgress(uid: String, missionId: String, amount: Int) {
        val target = getMissionCatalog().firstOrNull { it.id == missionId }?.target ?: return
        firestore.runTransaction { tx ->
            val ref = missionsCol(uid).document(missionId)
            val snap = tx.get(ref)
            val current = snap.toObject(MissionProgress::class.java) ?: MissionProgress(id = missionId)
            if (current.completed) return@runTransaction
            val newProgress = (current.progress + amount).coerceAtMost(target)
            tx.set(ref, current.copy(id = missionId, progress = newProgress, completed = newProgress >= target))
        }.await()
    }

    suspend fun claimMission(uid: String, missionId: String) {
        missionsCol(uid).document(missionId).set(mapOf("claimed" to true), SetOptions.merge()).await()
    }

    // --------------------------------------------------------------------------------------- Skins

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

    // ------------------------------------------------------------------------------ Histórico de batalha

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
