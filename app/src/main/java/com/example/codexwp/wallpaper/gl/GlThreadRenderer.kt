package com.example.codexwp.wallpaper.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30
import android.view.MotionEvent
import android.view.Surface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.atan2
import kotlin.math.PI

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
    private var visualTimeSec = 0.0

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
        val now = System.nanoTime()
        synchronized(lock) {
            touch.lastEventNanos = now
            val count = ev.pointerCount
            if (count > 0) {
                val x0 = (ev.getX(0) / w).coerceIn(0f, 1f)
                val y0 = (ev.getY(0) / h).coerceIn(0f, 1f)
                touch.x0 = x0
                touch.y0 = y0
                touch.isDown0 = true
            } else {
                touch.isDown0 = false
            }
            if (count > 1) {
                val x1 = (ev.getX(1) / w).coerceIn(0f, 1f)
                val y1 = (ev.getY(1) / h).coerceIn(0f, 1f)
                touch.x1 = x1
                touch.y1 = y1
                touch.isDown1 = true
                touch.centerX = (touch.x0 + touch.x1) * 0.5f
                touch.centerY = (touch.y0 + touch.y1) * 0.5f
            } else {
                touch.isDown1 = false
            }

            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (count > 0) touch.strength0 = 1.0f
                    if (count > 1) touch.strength1 = 1.0f
                    if (count > 0) {
                        touch.centerX = touch.x0
                        touch.centerY = touch.y0
                        touch.swirlAccum = 0f
                        touch.lastAngle = 0f
                    }
                    if (count > 1) {
                        val dx = touch.x0 - touch.x1
                        val dy = touch.y0 - touch.y1
                        touch.pinchStartDist2 = dx * dx + dy * dy
                        touch.pinchStartTime = now
                        touch.pinchTriggered = false
                        touch.centerX = (touch.x0 + touch.x1) * 0.5f
                        touch.centerY = (touch.y0 + touch.y1) * 0.5f
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (count > 0) touch.strength0 = min(1.0f, touch.strength0 + 0.06f)
                    if (count > 1) touch.strength1 = min(1.0f, touch.strength1 + 0.06f)
                    if (count > 0) {
                        val cx = touch.centerX
                        val cy = touch.centerY
                        val dx = (touch.x0 - cx).toDouble()
                        val dy = (touch.y0 - cy).toDouble()
                        if (dx * dx + dy * dy > 0.0004) {
                            val angle = atan2(dy, dx).toFloat()
                            if (touch.lastAngle != 0f) {
                                var delta = angle - touch.lastAngle
                                if (delta > PI) delta -= (2.0 * PI).toFloat()
                                if (delta < -PI) delta += (2.0 * PI).toFloat()
                                touch.swirlAccum += kotlin.math.abs(delta)
                                if (touch.swirlAccum > (2.0 * PI * 0.85).toFloat()) {
                                    touch.tunnelTime = 15.0f
                                    touch.swirlAccum = 0f
                                }
                            }
                            touch.lastAngle = angle
                        }
                    }
                    if (count > 1 && !touch.pinchTriggered) {
                        touch.centerX = (touch.x0 + touch.x1) * 0.5f
                        touch.centerY = (touch.y0 + touch.y1) * 0.5f
                        val dx = touch.x0 - touch.x1
                        val dy = touch.y0 - touch.y1
                        val dist2 = dx * dx + dy * dy
                        val grow = dist2 / (touch.pinchStartDist2 + 1e-6f)
                        val elapsedMs = (now - touch.pinchStartTime) / 1_000_000L
                        if (elapsedMs < 700 && grow > 1.35f * 1.35f) {
                            touch.tunnelTime = 7.0f
                            touch.pinchTriggered = true
                        }
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (count <= 1) touch.isDown1 = false
                    if (count == 0) touch.isDown0 = false
                    if (count < 2) {
                        touch.pinchStartDist2 = 0f
                        touch.pinchTriggered = false
                    }
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
            val slowPhase = 0.5 + 0.5 * kotlin.math.sin(timekeeper.timeSecD * 0.25)
            val slowAmount = smoothstep(0.74, 0.94, slowPhase)
            val slowFactor = lerp(1.0, 0.45, slowAmount)
            visualTimeSec += dt * slowFactor

            renderFrame(dt, visualTimeSec.toFloat())

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

        var touchX0 = 0f
        var touchY0 = 0f
        var touchS0 = 0f
        var touchX1 = 0f
        var touchY1 = 0f
        var touchS1 = 0f
        var tunnelX = 0.5f
        var tunnelY = 0.5f
        var tunnelStrength = 0f
        synchronized(lock) {
            if (s.motionMode == SettingsState.MOTION_SWIPE_TOUCH) {
                touchX0 = touch.x0
                touchY0 = touch.y0
                touchS0 = touch.strength0
                touchX1 = touch.x1
                touchY1 = touch.y1
                touchS1 = touch.strength1
                touch.strength0 = max(0f, touch.strength0 - dtSec * 1.0f)
                touch.strength1 = max(0f, touch.strength1 - dtSec * 1.0f)
                if (touch.tunnelTime > 0f) {
                    touch.tunnelTime = max(0f, touch.tunnelTime - dtSec)
                }
                tunnelX = touch.centerX
                tunnelY = touch.centerY
                val tLeft = touch.tunnelTime
                if (tLeft > 0f) {
                    val fadeIn = (1f - (tLeft / 15.0f)).coerceIn(0f, 1f)
                    val fadeOut = (tLeft / 15.0f).coerceIn(0f, 1f)
                    tunnelStrength = min(fadeIn * 5f, fadeOut * 5f)
                }
            } else {
                touch.strength0 = max(0f, touch.strength0 - dtSec * 1.0f)
                touch.strength1 = max(0f, touch.strength1 - dtSec * 1.0f)
            }
        }

        program.use()
        program.setResolution(w, h)
        program.setTime(timeSec)
        program.setDt(dtSec)
        program.setHomeX(homeXOffset)
        program.setTouch(touchX0, touchY0, touchS0, touchX1, touchY1, touchS1)
        program.setTunnel(tunnelX, tunnelY, tunnelStrength)
        program.setIntensity(intensity)
        program.setSpeed(s.speed)
        program.setColorMode(s.colorMode)
        quad.draw()
        windowSurface.swapBuffers()
    }

    companion object {
        private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
            return t * t * (3.0 - 2.0 * t)
        }

        private fun lerp(a: Double, b: Double, t: Double): Double {
            return a + (b - a) * t
        }

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
uniform vec2  u_touchPos0;
uniform vec2  u_touchPos1;
uniform float u_touchStrength0;
uniform float u_touchStrength1;
uniform vec2  u_tunnelPos;
uniform float u_tunnelStrength;

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

  vec2 tp0 = (u_touchPos0 - 0.5);
  tp0.x *= u_resolution.x / u_resolution.y;
  vec2 d0 = p - tp0;
  float dist0 = length(d0) + 1e-4;
  float fall0 = exp(-dist0*5.0);
  vec2 touchWarp0 = (vec2(-d0.y, d0.x) / dist0) * (u_touchStrength0 * fall0) * 0.07;

  vec2 tp1 = (u_touchPos1 - 0.5);
  tp1.x *= u_resolution.x / u_resolution.y;
  vec2 d1 = p - tp1;
  float dist1 = length(d1) + 1e-4;
  float fall1 = exp(-dist1*5.0);
  vec2 touchWarp1 = (vec2(-d1.y, d1.x) / dist1) * (u_touchStrength1 * fall1) * 0.07;

  vec2 tc = (u_tunnelPos - 0.5);
  tc.x *= u_resolution.x / u_resolution.y;
  vec2 dp = p - tc;
  float rr = length(dp) + 1e-4;
  float ang = atan(dp.y, dp.x);
  float pull = u_tunnelStrength * exp(-rr * 1.2);
  float twist = u_tunnelStrength * exp(-rr * 1.8);
  ang += twist * 0.8 + t * 0.55 * pull;
  rr = rr * (1.0 - pull * 0.55);
  dp = vec2(cos(ang), sin(ang)) * rr;
  p = tc + dp;

  p += (warp * 0.25 + touchWarp0 + touchWarp1) * mix(0.35, 1.1, u_intensity);
  p.x += ox;

  float x = p.x;

  // multi-layer feathered streaks (different speeds per layer)
  float tA = t * 1.00;
  float tB = t * 0.82;
  float tC = t * 1.18;
  float tD = t * 0.66;
  float tE = t * 1.35;

  float y0a = 0.23*sin(1.6*x + tA*0.85) + 0.10*sin(3.2*x - tA*0.55);
  float y0b = 0.18*sin(1.1*x - tB*0.65) + 0.08*sin(2.6*x + tB*0.35);
  float y0c = 0.12*sin(2.2*x + tC*0.45) + 0.06*sin(4.1*x - tC*0.25);
  float y0d = 0.09*sin(2.8*x + tD*0.30) + 0.05*sin(5.0*x + tD*0.15);
  float y0e = 0.07*sin(3.4*x - tE*0.22) + 0.04*sin(6.2*x - tE*0.12);

  float yA = p.y - y0a;
  float yB = p.y - y0b;
  float yC = p.y - y0c;
  float yD = p.y - y0d;
  float yE = p.y - y0e;

  float thA = 0.045 + 0.02*sin(tA + x*1.6);
  float thB = 0.035 + 0.015*sin(tB*1.2 + x*2.1);
  float thC = 0.028 + 0.012*sin(tC*1.4 + x*2.6);
  float thD = 0.022 + 0.010*sin(tD*1.6 + x*3.1);
  float thE = 0.018 + 0.008*sin(tE*1.8 + x*3.6);

  float coreA = exp(-(yA*yA)/(thA*thA));
  float coreB = exp(-(yB*yB)/(thB*thB));
  float coreC = exp(-(yC*yC)/(thC*thC));
  float coreD = exp(-(yD*yD)/(thD*thD));
  float coreE = exp(-(yE*yE)/(thE*thE));

  float glowA = exp(-(yA*yA)/(thA*thA*9.0));
  float glowB = exp(-(yB*yB)/(thB*thB*8.0));
  float glowC = exp(-(yC*yC)/(thC*thC*7.0));
  float glowD = exp(-(yD*yD)/(thD*thD*6.0));
  float glowE = exp(-(yE*yE)/(thE*thE*6.0));

  // longer tail: soften with directional mask
  float tail = smoothstep(-0.9, 0.6, p.x);
  float vign = smoothstep(1.25, 0.25, length(p-vec2(0.45,-0.2)));

  float ct = fract(0.22*x + 0.07*t);
  vec3 col = palette(ct, u_colorMode);

  float aCore = coreA*1.1 + coreB*0.8 + coreC*0.6 + coreD*0.5 + coreE*0.4;
  float aGlow = glowA*0.8 + glowB*0.6 + glowC*0.45 + glowD*0.35 + glowE*0.28;

  // speckle particles along the streaks
  float speckle = noise(vec2(x*8.0, (yA+yB+yC)*18.0) + t*0.9);
  speckle = pow(max(0.0, speckle - 0.55), 3.0);

  float coreBoost = exp(-rr * 6.0) * u_tunnelStrength * 1.6;
  vec3 rgb = col * (aCore + aGlow + speckle*0.9 + coreBoost) * vign * tail;
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
uniform vec2  u_touchPos0;
uniform vec2  u_touchPos1;
uniform float u_touchStrength0;
uniform float u_touchStrength1;
uniform vec2  u_tunnelPos;
uniform float u_tunnelStrength;

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

  vec2 tp0 = (u_touchPos0 - 0.5);
  tp0.x *= u_resolution.x / u_resolution.y;
  vec2 d0 = p - tp0;
  float dist0 = length(d0) + 1e-4;
  float fall0 = exp(-dist0*5.0);
  vec2 touchWarp0 = (vec2(-d0.y, d0.x) / dist0) * (u_touchStrength0 * fall0) * 0.07;

  vec2 tp1 = (u_touchPos1 - 0.5);
  tp1.x *= u_resolution.x / u_resolution.y;
  vec2 d1 = p - tp1;
  float dist1 = length(d1) + 1e-4;
  float fall1 = exp(-dist1*5.0);
  vec2 touchWarp1 = (vec2(-d1.y, d1.x) / dist1) * (u_touchStrength1 * fall1) * 0.07;

  vec2 tc = (u_tunnelPos - 0.5);
  tc.x *= u_resolution.x / u_resolution.y;
  vec2 dp = p - tc;
  float rr = length(dp) + 1e-4;
  float ang = atan(dp.y, dp.x);
  float pull = u_tunnelStrength * exp(-rr * 1.2);
  float twist = u_tunnelStrength * exp(-rr * 1.8);
  ang += twist * 0.8 + t * 0.55 * pull;
  rr = rr * (1.0 - pull * 0.55);
  dp = vec2(cos(ang), sin(ang)) * rr;
  p = tc + dp;

  p += (warp * 0.25 + touchWarp0 + touchWarp1) * mix(0.35, 1.1, u_intensity);
  p.x += ox;

  float x = p.x;

  float tA = t * 1.00;
  float tB = t * 0.82;
  float tC = t * 1.18;
  float tD = t * 0.66;
  float tE = t * 1.35;

  float y0a = 0.23*sin(1.6*x + tA*0.85) + 0.10*sin(3.2*x - tA*0.55);
  float y0b = 0.18*sin(1.1*x - tB*0.65) + 0.08*sin(2.6*x + tB*0.35);
  float y0c = 0.12*sin(2.2*x + tC*0.45) + 0.06*sin(4.1*x - tC*0.25);
  float y0d = 0.09*sin(2.8*x + tD*0.30) + 0.05*sin(5.0*x + tD*0.15);
  float y0e = 0.07*sin(3.4*x - tE*0.22) + 0.04*sin(6.2*x - tE*0.12);

  float yA = p.y - y0a;
  float yB = p.y - y0b;
  float yC = p.y - y0c;
  float yD = p.y - y0d;
  float yE = p.y - y0e;

  float thA = 0.045 + 0.02*sin(tA + x*1.6);
  float thB = 0.035 + 0.015*sin(tB*1.2 + x*2.1);
  float thC = 0.028 + 0.012*sin(tC*1.4 + x*2.6);
  float thD = 0.022 + 0.010*sin(tD*1.6 + x*3.1);
  float thE = 0.018 + 0.008*sin(tE*1.8 + x*3.6);

  float coreA = exp(-(yA*yA)/(thA*thA));
  float coreB = exp(-(yB*yB)/(thB*thB));
  float coreC = exp(-(yC*yC)/(thC*thC));
  float coreD = exp(-(yD*yD)/(thD*thD));
  float coreE = exp(-(yE*yE)/(thE*thE));

  float glowA = exp(-(yA*yA)/(thA*thA*9.0));
  float glowB = exp(-(yB*yB)/(thB*thB*8.0));
  float glowC = exp(-(yC*yC)/(thC*thC*7.0));
  float glowD = exp(-(yD*yD)/(thD*thD*6.0));
  float glowE = exp(-(yE*yE)/(thE*thE*6.0));

  float tail = smoothstep(-0.9, 0.6, p.x);
  float vign = smoothstep(1.25, 0.25, length(p-vec2(0.45,-0.2)));

  float ct = fract(0.22*x + 0.07*t);
  vec3 col = palette(ct, u_colorMode);

  float aCore = coreA*1.1 + coreB*0.8 + coreC*0.6 + coreD*0.5 + coreE*0.4;
  float aGlow = glowA*0.8 + glowB*0.6 + glowC*0.45 + glowD*0.35 + glowE*0.28;

  float speckle = noise(vec2(x*8.0, (yA+yB+yC)*18.0) + t*0.9);
  speckle = pow(max(0.0, speckle - 0.55), 3.0);

  float coreBoost = exp(-rr * 6.0) * u_tunnelStrength * 1.6;
  vec3 rgb = col * (aCore + aGlow + speckle*0.9 + coreBoost) * vign * tail;
  gl_FragColor = vec4(rgb, 1.0);
}
""".trimIndent()
    }
}
