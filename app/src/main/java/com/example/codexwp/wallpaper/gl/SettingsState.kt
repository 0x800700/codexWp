package com.example.codexwp.wallpaper.gl

data class SettingsState(
    val intensity: Float,      // 0..1
    val speed: Float,          // 0..1
    val colorMode: Int,        // enum int
    val motionMode: Int,       // enum int
    val fpsMode: Int           // enum int
) {
    companion object {
        const val COLOR_RAINBOW = 0
        const val COLOR_WARM = 1
        const val COLOR_COOL = 2

        const val MOTION_SWIPE_ONLY = 0
        const val MOTION_SWIPE_TOUCH = 1

        const val FPS_AUTO = 0
        const val FPS_60 = 1
        const val FPS_BATTERY = 2
    }
}
