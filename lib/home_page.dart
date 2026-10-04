import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';
import 'pet_logic.dart';
import 'painters.dart';
import 'overlay_bridge.dart';
import 'pet_taunts.dart';

/// 主界面: 权限引导 + 状态面板 + 喂食/摸头/戳/睡觉 + 进化闪光
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with SingleTickerProviderStateMixin {
  final PetLogic _logic = PetLogic();
  bool _permission = false;
  bool _overlayVisible = false;
  String _status = '加载中…';
  bool _evolving = false;

  late final AnimationController _evolveCtrl = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1600),
  );

  Timer? _uiTimer;

  @override
  void initState() {
    super.initState();
    _bootstrap();
    OverlayBridge.listen((action) => _handleOverlayAction(action));
    _uiTimer = Timer.periodic(const Duration(seconds: 2), (_) => setState(() {}));
  }

  /// 原生悬浮窗菜单动作入口
  Future<void> _handleOverlayAction(String action) async {
    switch (action) {
      case 'feed':
        await _doFeed();
        break;
      case 'pet':
        await _doPet();
        break;
      case 'poke':
        await _doPoke();
        break;
      case 'nap':
        await _doNap();
        break;
      case 'taunt':
        await _logic.tap();
        setState(() {});
        break;
    }
  }

  Future<void> _bootstrap() async {
    await _logic.init();
    _permission = await OverlayBridge.hasPermission();
    setState(() {
      _status = _permission ? '悬浮窗权限:已授权' : '悬浮窗权限:未授权';
    });
  }

  @override
  void dispose() {
    _logic.dispose();
    _uiTimer?.cancel();
    _evolveCtrl.dispose();
    super.dispose();
  }

  Future<void> _start() async {
    if (!_permission) {
      _permission = await OverlayBridge.ensurePermission();
      if (!_permission) {
        setState(() => _status = '权限被拒, 请到系统设置 → 应用 → 桌面灵宠 → 显示在其他应用上层 手动打开');
        return;
      }
      setState(() => _status = '悬浮窗权限:已授权');
    }
    final ok = await OverlayBridge.showOverlay();
    setState(() {
      _overlayVisible = ok;
      _status = ok ? '悬浮窗已开启, 回桌面就能看到灵宠!' : '悬浮窗启动失败';
    });
    if (ok) {
      await OverlayBridge.update(form: _logic._formName, behave: _logic._behaveName, bubble: '嗨, 我是灵宠~');
    }
  }

  Future<void> _stop() async {
    await OverlayBridge.hideOverlay();
    setState(() => _overlayVisible = false);
  }

  Future<void> _doFeed() async {
    await _logic.feed(0);
    setState(() {});
    _maybeEvolveFlash();
  }

  Future<void> _doPet() async {
    await _logic.pet();
    setState(() {});
  }

  Future<void> _doPoke() async {
    await _logic.poke();
    setState(() {});
  }

  Future<void> _doNap() async {
    await _logic.nap();
    setState(() {});
  }

  /// 进化全屏闪光
  void _maybeEvolveFlash() {
    if (_logic.feedCount == PetLogic.feedForBaby ||
        _logic.feedCount == PetLogic.feedForAdult) {
      setState(() => _evolving = true);
      _evolveCtrl.repeat(reverse: true);
      Timer(const Duration(milliseconds: 2200), () {
        _evolveCtrl.stop();
        setState(() => _evolving = false);
      });
    }
  }

  String get _formLabel {
    switch (_logic.form) {
      case PetForm.egg: return '蛋形态';
      case PetForm.baby: return '狐耳幼宠';
      case PetForm.adult: return '完全体';
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: Stack(
        children: [
          // 进化闪光层
          if (_evolving)
            Positioned.fill(
              child: IgnorePointer(
                child: AnimatedBuilder(
                  animation: _evolveCtrl,
                  builder: (context, _) => Container(
                    color: Colors.white.withOpacity(0.3 + _evolveCtrl.value * 0.6),
                    alignment: Alignment.center,
                    child: const Icon(Icons.auto_awesome, size: 120, color: Colors.amber),
                  ),
                ),
              ),
            ),
          Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _buildPetPreview(theme),
                  const SizedBox(height: 16),
                  Text('桌面灵宠', style: theme.textTheme.headlineMedium),
                  Text('$_formLabel · 摸鱼基地出品', style: theme.textTheme.bodySmall),
                  const SizedBox(height: 12),
                  _buildStats(theme),
                  const SizedBox(height: 16),
                  Text(_status, style: theme.textTheme.bodySmall),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: _overlayVisible ? _stop : _start,
                    icon: Icon(_overlayVisible ? Icons.visibility_off : Icons.pets),
                    label: Text(_overlayVisible ? '关闭悬浮窗' : '开始悬浮窗'),
                  ),
                  const SizedBox(height: 20),
                  _buildInteractRow(),
                  const SizedBox(height: 8),
                  _buildFeedRow(),
                  const SizedBox(height: 20),
                  Text(
                    '小提示: 回桌面看悬浮灵宠, 可拖动/点击; 摸头涨心情, 喂食推进进化; 很久不理它会睡觉还会语音吐槽哦',
                    style: theme.textTheme.bodySmall?.copyWith(color: Colors.grey),
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPetPreview(ThemeData theme) {
    return AnimatedBuilder(
      animation: _evolveCtrl,
      builder: (context, child) {
        final breathe = _evolveCtrl.isAnimating
            ? _evolveCtrl.value
            : (DateTime.now().millisecondsSinceEpoch % 1600) / 1600;
        if (_logic.form == PetForm.egg) {
          return CustomPaint(
            size: const Size(150, 190),
            painter: EggPainter(breathe: breathe, wobble: _logic.behave.name == 'eating'),
          );
        }
        return CustomPaint(
          size: const Size(150, 190),
          painter: SpiritPainter(
            breathe: breathe,
            asleep: _logic.isSleeping,
            evolve: _logic.form == PetForm.adult,
          ),
        );
      },
    );
  }

  Widget _buildStats(ThemeData theme) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _statBar(theme, '饱腹度', _logic.fullness, const Color(0xFFFF9E5C)),
            const SizedBox(height: 8),
            _statBar(theme, '心情', _logic.mood, const Color(0xFFFF5C8A)),
            const SizedBox(height: 8),
            _statBar(theme, '进化', (_logic.feedCount / PetLogic.feedForAdult).clamp(0.0, 1.0) * 100, const Color(0xFF9C8ADF)),
            const SizedBox(height: 4),
            Text('喂食 ${_logic.feedCount} 次 · 距离完全体还需 ${max(0, PetLogic.feedForAdult - _logic.feedCount)} 次',
                style: theme.textTheme.bodySmall),
          ],
        ),
      ),
    );
  }

  Widget _statBar(ThemeData theme, String label, double value, Color color) {
    return Row(
      children: [
        SizedBox(width: 48, child: Text(label, style: theme.textTheme.bodySmall)),
        Expanded(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(
              value: (value / 100).clamp(0.0, 1.0),
              minHeight: 10,
              backgroundColor: Colors.grey.shade200,
              valueColor: AlwaysStoppedAnimation(color),
            ),
          ),
        ),
        SizedBox(width: 36, child: Text('${value.round()}', textAlign: TextAlign.right, style: theme.textTheme.bodySmall)),
      ],
    );
  }

  Widget _buildInteractRow() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        _actionBtn(Icons.favorite, '摸头', _doPet, const Color(0xFFFF5C8A)),
        const SizedBox(width: 12),
        _actionBtn(Icons.touch_app, '戳肚子', _doPoke, const Color(0xFF5C8AFF)),
        const SizedBox(width: 12),
        _actionBtn(Icons.bedtime, '睡觉', _doNap, const Color(0xFF9C8ADF)),
      ],
    );
  }

  Widget _buildFeedRow() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        _actionBtn(Icons.apple, '苹果', _doFeed, const Color(0xFFFF6B6B)),
        const SizedBox(width: 12),
        _actionBtn(Icons.set_meal, '小鱼', _doFeed, const Color(0xFF4FC3F7)),
        const SizedBox(width: 12),
        _actionBtn(Icons.cake, '糖果', _doFeed, const Color(0xFFFFB74D)),
      ],
    );
  }

  Widget _actionBtn(IconData icon, String label, VoidCallback onTap, Color color) {
    return FilledButton.tonalIcon(
      onPressed: onTap,
      icon: Icon(icon, color: color),
      label: Text(label),
    );
  }
}

/// 随手工具: 状态名字段访问(避免 import 内部)
extension PetLogicFormName on PetLogic {
  String get _formName {
    switch (form) {
      case PetForm.egg: return 'egg';
      case PetForm.baby: return 'baby';
      case PetForm.adult: return 'adult';
    }
  }

  String get _behaveName {
    switch (behave) {
      case PetBehave.idle: return 'idle';
      case PetBehave.eating: return 'eating';
      case PetBehave.petting: return 'petting';
      case PetBehave.poking: return 'poking';
      case PetBehave.sleeping: return 'sleeping';
      case PetBehave.hatch: return 'hatch';
      case PetBehave.evolve: return 'evolve';
    }
  }
}
