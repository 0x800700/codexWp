package com.example.codexwp.wallpaper.gl

class Timekeeper {
    private var lastNanos: Long = 0L
    private var timeSecDouble: Double = 0.0
    val timeSecD: Double
        get() = timeSecDouble
    val timeSec: Float
        get() = timeSecDouble.toFloat()

    fun step(nowNanos: Long): Float {
        if (lastNanos == 0L) lastNanos = nowNanos
        val delta = (nowNanos - lastNanos).coerceAtMost(50_000_000L)
        lastNanos = nowNanos
        val dt = delta.toDouble() / 1e9
        timeSecDouble += dt
        return dt.toFloat()
    }
}
