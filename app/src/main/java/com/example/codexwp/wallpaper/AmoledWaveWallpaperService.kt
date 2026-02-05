package com.example.codexwp.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.example.codexwp.util.Prefs
import com.example.codexwp.wallpaper.gl.GlThreadRenderer

class AmoledWaveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = AmoledWaveEngine(this)

    inner class AmoledWaveEngine(private val ctx: Context) : Engine(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        private var renderer: GlThreadRenderer? = null
        private val prefs = Prefs(ctx)

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(true)
            prefs.registerListener(this)
        }

        override fun onDestroy() {
            prefs.unregisterListener(this)
            renderer?.shutdown()
            renderer = null
            super.onDestroy()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            renderer = GlThreadRenderer(ctx, holder.surface, prefs.readSettingsState())
            renderer?.startRenderer()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer?.onSurfaceSize(width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            renderer?.onSurfaceDestroyed()
            renderer?.shutdown()
            renderer = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            renderer?.setVisible(visible)
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            renderer?.setHomeOffset(xOffset, yOffset, xPixelOffset, yPixelOffset)
        }

        override fun onTouchEvent(event: MotionEvent) {
            renderer?.onTouchEvent(event)
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            renderer?.updateSettings(prefs.readSettingsState())
        }
    }
}
