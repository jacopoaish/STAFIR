package com.stafir.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Impostazioni dell'utente, osservabili dall'interfaccia. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    data class Values(
        val onboardingDone: Boolean,
        val recognizeScenes: Boolean,
        val recognizePlaces: Boolean,
        val recognizeVideos: Boolean,
        val onlyWhileCharging: Boolean,
        val minConfidence: Float,
        val gridColumns: Int,
        val themeMode: Int, // 0 = sistema, 1 = chiaro, 2 = scuro
        val autostartConfirmed: Boolean,
    )

    private val _values = MutableStateFlow(read())
    val values: StateFlow<Values> = _values.asStateFlow()
    val current: Values get() = _values.value

    private fun read() = Values(
        onboardingDone = prefs.getBoolean("onboardingDone", false),
        recognizeScenes = prefs.getBoolean("recognizeScenes", true),
        recognizePlaces = prefs.getBoolean("recognizePlaces", true),
        recognizeVideos = prefs.getBoolean("recognizeVideos", true),
        onlyWhileCharging = prefs.getBoolean("onlyWhileCharging", true),
        minConfidence = prefs.getFloat("minConfidence", 0.6f),
        gridColumns = prefs.getInt("gridColumns", 4),
        themeMode = prefs.getInt("themeMode", 0),
        autostartConfirmed = prefs.getBoolean("autostartConfirmed", false),
    )

    fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _values.value = read()
    }

    fun setOnboardingDone(v: Boolean) = update { putBoolean("onboardingDone", v) }
    fun setRecognizeScenes(v: Boolean) = update { putBoolean("recognizeScenes", v) }
    fun setRecognizePlaces(v: Boolean) = update { putBoolean("recognizePlaces", v) }
    fun setRecognizeVideos(v: Boolean) = update { putBoolean("recognizeVideos", v) }
    fun setOnlyWhileCharging(v: Boolean) = update { putBoolean("onlyWhileCharging", v) }
    fun setMinConfidence(v: Float) = update { putFloat("minConfidence", v) }
    fun setGridColumns(v: Int) = update { putInt("gridColumns", v.coerceIn(2, 7)) }
    fun setThemeMode(v: Int) = update { putInt("themeMode", v) }
    fun setAutostartConfirmed(v: Boolean) = update { putBoolean("autostartConfirmed", v) }

    // --- Stato dell'analisi (per accorgersi se il sistema l'ha interrotta) ---
    var analysisHeartbeat: Long
        get() = prefs.getLong("heartbeat", 0)
        set(v) { prefs.edit().putLong("heartbeat", v).apply() }
    var analysisRunning: Boolean
        get() = prefs.getBoolean("analysisRunning", false)
        set(v) { prefs.edit().putBoolean("analysisRunning", v).apply() }
    /** Velocità media osservata (file al secondo), per stimare il tempo rimanente. */
    var analysisRate: Float
        get() = prefs.getFloat("analysisRate", 0f)
        set(v) { prefs.edit().putFloat("analysisRate", v).apply() }
}
