package com.example.codexwp.wallpaper.gl

import android.opengl.GLES20

class GlProgram(vertexSrc: String, fragmentSrc: String) {
    private val programId: Int
    private val uResolution: Int
    private val uTime: Int
    private val uDt: Int
    private val uHomeX: Int
    private val uTouchPos: Int
    private val uTouchDelta: Int
    private val uTouchStrength: Int
    private val uIntensity: Int
    private val uSpeed: Int
    private val uColorMode: Int

    init {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        programId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programId, vs)
        GLES20.glAttachShader(programId, fs)
        GLES20.glBindAttribLocation(programId, 0, "a_pos")
        GLES20.glBindAttribLocation(programId, 1, "a_uv")
        GLES20.glLinkProgram(programId)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        check(linkStatus[0] == GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(programId)
            GLES20.glDeleteProgram(programId)
            "Link failed: $log"
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        uResolution = GLES20.glGetUniformLocation(programId, "u_resolution")
        uTime = GLES20.glGetUniformLocation(programId, "u_time")
        uDt = GLES20.glGetUniformLocation(programId, "u_dt")
        uHomeX = GLES20.glGetUniformLocation(programId, "u_homeX")
        uTouchPos = GLES20.glGetUniformLocation(programId, "u_touchPos")
        uTouchDelta = GLES20.glGetUniformLocation(programId, "u_touchDelta")
        uTouchStrength = GLES20.glGetUniformLocation(programId, "u_touchStrength")
        uIntensity = GLES20.glGetUniformLocation(programId, "u_intensity")
        uSpeed = GLES20.glGetUniformLocation(programId, "u_speed")
        uColorMode = GLES20.glGetUniformLocation(programId, "u_colorMode")
    }

    fun use() {
        GLES20.glUseProgram(programId)
    }

    fun setResolution(w: Float, h: Float) {
        GLES20.glUniform2f(uResolution, w, h)
    }

    fun setTime(time: Float) {
        GLES20.glUniform1f(uTime, time)
    }

    fun setDt(dt: Float) {
        GLES20.glUniform1f(uDt, dt)
    }

    fun setHomeX(x: Float) {
        GLES20.glUniform1f(uHomeX, x)
    }

    fun setTouch(posX: Float, posY: Float, dx: Float, dy: Float, strength: Float) {
        GLES20.glUniform2f(uTouchPos, posX, posY)
        GLES20.glUniform2f(uTouchDelta, dx, dy)
        GLES20.glUniform1f(uTouchStrength, strength)
    }

    fun setIntensity(v: Float) {
        GLES20.glUniform1f(uIntensity, v)
    }

    fun setSpeed(v: Float) {
        GLES20.glUniform1f(uSpeed, v)
    }

    fun setColorMode(mode: Int) {
        GLES20.glUniform1i(uColorMode, mode)
    }

    fun release() {
        GLES20.glDeleteProgram(programId)
    }

    private fun compileShader(type: Int, src: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, src)
        GLES20.glCompileShader(id)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, compiled, 0)
        check(compiled[0] == GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(id)
            GLES20.glDeleteShader(id)
            "Shader compile failed: $log"
        }
        return id
    }
}
