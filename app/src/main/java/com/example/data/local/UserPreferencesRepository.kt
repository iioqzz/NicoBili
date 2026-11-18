package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DanmakuFilterConfig
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nicobili_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SESSION_COOKIE = stringPreferencesKey("nico_session_cookie")
        val USER_NICKNAME = stringPreferencesKey("nico_user_nickname")
        val USER_ID = stringPreferencesKey("nico_user_id")
        val USER_ICON = stringPreferencesKey("nico_user_icon")
        val DANMAKU_ENABLED = booleanPreferencesKey("danmaku_enabled")
        val DANMAKU_SCROLL = booleanPreferencesKey("danmaku_scroll")
        val DANMAKU_TOP = booleanPreferencesKey("danmaku_top")
        val DANMAKU_BOTTOM = booleanPreferencesKey("danmaku_bottom")
        val DANMAKU_COLORED = booleanPreferencesKey("danmaku_colored")
        val DANMAKU_OPACITY = floatPreferencesKey("danmaku_opacity")
        val DANMAKU_SPEED = floatPreferencesKey("danmaku_speed")
        val DANMAKU_SCALE = floatPreferencesKey("danmaku_scale")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.THEME_MODE]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val sessionCookieFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SESSION_COOKIE] ?: ""
    }

    val danmakuPrefsFlow: Flow<DanmakuFilterConfig> = context.dataStore.data.map { prefs ->
        DanmakuFilterConfig(
            enabled = prefs[Keys.DANMAKU_ENABLED] ?: true,
            showScroll = prefs[Keys.DANMAKU_SCROLL] ?: true,
            showTop = prefs[Keys.DANMAKU_TOP] ?: true,
            showBottom = prefs[Keys.DANMAKU_BOTTOM] ?: true,
            showColored = prefs[Keys.DANMAKU_COLORED] ?: true,
            opacity = prefs[Keys.DANMAKU_OPACITY] ?: 0.9f,
            speedMultiplier = prefs[Keys.DANMAKU_SPEED] ?: 1.0f,
            fontSizeScale = prefs[Keys.DANMAKU_SCALE] ?: 1.0f
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun saveSession(cookie: String, userId: String, nickname: String, iconUrl: String) {
        context.dataStore.edit {
            it[Keys.SESSION_COOKIE] = cookie
            it[Keys.USER_ID] = userId
            it[Keys.USER_NICKNAME] = nickname
            it[Keys.USER_ICON] = iconUrl
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(Keys.SESSION_COOKIE)
            it.remove(Keys.USER_ID)
            it.remove(Keys.USER_NICKNAME)
            it.remove(Keys.USER_ICON)
        }
    }

    suspend fun updateDanmakuConfig(config: DanmakuFilterConfig) {
        context.dataStore.edit {
            it[Keys.DANMAKU_ENABLED] = config.enabled
            it[Keys.DANMAKU_SCROLL] = config.showScroll
            it[Keys.DANMAKU_TOP] = config.showTop
            it[Keys.DANMAKU_BOTTOM] = config.showBottom
            it[Keys.DANMAKU_COLORED] = config.showColored
            it[Keys.DANMAKU_OPACITY] = config.opacity
            it[Keys.DANMAKU_SPEED] = config.speedMultiplier
            it[Keys.DANMAKU_SCALE] = config.fontSizeScale
        }
    }
}
