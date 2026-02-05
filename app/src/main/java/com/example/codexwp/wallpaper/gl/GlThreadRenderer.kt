package com.example.codexwp.wallpaper.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30
import android.view.MotionEvent
import android.view.Surface
import kotlin.math.max
import kotlin.math.min

class GlThreadRenderer(
    private val ctx: Context,
    private val surface: Surface,
    initialSettings: SettingsState
) : Thread("AmoledWaveGL") {

    private val lock = Any()

    @Volatile private var running = true
    @Volatile private var visible = true

    @Volatile private var width = 1
    @Volatile private var height = 1

    @Volatile private var homeXOffset = 0f
    @Volatile private var homeYOffset = 0f
    @Volatile private var homeXPixel = 0
    @Volatile private var homeYPixel = 0

    private val touch = TouchState()
    @Volatile private var settings = initialSettings

    private lateinit var eglCore: EglCore
    private lateinit var windowSurface: WindowSurface
    private lateinit var program: GlProgram
    private lateinit var quad: FullscreenQuad
    private val timekeeper = Timekeeper()

    fun startRenderer(): Unit = start()

    fun shutdown() {
        running = false
        interrupt()
    }

    fun setVisible(v: Boolean) { visible = v }

    fun onSurfaceSize(w: Int, h: Int) {
        width = max(1, w)
        height = max(1, h)
    }

    fun onSurfaceDestroyed() {
        running = false
        interrupt()
    }

    fun setHomeOffset(x: Float, y: Float, xPix: Int, yPix: Int) {
        homeXOffset = x
        homeYOffset = y
        homeXPixel = xPix
        homeYPixel = yPix
    }

    fun updateSettings(s: SettingsState) { settings = s }

    fun onTouchEvent(ev: MotionEvent) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        val x = (ev.x / w).coerceIn(0f, 1f)
        val y = (ev.y / h).coerceIn(0f, 1f)
        val now = System.nanoTime()
        synchronized(lock) {
            val prevX = touch.xNorm
            val prevY = touch.yNorm
            touch.xNorm = x
            touch.yNorm = y
            touch.dxNorm = x - prevX
            touch.dyNorm = y - prevY
            touch.lastEventNanos = now
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touch.isDown = true
                    touch.strength = 1.0f
                }
                MotionEvent.ACTION_MOVE -> {
                    touch.isDown = true
                    touch.strength = min(1.0f, touch.strength + 0.06f)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    touch.isDown = false
                }
            }
        }
    }

    override fun run() {
        try {
            eglCore = EglCore().apply { init() }
            windowSurface = WindowSurface(eglCore, surface)
            windowSurface.create()
            windowSurface.makeCurrent()

            program = if (eglCore.isGles3) {
                GlProgram(VERTEX_GLSL_300, FRAGMENT_GLSL_300)
            } else {
                GlProgram(VERTEX_GLSL_100, FRAGMENT_GLSL_100)
            }
            quad = FullscreenQuad()

            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_CULL_FACE)

            var lastFrameNanos = System.nanoTime()

            while (running) {
                if (!visible) {
                    try { sleep(250) } catch (_: InterruptedException) {}
                    continue
                }

                val now = System.nanoTime()
                val dt = timekeeper.step(now)
                val time = timekeeper.timeSec

                renderFrame(dt, time)

                val s = settings
                val targetFrameNs = when (s.fpsMode) {
                    SettingsState.FPS_60 -> 16_666_667L
                    SettingsState.FPS_BATTERY -> 33_333_333L
                    else -> 0L
                }
                if (targetFrameNs > 0L) {
                    val frameNs = System.nanoTime() - lastFrameNanos
                    val sleepNs = targetFrameNs - frameNs
                    if (sleepNs > 0L) {
                        try { sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt()) } catch (_: InterruptedException) {}
                    }
                } else {
                    val frameNs = System.nanoTime() - lastFrameNanos
                    if (frameNs < 12_000_000L) {
                        try { sleep(0, 500_000) } catch (_: InterruptedException) {}
                    }
                }
                lastFrameNanos = System.nanoTime()
            }
        } finally {
            if (this::quad.isInitialized) quad.release()
            if (this::program.isInitialized) program.release()
            if (this::windowSurface.isInitialized) windowSurface.release()
            if (this::eglCore.isInitialized) eglCore.release()
        }
    }

    private fun renderFrame(dtSec: Float, timeSec: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        GLES20.glViewport(0, 0, w.toInt(), h.toInt())
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val s = settings
        val intensity = if (s.fpsMode == SettingsState.FPS_BATTERY) s.intensity * 0.85f else s.intensity

        var touchX = 0f
        var touchY = 0f
        var touchDx = 0f
        var touchDy = 0f
        var touchStrength = 0f
        synchronized(lock) {
            if (s.motionMode == SettingsState.MOTION_SWIPE_TOUCH) {
                touchX = touch.xNorm
                touchY = touch.yNorm
                touchDx = touch.dxNorm
                touchDy = touch.dyNorm
                touchStrength = touch.strength
                touch.strength = max(0f, touch.strength - dtSec * 1.0f)
            } else {
                touch.strength = max(0f, touch.strength - dtSec * 1.0f)
            }
        }

        program.use()
        program.setResolution(w, h)
        program.setTime(timeSec)
        program.setDt(dtSec)
        program.setHomeX(homeXOffset)
        program.setTouch(touchX, touchY, touchDx, touchDy, touchStrength)
        program.setIntensity(intensity)
        program.setSpeed(s.speed)
        program.setColorMode(s.colorMode)
        quad.draw()
        windowSurface.swapBuffers()
    }

    companion object {
        private val VERTEX_GLSL_300 = """
#version 300 es
layout(location=0) in vec2 a_pos;
layout(location=1) in vec2 a_uv;
out vec2 v_uv;
void main() {
  v_uv = a_uv;
  gl_Position = vec4(a_pos, 0.0, 1.0);
}
""".trimIndent()

        private val FRAGMENT_GLSL_300 = """
#version 300 es
precision highp float;

in vec2 v_uv;
out vec4 outColor;

uniform vec2  u_resolution;
uniform float u_time;
uniform float u_dt;

uniform float u_homeX;
uniform vec2  u_touchPos;
uniform vec2  u_touchDelta;
uniform float u_touchStrength;

uniform float u_intensity;
uniform float u_speed;
uniform int   u_colorMode;

float hash21(vec2 p){
  p = fract(p*vec2(123.34, 456.21));
  p += dot(p, p+45.32);
  return fract(p.x*p.y);
}

float noise(vec2 p){
  vec2 i = floor(p);
  vec2 f = fract(p);
  float a = hash21(i);
  float b = hash21(i+vec2(1.0,0.0));
  float c = hash21(i+vec2(0.0,1.0));
  float d = hash21(i+vec2(1.0,1.0));
  vec2 u = f*f*(3.0-2.0*f);
  return mix(a,b,u.x) + (c-a)*u.y*(1.0-u.x) + (d-b)*u.x*u.y;
}

vec3 palette(float t, int mode){
  if(mode==1){
    return 0.6 + 0.4*cos(6.28318*(t+vec3(0.0,0.1,0.2)));
  } else if(mode==2){
    return vec3(0.2,0.4,1.0)*0.6 + 0.4*cos(6.28318*(t+vec3(0.2,0.4,0.6)));
  } else {
    return 0.5 + 0.5*cos(6.28318*(t+vec3(0.0,0.33,0.67)));
  }
}

void main() {
  vec2 uv = v_uv;
  vec2 p = (uv - 0.5);
  p.x *= u_resolution.x / u_resolution.y;

  float ox = (u_homeX - 0.5) * 1.2;
  float t = u_time * mix(0.4, 1.6, u_speed);

  float n1 = noise(p*2.0 + vec2(t*0.25, -t*0.18));
  float n2 = noise(p*3.5 + vec2(-t*0.12, t*0.22));
  vec2 warp = vec2(n1 - 0.5, n2 - 0.5);

  vec2 tp = (u_touchPos - 0.5);
  tp.x *= u_resolution.x / u_resolution.y;
  vec2 d = p - tp;
  float dist = length(d) + 1e-4;
  float touchFalloff = exp(-dist*5.0);
  vec2 touchWarp = (vec2(-d.y, d.x) / dist) * (u_touchStrength * touchFalloff) * 0.08;

  p += (warp * 0.25 + touchWarp) * mix(0.35, 1.1, u_intensity);
  p.x += ox;

  float x = p.x;

  // multi-layer feathered streaks
  float y0a = 0.23*sin(1.6*x + t*0.85) + 0.10*sin(3.2*x - t*0.55);
  float y0b = 0.18*sin(1.1*x - t*0.65) + 0.08*sin(2.6*x + t*0.35);
  float y0c = 0.12*sin(2.2*x + t*0.45) + 0.06*sin(4.1*x - t*0.25);

  float yA = p.y - y0a;
  float yB = p.y - y0b;
  float yC = p.y - y0c;

  float thA = 0.045 + 0.02*sin(t + x*1.6);
  float thB = 0.035 + 0.015*sin(t*1.2 + x*2.1);
  float thC = 0.028 + 0.012*sin(t*1.4 + x*2.6);

  float coreA = exp(-(yA*yA)/(thA*thA));
  float coreB = exp(-(yB*yB)/(thB*thB));
  float coreC = exp(-(yC*yC)/(thC*thC));

  float glowA = exp(-(yA*yA)/(thA*thA*9.0));
  float glowB = exp(-(yB*yB)/(thB*thB*8.0));
  float glowC = exp(-(yC*yC)/(thC*thC*7.0));

  // longer tail: soften with directional mask
  float tail = smoothstep(-0.9, 0.6, p.x);
  float vign = smoothstep(1.25, 0.25, length(p-vec2(0.45,-0.2)));

  float ct = fract(0.22*x + 0.07*t);
  vec3 col = palette(ct, u_colorMode);

  float aCore = coreA*1.1 + coreB*0.8 + coreC*0.6;
  float aGlow = glowA*0.8 + glowB*0.6 + glowC*0.45;

  vec3 rgb = col * (aCore + aGlow) * vign * tail;
  outColor = vec4(rgb, 1.0);
}
""".trimIndent()

        private val VERTEX_GLSL_100 = """
precision mediump float;
attribute vec2 a_pos;
attribute vec2 a_uv;
varying vec2 v_uv;
void main() {
  v_uv = a_uv;
  gl_Position = vec4(a_pos, 0.0, 1.0);
}
""".trimIndent()

        private val FRAGMENT_GLSL_100 = """
precision highp float;

varying vec2 v_uv;

uniform vec2  u_resolution;
uniform float u_time;
uniform float u_dt;

uniform float u_homeX;
uniform vec2  u_touchPos;
uniform vec2  u_touchDelta;
uniform float u_touchStrength;

uniform float u_intensity;
uniform float u_speed;
uniform int   u_colorMode;

float hash21(vec2 p){
  p = fract(p*vec2(123.34, 456.21));
  p += dot(p, p+45.32);
  return fract(p.x*p.y);
}

float noise(vec2 p){
  vec2 i = floor(p);
  vec2 f = fract(p);
  float a = hash21(i);
  float b = hash21(i+vec2(1.0,0.0));
  float c = hash21(i+vec2(0.0,1.0));
  float d = hash21(i+vec2(1.0,1.0));
  vec2 u = f*f*(3.0-2.0*f);
  return mix(a,b,u.x) + (c-a)*u.y*(1.0-u.x) + (d-b)*u.x*u.y;
}

vec3 palette(float t, int mode){
  if(mode==1){
    return 0.6 + 0.4*cos(6.28318*(t+vec3(0.0,0.1,0.2)));
  } else if(mode==2){
    return vec3(0.2,0.4,1.0)*0.6 + 0.4*cos(6.28318*(t+vec3(0.2,0.4,0.6)));
  } else {
    return 0.5 + 0.5*cos(6.28318*(t+vec3(0.0,0.33,0.67)));
  }
}

void main() {
  vec2 uv = v_uv;
  vec2 p = (uv - 0.5);
  p.x *= u_resolution.x / u_resolution.y;

  float ox = (u_homeX - 0.5) * 1.2;
  float t = u_time * mix(0.4, 1.6, u_speed);

  float n1 = noise(p*2.0 + vec2(t*0.25, -t*0.18));
  float n2 = noise(p*3.5 + vec2(-t*0.12, t*0.22));
  vec2 warp = vec2(n1 - 0.5, n2 - 0.5);

  vec2 tp = (u_touchPos - 0.5);
  tp.x *= u_resolution.x / u_resolution.y;
  vec2 d = p - tp;
  float dist = length(d) + 1e-4;
  float touchFalloff = exp(-dist*5.0);
  vec2 touchWarp = (vec2(-d.y, d.x) / dist) * (u_touchStrength * touchFalloff) * 0.08;

  p += (warp * 0.25 + touchWarp) * mix(0.35, 1.1, u_intensity);
  p.x += ox;

  float x = p.x;

  float y0a = 0.23*sin(1.6*x + t*0.85) + 0.10*sin(3.2*x - t*0.55);
  float y0b = 0.18*sin(1.1*x - t*0.65) + 0.08*sin(2.6*x + t*0.35);
  float y0c = 0.12*sin(2.2*x + t*0.45) + 0.06*sin(4.1*x - t*0.25);

  float yA = p.y - y0a;
  float yB = p.y - y0b;
  float yC = p.y - y0c;

  float thA = 0.045 + 0.02*sin(t + x*1.6);
  float thB = 0.035 + 0.015*sin(t*1.2 + x*2.1);
  float thC = 0.028 + 0.012*sin(t*1.4 + x*2.6);

  float coreA = exp(-(yA*yA)/(thA*thA));
  float coreB = exp(-(yB*yB)/(thB*thB));
  float coreC = exp(-(yC*yC)/(thC*thC));

  float glowA = exp(-(yA*yA)/(thA*thA*9.0));
  float glowB = exp(-(yB*yB)/(thB*thB*8.0));
  float glowC = exp(-(yC*yC)/(thC*thC*7.0));

  float tail = smoothstep(-0.9, 0.6, p.x);
  float vign = smoothstep(1.25, 0.25, length(p-vec2(0.45,-0.2)));

  float ct = fract(0.22*x + 0.07*t);
  vec3 col = palette(ct, u_colorMode);

  float aCore = coreA*1.1 + coreB*0.8 + coreC*0.6;
  float aGlow = glowA*0.8 + glowB*0.6 + glowC*0.45;

  vec3 rgb = col * (aCore + aGlow) * vign * tail;
  gl_FragColor = vec4(rgb, 1.0);
}
""".trimIndent()
    }
}
