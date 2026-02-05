package com.example.codexwp.wallpaper.gl

import android.opengl.EGLSurface

class WindowSurface(
    private val eglCore: EglCore,
    private val surface: Any
) {
    private var eglSurface: EGLSurface? = null

    fun create() {
        eglSurface = eglCore.createWindowSurface(surface)
    }

    fun makeCurrent() {
        val s = eglSurface ?: return
        eglCore.makeCurrent(s)
    }

    fun swapBuffers(): Boolean {
        val s = eglSurface ?: return false
        return eglCore.swapBuffers(s)
    }

    fun release() {
        val s = eglSurface
        if (s != null) {
            eglCore.releaseSurface(s)
            eglSurface = null
        }
    }
}
