package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.models.MapTileProvider
import com.example.domain.models.RoutePreference
import com.example.domain.models.TransitMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.commutePrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "commute_prefs")

data class UserPreferences(
    val userName: String = "Delhi NCR Commuter",
    val defaultRoutePreference: RoutePreference = RoutePreference.FASTEST,
    val preferredModes: Set<TransitMode> = setOf(
        TransitMode.METRO,
        TransitMode.BUS,
        TransitMode.RAILWAY,
        TransitMode.RRTS
    ),
    val maxWalkMeters: Int = 1200,
    val mapTileProvider: MapTileProvider = MapTileProvider.OPENFREEMAP_POSITRON,
    val backendApiBaseUrl: String = "http://10.0.2.2:8000",
    val offlineFirstMode: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val DEFAULT_ROUTE_PREF = stringPreferencesKey("default_route_pref")
        val PREFERRED_MODES = stringSetPreferencesKey("preferred_modes")
        val MAX_WALK_METERS = intPreferencesKey("max_walk_meters")
        val MAP_TILE_PROVIDER = stringPreferencesKey("map_tile_provider")
        val BACKEND_API_BASE_URL = stringPreferencesKey("backend_api_base_url")
        val OFFLINE_FIRST = booleanPreferencesKey("offline_first")
    }

    val preferencesFlow: Flow<UserPreferences> = context.commutePrefsDataStore.data.map { prefs ->
        val modeStrings = prefs[Keys.PREFERRED_MODES]
        val parsedModes = modeStrings?.mapNotNull { name ->
            TransitMode.entries.find { it.name == name }
        }?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf(
            TransitMode.METRO,
            TransitMode.BUS,
            TransitMode.RAILWAY,
            TransitMode.RRTS
        )

        val prefRoute = prefs[Keys.DEFAULT_ROUTE_PREF]?.let { name ->
            RoutePreference.entries.find { it.name == name }
        } ?: RoutePreference.FASTEST

        UserPreferences(
            userName = prefs[Keys.USER_NAME] ?: "Delhi NCR Commuter",
            defaultRoutePreference = prefRoute,
            preferredModes = parsedModes,
            maxWalkMeters = prefs[Keys.MAX_WALK_METERS] ?: 1200,
            mapTileProvider = MapTileProvider.fromId(prefs[Keys.MAP_TILE_PROVIDER] ?: MapTileProvider.OPENFREEMAP_POSITRON.id),
            backendApiBaseUrl = prefs[Keys.BACKEND_API_BASE_URL] ?: "http://10.0.2.2:8000",
            offlineFirstMode = prefs[Keys.OFFLINE_FIRST] ?: false
        )
    }

    suspend fun updateUserName(name: String) {
        context.commutePrefsDataStore.edit { it[Keys.USER_NAME] = name.trim().ifBlank { "Delhi NCR Commuter" } }
    }

    suspend fun updateDefaultRoutePreference(preference: RoutePreference) {
        context.commutePrefsDataStore.edit { it[Keys.DEFAULT_ROUTE_PREF] = preference.name }
    }

    suspend fun toggleTransitMode(mode: TransitMode) {
        context.commutePrefsDataStore.edit { prefs ->
            val current = prefs[Keys.PREFERRED_MODES]?.mapNotNull { n ->
                TransitMode.entries.find { it.name == n }
            }?.toMutableSet() ?: mutableSetOf(
                TransitMode.METRO,
                TransitMode.BUS,
                TransitMode.RAILWAY,
                TransitMode.RRTS
            )
            if (mode in current) {
                if (current.size > 1) current.remove(mode)
            } else {
                current.add(mode)
            }
            prefs[Keys.PREFERRED_MODES] = current.map { it.name }.toSet()
        }
    }

    suspend fun updateMaxWalkMeters(meters: Int) {
        context.commutePrefsDataStore.edit { it[Keys.MAX_WALK_METERS] = meters.coerceIn(300, 2000) }
    }

    suspend fun updateMapTileProvider(provider: MapTileProvider) {
        context.commutePrefsDataStore.edit { it[Keys.MAP_TILE_PROVIDER] = provider.id }
    }

    suspend fun updateBackendApiBaseUrl(url: String) {
        context.commutePrefsDataStore.edit { it[Keys.BACKEND_API_BASE_URL] = url.trim() }
    }

    suspend fun updateOfflineFirstMode(enabled: Boolean) {
        context.commutePrefsDataStore.edit { it[Keys.OFFLINE_FIRST] = enabled }
    }
}
