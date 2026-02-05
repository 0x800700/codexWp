package com.example.codexwp.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.example.codexwp.wallpaper.gl.SettingsState

class Prefs(ctx: Context) {
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)

    fun readSettingsState(): SettingsState {
        val intensity = sp.getInt(KEY_INTENSITY, 70).coerceIn(0, 100) / 100f
        val speed = sp.getInt(KEY_SPEED, 60).coerceIn(0, 100) / 100f
        val colorMode = sp.getString(KEY_COLOR_MODE, "0")?.toIntOrNull() ?: 0
        val motionMode = sp.getString(KEY_MOTION_MODE, "1")?.toIntOrNull() ?: 1
        val fpsMode = sp.getString(KEY_FPS_MODE, "0")?.toIntOrNull() ?: 0
        return SettingsState(intensity, speed, colorMode, motionMode, fpsMode)
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val KEY_INTENSITY = "intensity"
        const val KEY_SPEED = "speed"
        const val KEY_COLOR_MODE = "colorMode"
        const val KEY_MOTION_MODE = "motionMode"
        const val KEY_FPS_MODE = "fpsMode"
    }
}
