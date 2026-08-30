package com.app.hero_nexus

import android.app.Application
import com.app.hero_nexus.data.local.AppDatabase
import com.app.hero_nexus.data.remote.NetworkModule
import com.app.hero_nexus.data.repository.CharacterRepository
import com.app.hero_nexus.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Container manual de dependências (sem Hilt/Koin para manter o projeto simples de entender).
 * As Activities acessam os repositórios via (application as HeroNexusApp).xxxRepository.
 */
class HeroNexusApp : Application() {

    lateinit var characterRepository: CharacterRepository
        private set
    lateinit var userRepository: UserRepository
        private set

    override fun onCreate() {
        super.onCreate()

        val db = AppDatabase.getInstance(this)
        characterRepository = CharacterRepository(NetworkModule.comicVineApi, db.characterDao())
        userRepository = UserRepository(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance())
    }
}
