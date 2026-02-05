package com.example.codexwp.wallpaper.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class FullscreenQuad {
    private val vboId: Int
    private val vertexCount = 4
    private val strideBytes = 4 * 4

    init {
        val data = floatArrayOf(
            // x, y, u, v
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
        )
        val buffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(data).position(0)
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vboId = ids[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            data.size * 4,
            buffer,
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun draw() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, strideBytes, 0)
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, strideBytes, 8)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(0)
        GLES20.glDisableVertexAttribArray(1)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun release() {
        val ids = intArrayOf(vboId)
        GLES20.glDeleteBuffers(1, ids, 0)
    }
}
