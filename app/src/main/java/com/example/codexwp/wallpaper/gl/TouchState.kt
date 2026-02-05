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
    var centerX: Float = 0.5f,
    var centerY: Float = 0.5f,
    var lastAngle: Float = 0f,
    var swirlAccum: Float = 0f,
    var tunnelTime: Float = 0f,
    var pinchStartDist2: Float = 0f,
    var pinchStartTime: Long = 0L,
    var pinchTriggered: Boolean = false,
    var tunnelMode: Int = 0,
    var tunnelPhase: Float = 0f,
    var lastEventNanos: Long = 0L
)
