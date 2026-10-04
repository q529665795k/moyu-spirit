package com.marvis.desktop_spirit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.util.Locale
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * 桌面灵宠 · 悬浮窗服务(摸鱼基地原创实现)
 * 前台服务 + WindowManager 悬浮窗:
 *   - 蛋/精灵 Canvas 占位绘制(豆包素材到位后替换为序列帧)
 *   - 可拖动 / 点击冒泡 / 呼吸动画 / 睡觉 ZZZ / 爱心
 *   - 系统 TTS 语音吐槽 + 振动反馈
 */
class PetOverlayService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "spirit_overlay_channel"
        const val NOTIFICATION_ID = 2001
        @Volatile var instance: PetOverlayService? = null

        // Flutter 下发的当前状态
        @Volatile var form: String = "egg"          // egg | baby | adult
        @Volatile var behave: String = "idle"       // idle|eating|petting|poking|sleeping|hatch|evolve
        @Volatile var bubble: String? = null
        @Volatile var wantsBubble: Boolean = false
    }

    private lateinit var wm: WindowManager
    private var spiritView: SpiritView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val rnd = Random()

    /** 屏幕密度(dp 缩放), inner View 里直接使用 */
    private val density: Float
        get() = resources.displayMetrics.density

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
    }

    override fun onDestroy() {
        hideOverlay()
        tts?.stop()
        tts?.shutdown()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getBooleanExtra("show", false)?.let { if (it) showOverlay() }
        intent?.getBooleanExtra("hide", false)?.let { if (it) hideOverlay() }
        return START_STICKY
    }

    // ---------- 对外指令(由 MainActivity 转发) ----------
    fun showOverlay() {
        mainHandler.post {
            if (spiritView != null) return@post
            val view = SpiritView(this)
            val params = WindowManager.LayoutParams(
                (260 * resources.displayMetrics.density).toInt(),
                (320 * resources.displayMetrics.density).toInt(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 40
                y = 240
            }
            try {
                wm.addView(view, params)
                spiritView = view
                view.startBreathing()
            } catch (e: Exception) {
                spiritView = null
            }
        }
    }

    fun hideOverlay() {
        mainHandler.post {
            spiritView?.let { v ->
                try { wm.removeView(v) } catch (_: Exception) {}
                spiritView = null
            }
        }
    }

    fun updateState(f: String, b: String, bubbleText: String?) {
        form = f
        behave = b
        if (bubbleText != null) {
            bubble = bubbleText
            wantsBubble = true
        }
        mainHandler.post { spiritView?.invalidate() }
    }

    fun showBubble(text: String) {
        bubble = text
        wantsBubble = true
        mainHandler.post { spiritView?.invalidate() }
        // 气泡 3 秒后消失
        mainHandler.postDelayed({ wantsBubble = false; spiritView?.invalidate() }, 3000)
    }

    fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "spirit")
        }
        // TTS 未就绪时静默, 气泡仍会显示
    }

    fun buzz(ms: Int) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(ms.toLong())
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        tts?.language = Locale.CHINESE
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "灵宠悬浮窗", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle("桌面灵宠")
            .setContentText("灵宠正在桌面陪你摸鱼~")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(pi)
            .build()
    }

    // ---------- 悬浮视图 ----------
    inner class SpiritView(context: Context) : View(context) {
        private val breathePhase = floatArrayOf(0f)
        private val animator = object : Runnable {
            override fun run() {
                breathePhase[0] = (breathePhase[0] + 0.02f) % 1f
                invalidate()
                mainHandler.postDelayed(this, 40)
            }
        }

        fun startBreathing() { mainHandler.post(animator) }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            mainHandler.removeCallbacks(animator)
        }

        private var dragStartX = 0f
        private var dragStartY = 0f
        private var originX = 0
        private var originY = 0
        private var dragging = false

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    val lp = layoutParams as WindowManager.LayoutParams
                    originX = lp.x
                    originY = lp.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return true
                    val dx = (event.rawX - dragStartX).toInt()
                    val dy = (event.rawY - dragStartY).toInt()
                    val lp = layoutParams as WindowManager.LayoutParams
                    lp.x = originX + dx
                    lp.y = originY + dy
                    try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    // 移动距离小 → 视为点击
                    val moved = kotlin.math.abs(event.rawX - dragStartX) + kotlin.math.abs(event.rawY - dragStartY)
                    if (moved < 40f) onSpiritTap()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun onSpiritTap() {
            // 点击行为由 Flutter 状态机驱动(MainActivity 转发), 这里只做本地气泡兜底
            if (bubble == null) showBubble("咕噜咕噜~")
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val breathe = (sin(breathePhase[0] * 2 * Math.PI).toFloat() + 1f) / 2f

            // 泡泡背景(文字)
            if (wantsBubble && bubble != null) {
                drawBubble(canvas, bubble!!, cx, h * 0.18f)
            }

            when (form) {
                "baby", "adult" -> drawSpirit(canvas, cx, h * 0.58f, breathe, form == "adult")
                else -> drawEgg(canvas, cx, h * 0.58f, breathe)
            }

            // 睡觉 ZZZ
            if (behave == "sleeping") {
                val tp = Paint().apply { color = Color.parseColor("#8E7CC3"); textSize = 30f * density }
                canvas.drawText("Z z z", cx + w * 0.18f, h * 0.30f, tp)
            }
            // 摸头爱心
            if (behave == "petting") {
                drawHeart(canvas, cx + w * 0.28f, h * 0.42f, 34f * density)
            }
            // 饿气泡
            if (behave == "idle" && form != "egg") {
                // 饿由 Flutter 气泡控制, 这里不画
            }
        }

        private fun drawEgg(canvas: Canvas, cx: Float, cy: Float, breathe: Float) {
            val r = 92f * density
            // 光晕
            val glow = Paint().apply {
                shader = android.graphics.RadialGradient(
                    cx, cy, r * 1.5f,
                    intArrayOf(Color.parseColor("#559C8ADF"), Color.TRANSPARENT),
                    floatArrayOf(0f, 1f), android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, r * 1.5f, glow)
            // 蛋体
            val eggRect = RectF(cx - r * 0.62f, cy - r * 0.95f, cx + r * 0.62f, cy + r * 0.85f)
            val egg = Paint().apply {
                shader = android.graphics.LinearGradient(
                    eggRect.left, eggRect.top, eggRect.right, eggRect.bottom,
                    intArrayOf(Color.parseColor("#FFFBFF"), Color.parseColor("#DCD0F8"), Color.parseColor("#9C8ADF")),
                    floatArrayOf(0f, 0.6f, 1f), android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawOval(eggRect, egg)
            // 高光
            val hl = Paint().apply { color = Color.argb(160, 255, 255, 255) }
            canvas.drawOval(RectF(cx - r * 0.42f, cy - r * 0.72f, cx - r * 0.18f, cy - r * 0.28f), hl)
            // 表情
            val eye = Paint().apply { color = Color.parseColor("#5B4B8A") }
            canvas.drawCircle(cx - r * 0.22f, cy - r * 0.12f, r * 0.045f, eye)
            canvas.drawCircle(cx + r * 0.22f, cy - r * 0.12f, r * 0.045f, eye)
            val mouth = Paint().apply {
                color = Color.parseColor("#5B4B8A"); style = Paint.Style.STROKE
                strokeWidth = r * 0.035f; strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(RectF(cx - r * 0.18f, cy + r * 0.05f, cx + r * 0.18f, cy + r * 0.25f), 20f, 130f, false, mouth)
        }

        private fun drawSpirit(canvas: Canvas, cx: Float, cy: Float, breathe: Float, isAdult: Boolean) {
            val r = 88f * density
            val fur = Color.parseColor("#FFF2F8")
            // 进化光环
            if (isAdult) {
                val ring = Paint().apply {
                    style = Paint.Style.STROKE; strokeWidth = 8f * density
                    color = Color.argb((120 + breathe * 100).toInt(), 255, 213, 79)
                }
                canvas.drawCircle(cx, cy - r * 0.1f, r * 1.35f, ring)
            }
            // 狐耳
            val earPaint = Paint().apply { color = fur }
            val earL = Path().apply {
                moveTo(cx - r * 0.55f, cy - r * 0.5f)
                lineTo(cx - r * 0.85f, cy - r * 1.15f)
                lineTo(cx - r * 0.2f, cy - r * 0.85f)
                close()
            }
            val earR = Path().apply {
                moveTo(cx + r * 0.55f, cy - r * 0.5f)
                lineTo(cx + r * 0.85f, cy - r * 1.15f)
                lineTo(cx + r * 0.2f, cy - r * 0.85f)
                close()
            }
            canvas.drawPath(earL, earPaint)
            canvas.drawPath(earR, earPaint)
            val earIn = Paint().apply { color = Color.parseColor("#FFB7D0") }
            canvas.drawCircle(cx - r * 0.55f, cy - r * 1.02f, r * 0.12f, earIn)
            canvas.drawCircle(cx + r * 0.55f, cy - r * 1.02f, r * 0.12f, earIn)
            // 脸
            canvas.drawCircle(cx, cy - r * 0.1f, r, Paint().apply { color = fur })
            // 眼(睡=闭眼)
            if (behave == "sleeping") {
                val line = Paint().apply {
                    color = Color.parseColor("#4A3B6B"); style = Paint.Style.STROKE
                    strokeWidth = 4f * density; strokeCap = Paint.Cap.ROUND
                }
                canvas.drawArc(RectF(cx - r * 0.62f, cy - r * 0.2f, cx - r * 0.18f, cy + r * 0.05f), 0f, 180f, false, line)
                canvas.drawArc(RectF(cx + r * 0.18f, cy - r * 0.2f, cx + r * 0.62f, cy + r * 0.05f), 0f, 180f, false, line)
            } else {
                val eye = Paint().apply { color = Color.parseColor("#4A3B6B") }
                canvas.drawCircle(cx - r * 0.4f, cy - r * 0.18f, r * 0.11f, eye)
                canvas.drawCircle(cx + r * 0.4f, cy - r * 0.18f, r * 0.11f, eye)
                val hl = Paint().apply { color = Color.WHITE }
                canvas.drawCircle(cx - r * 0.37f, cy - r * 0.24f, r * 0.035f, hl)
                canvas.drawCircle(cx + r * 0.43f, cy - r * 0.24f, r * 0.035f, hl)
            }
            // 腮红
            val blush = Paint().apply { color = Color.argb(130, 255, 143, 177) }
            canvas.drawCircle(cx - r * 0.62f, cy + r * 0.08f, r * 0.12f, blush)
            canvas.drawCircle(cx + r * 0.62f, cy + r * 0.08f, r * 0.12f, blush)
            // 嘴
            val mouth = Paint().apply {
                color = Color.parseColor("#4A3B6B"); style = Paint.Style.STROKE
                strokeWidth = 4f * density; strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(RectF(cx - r * 0.25f, cy + r * 0.12f, cx + r * 0.25f, cy + r * 0.42f), 20f, 130f, false, mouth)
        }

        private fun drawBubble(canvas: Canvas, text: String, cx: Float, top: Float) {
            val tp = Paint().apply {
                color = Color.WHITE; textSize = 16f * density
                isAntiAlias = true
            }
            val wText = tp.measureText(text)
            val pad = 22f * density
            val bw = wText + pad * 2
            val bh = 40f * density
            val rect = RectF(cx - bw / 2, top, cx + bw / 2, top + bh)
            val bg = Paint().apply {
                color = Color.argb(235, 255, 255, 255)
                setShadowLayer(8f, 0f, 2f, Color.argb(70, 0, 0, 0))
            }
            canvas.drawRoundRect(rect, 18f * density, 18f * density, bg)
            canvas.drawText(text, rect.left + pad, rect.top + bh * 0.62f, tp)
            // 小三角
            val tri = Path().apply {
                moveTo(cx - 8f * density, rect.bottom)
                lineTo(cx + 8f * density, rect.bottom)
                lineTo(cx, rect.bottom + 10f * density)
                close()
            }
            canvas.drawPath(tri, Paint().apply { color = Color.argb(235, 255, 255, 255) })
        }

        private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, s: Float) {
            val p = Paint().apply { color = Color.argb(200, 255, 92, 138) }
            val path = Path().apply {
                moveTo(cx, cy + s * 0.6f)
                cubicTo(cx - s, cy - s * 0.2f, cx - s * 0.5f, cy - s, cx, cy - s * 0.1f)
                cubicTo(cx + s * 0.5f, cy - s, cx + s, cy - s * 0.2f, cx, cy + s * 0.6f)
                close()
            }
            canvas.drawPath(path, p)
        }
    }
}
