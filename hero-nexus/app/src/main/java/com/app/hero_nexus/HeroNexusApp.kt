package com.app.hero_nexus

import android.app.Application
import com.app.hero_nexus.data.local.AppDatabase
import com.app.hero_nexus.data.remote.NetworkModule
import com.app.hero_nexus.data.repository.BackgroundRepository
import com.app.hero_nexus.data.repository.CharacterRepository
import com.app.hero_nexus.data.repository.TranslationRepository
import com.app.hero_nexus.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HeroNexusApp : Application() {

    lateinit var characterRepository: CharacterRepository
        private set
    lateinit var userRepository: UserRepository
        private set
    lateinit var translationRepository: TranslationRepository
        private set
    lateinit var backgroundRepository: BackgroundRepository
        private set

    override fun onCreate() {
        super.onCreate()

        val db = AppDatabase.getInstance(this)
        characterRepository = CharacterRepository(NetworkModule.comicVineApi, db.characterDao())
        userRepository = UserRepository(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance())
        translationRepository = TranslationRepository(NetworkModule.myMemoryApi, db.translationDao())
        backgroundRepository = BackgroundRepository(NetworkModule.pexelsApi)
    }
}
