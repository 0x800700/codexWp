package com.example.codexwp.wallpaper.gl

data class TouchState(
    var isDown0: Boolean = false,
    var x0: Float = 0f,       // 0..1
    var y0: Float = 0f,       // 0..1
    var strength0: Float = 0f,    // decays over time
    var isDown1: Boolean = false,
    var x1: Float = 0f,       // 0..1
    var y1: Float = 0f,       // 0..1
    var strength1: Float = 0f,    // decays over time
    var lastEventNanos: Long = 0L
)
