package com.marvis.desktop_spirit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "moyu/spirit_overlay"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "hasPermission" -> result.success(Settings.canDrawOverlays(this))
                "ensurePermission" -> {
                    if (!Settings.canDrawOverlays(this)) {
                        try {
                            startActivity(Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            ))
                        } catch (_: Exception) {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        }
                    }
                    result.success(Settings.canDrawOverlays(this))
                }
                "show" -> {
                    startService(Intent(this, PetOverlayService::class.java).putExtra("show", true))
                    result.success(true)
                }
                "hide" -> {
                    startService(Intent(this, PetOverlayService::class.java).putExtra("hide", true))
                    result.success(true)
                }
                "update" -> {
                    val f = call.argument<String>("form") ?: "egg"
                    val b = call.argument<String>("behave") ?: "idle"
                    val bubble = call.argument<String>("bubble")
                    PetOverlayService.instance?.updateState(f, b, bubble)
                    if (bubble != null) PetOverlayService.instance?.showBubble(bubble)
                    result.success(null)
                }
                "say" -> {
                    val text = call.argument<String>("text") ?: ""
                    PetOverlayService.instance?.speak(text)
                    result.success(null)
                }
                "vibrate" -> {
                    val ms = call.argument<Int>("ms") ?: 30
                    PetOverlayService.instance?.buzz(ms)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }
}
