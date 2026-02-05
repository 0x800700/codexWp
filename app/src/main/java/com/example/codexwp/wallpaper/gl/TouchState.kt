package com.example.codexwp.wallpaper.gl

data class TouchState(
    var isDown: Boolean = false,
    var xNorm: Float = 0f,       // 0..1
    var yNorm: Float = 0f,       // 0..1
    var dxNorm: Float = 0f,
    var dyNorm: Float = 0f,
    var strength: Float = 0f,    // decays over time
    var lastEventNanos: Long = 0L
)
