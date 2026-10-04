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
import android.os.SystemClock
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * 桌面灵宠 · 悬浮窗服务(摸鱼基地原创实现)
 * 前台服务 + WindowManager 悬浮窗:
 *   - 蛋/精灵 Canvas 占位绘制(豆包素材到位后替换为序列帧)
 *   - 单指拖动 / 双指缩放 / 透明度调节 / 点击弹出互动菜单(喂食·摸头·戳·睡觉·吐槽)
 *   - 菜单动作上抛 Flutter 状态机, 反馈(气泡/TTS/振动)全同步
 *   - 呼吸动画 / 睡觉 ZZZ / 爱心
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

    /** 窗口尺寸上下限(dp) */
    private val minWdp = 130
    private val minHdp = 160
    private val maxWdp = 640
    private val maxHdp = 800

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
                alpha = 1f
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

        // ---- 手势状态 ----
        private var dragging = false
        private var pinchUsed = false
        private var dragStartX = 0f
        private var dragStartY = 0f
        private var originX = 0
        private var originY = 0
        private var downTime = 0L
        private var startDist = 0f
        private var startW = 0
        private var startH = 0

        // ---- 菜单状态 ----
        private var menuOpen = false
        private var settingsOpen = false
        private val menuRects = mutableListOf<Pair<RectF, String>>()
        private var pendingAction: String? = null
        private var currentAlpha = 1f

        fun startBreathing() { mainHandler.post(animator) }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            mainHandler.removeCallbacks(animator)
        }

        private fun dist(e: MotionEvent): Float {
            if (e.pointerCount < 2) return 0f
            val dx = e.getX(0) - e.getX(1)
            val dy = e.getY(0) - e.getY(1)
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val lp = layoutParams as WindowManager.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (menuOpen) {
                        val act = hitMenu(event.x, event.y)
                        if (act != null) {
                            pendingAction = act
                        } else {
                            closeMenu()
                        }
                        return true
                    }
                    downTime = SystemClock.uptimeMillis()
                    if (event.pointerCount >= 2) {
                        pinchUsed = true
                        startDist = dist(event)
                        startW = lp.width
                        startH = lp.height
                    } else {
                        dragging = true
                        dragStartX = event.rawX
                        dragStartY = event.rawY
                        originX = lp.x
                        originY = lp.y
                    }
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        pinchUsed = true
                        startDist = dist(event)
                        startW = lp.width
                        startH = lp.height
                        dragging = false
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (menuOpen) return true
                    if (event.pointerCount >= 2 && startDist > 0f) {
                        val scale = dist(event) / startDist
                        val nw = (startW * scale).toInt().coerceIn((minWdp * density).toInt(), (maxWdp * density).toInt())
                        val nh = (startH * scale).toInt().coerceIn((minHdp * density).toInt(), (maxHdp * density).toInt())
                        lp.width = nw
                        lp.height = nh
                        try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
                        return true
                    }
                    if (dragging) {
                        val dx = (event.rawX - dragStartX).toInt()
                        val dy = (event.rawY - dragStartY).toInt()
                        lp.x = originX + dx
                        lp.y = originY + dy
                        try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
                    }
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount == 2) {
                        // 剩一指 → 转回拖动
                        val idx = event.actionIndex
                        val other = if (idx == 0) 1 else 0
                        dragStartX = event.getRawX(other)
                        dragStartY = event.getRawY(other)
                        originX = lp.x
                        originY = lp.y
                        dragging = true
                        startDist = 0f
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (menuOpen) {
                        val act = pendingAction
                        pendingAction = null
                        if (act != null) {
                            performAction(act)
                            closeMenu()
                        }
                        return true
                    }
                    val moved = abs(event.rawX - dragStartX) + abs(event.rawY - dragStartY)
                    val dt = SystemClock.uptimeMillis() - downTime
                    if (!pinchUsed && moved < 40f && dt < 500) {
                        openMenu()
                    }
                    dragging = false
                    pinchUsed = false
                    startDist = 0f
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        // ---------- 互动菜单 ----------
        private fun openMenu() {
            menuOpen = true
            settingsOpen = false
            invalidate()
        }

        private fun closeMenu() {
            menuOpen = false
            settingsOpen = false
            pendingAction = null
            invalidate()
        }

        private fun menuActions(): List<String> =
            if (settingsOpen)
                listOf("变大", "变小", "更透明", "更实心", "返回")
            else
                listOf("喂食", "摸头", "戳肚", "睡觉", "吐槽", "设置")

        private fun actionFor(label: String): String = when (label) {
            "喂食" -> "feed"
            "摸头" -> "pet"
            "戳肚" -> "poke"
            "睡觉" -> "nap"
            "吐槽" -> "taunt"
            "设置" -> "settings"
            "变大" -> "bigger"
            "变小" -> "smaller"
            "更透明" -> "moreTransparent"
            "更实心" -> "moreOpaque"
            "返回" -> "back"
            else -> ""
        }

        private fun hitMenu(x: Float, y: Float): String? {
            for ((rect, act) in menuRects) {
                if (rect.contains(x, y)) return act
            }
            return null
        }

        private fun performAction(action: String) {
            when (action) {
                "feed", "pet", "poke", "nap", "taunt" -> {
                    // 上抛 Flutter 状态机执行(气泡/TTS/振动由状态机处理)
                    try {
                        MainActivity.flutterChannel?.invokeMethod(
                            "overlayAction", mapOf("action" to action)
                        )
                    } catch (_: Exception) {
                        // 桥不可用(如 App 进程被回收)时本地兜底
                        showBubble("主人回来啦~")
                    }
                }
                "settings" -> {
                    settingsOpen = true
                    invalidate()
                }
                "back" -> {
                    settingsOpen = false
                    invalidate()
                }
                "bigger", "smaller" -> {
                    val lp = layoutParams as WindowManager.LayoutParams
                    val ratio = lp.height.toFloat() / lp.width.toFloat()
                    var nw = if (action == "bigger")
                        lp.width + (36 * density).toInt()
                    else
                        lp.width - (36 * density).toInt()
                    nw = nw.coerceIn((minWdp * density).toInt(), (maxWdp * density).toInt())
                    lp.width = nw
                    lp.height = (nw * ratio).toInt().coerceIn((minHdp * density).toInt(), (maxHdp * density).toInt())
                    try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
                    invalidate()
                }
                "moreTransparent", "moreOpaque" -> {
                    val lp = layoutParams as WindowManager.LayoutParams
                    currentAlpha = if (action == "moreTransparent")
                        (currentAlpha - 0.2f).coerceAtLeast(0.25f)
                    else
                        (currentAlpha + 0.2f).coerceAtMost(1f)
                    lp.alpha = currentAlpha
                    try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
                    invalidate()
                }
            }
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

            // 互动菜单
            if (menuOpen) {
                drawMenu(canvas)
            }
        }

        // ---------- 菜单绘制 ----------
        private fun drawMenu(canvas: Canvas) {
            menuRects.clear()
            val w = width.toFloat()
            val h = height.toFloat()
            val items = menuActions()
            val cols = 3
            val rows = ceil(items.size.toFloat() / cols).toInt()
            val margin = 10f * density
            val gap = 8f * density
            val btnH = 38f * density
            val btnW = (w * 0.94f - margin * 2 - gap * (cols - 1)) / cols
            val panelW = w * 0.94f
            val panelH = margin * 2 + rows * btnH + (rows - 1) * gap
            val left = (w - panelW) / 2f
            val top = h * 0.04f

            // 面板背景
            val bg = Paint().apply {
                color = Color.argb(225, 255, 255, 255)
                setShadowLayer(10f, 0f, 3f, Color.argb(80, 0, 0, 0))
            }
            canvas.drawRoundRect(RectF(left, top, left + panelW, top + panelH), 16f * density, 16f * density, bg)

            val btnBg = Paint().apply { color = Color.parseColor("#F3EEFF") }
            val btnAct = Paint().apply { color = Color.parseColor("#9C8ADF") }
            val tp = Paint().apply {
                color = Color.parseColor("#4A3B6B")
                textSize = 14f * density
                isAntiAlias = true
            }
            for (i in items.indices) {
                val r = i / cols
                val c = i % cols
                val x = left + margin + c * (btnW + gap)
                val y = top + margin + r * (btnH + gap)
                val rect = RectF(x, y, x + btnW, y + btnH)
                val isSettings = settingsOpen && i == items.size - 1
                canvas.drawRoundRect(rect, 10f * density, 10f * density, if (isSettings) btnAct else btnBg)
                val label = items[i]
                val tw = tp.measureText(label)
                canvas.drawText(label, rect.centerX() - tw / 2f, rect.centerY() + tp.textSize * 0.36f, tp)
                menuRects.add(rect to actionFor(label))
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
