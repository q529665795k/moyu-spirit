import 'package:flutter/services.dart';

/// Flutter ↔ 原生悬浮窗桥接
/// 原生侧: PetOverlayService(MethodCallHandler) + MainActivity 转发
class OverlayBridge {
  static const MethodChannel _channel = MethodChannel('moyu/spirit_overlay');

  /// 悬浮窗菜单动作回调(action: feed|pet|poke|nap|taunt)
  static void Function(String action)? onOverlayAction;

  /// 监听原生悬浮窗菜单上抛的动作
  static void listen(void Function(String action) onAction) {
    onOverlayAction = onAction;
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'overlayAction') {
        final action = (call.arguments as Map?)?['action'] as String? ?? '';
        onAction(action);
      }
    });
  }

  /// 请求悬浮窗权限, 返回是否已授权
  static Future<bool> ensurePermission() async {
    try {
      return await _channel.invokeMethod<bool>('ensurePermission') ?? false;
    } catch (_) {
      return false;
    }
  }

  /// 检查权限
  static Future<bool> hasPermission() async {
    try {
      return await _channel.invokeMethod<bool>('hasPermission') ?? false;
    } catch (_) {
      return false;
    }
  }

  /// 显示悬浮窗(蛋/精灵)
  static Future<bool> showOverlay() async {
    try {
      return await _channel.invokeMethod<bool>('show') ?? false;
    } catch (_) {
      return false;
    }
  }

  /// 隐藏悬浮窗
  static Future<bool> hideOverlay() async {
    try {
      return await _channel.invokeMethod<bool>('hide') ?? false;
    } catch (_) {
      return false;
    }
  }

  /// 更新悬浮窗表现: 形态 / 行为 / 气泡
  /// form: egg|baby|adult;  behave: idle|eating|petting|poking|sleeping|hatch|evolve
  static Future<void> update({required String form, required String behave, String? bubble}) async {
    try {
      await _channel.invokeMethod('update', {
        'form': form,
        'behave': behave,
        'bubble': bubble,
      });
    } catch (_) {}
  }

  /// 触发原生 TTS 说话
  static Future<void> say(String text) async {
    try {
      await _channel.invokeMethod('say', {'text': text});
    } catch (_) {}
  }

  /// 触发一次短振动反馈
  static Future<void> vibrate(int ms) async {
    try {
      await _channel.invokeMethod('vibrate', {'ms': ms});
    } catch (_) {}
  }
}
