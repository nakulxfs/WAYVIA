package com.example

import android.app.Application
import com.example.data.local.CommuteDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.repositories.TransitRepository
import com.example.domain.awareness.TransitAwarenessEngine
import com.example.domain.chat.TransitChatEngine
import com.example.services.location.LocationService
import org.maplibre.android.MapLibre

class AppContainer(application: Application) {
    val database: CommuteDatabase = CommuteDatabase.getInstance(application)
    val preferencesRepository: UserPreferencesRepository = UserPreferencesRepository(application)
    val transitRepository: TransitRepository = TransitRepository(application, database)
    val awarenessEngine: TransitAwarenessEngine = TransitAwarenessEngine()
    val locationService: LocationService = LocationService(application)
    val chatEngine: TransitChatEngine
        get() = TransitChatEngine(transitRepository.routingEngine)
}

class CommuteBuddyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        runCatching { MapLibre.getInstance(this) }
        container = AppContainer(this)
    }
}
