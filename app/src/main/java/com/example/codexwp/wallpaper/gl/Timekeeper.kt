package com.example.codexwp.wallpaper.gl

class Timekeeper {
    private var lastNanos: Long = 0L
    var timeSec: Float = 0f
        private set
    private val wrapSec = 120f

    fun step(nowNanos: Long): Float {
        if (lastNanos == 0L) lastNanos = nowNanos
        val dt = ((nowNanos - lastNanos).coerceAtMost(50_000_000L)).toFloat() / 1e9f
        lastNanos = nowNanos
        timeSec += dt
        if (timeSec > wrapSec) {
            timeSec -= wrapSec
        }
        return dt
    }
}
