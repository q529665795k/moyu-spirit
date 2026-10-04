import 'dart:async';
import 'dart:math';
import 'package:shared_preferences/shared_preferences.dart';
import 'pet_taunts.dart';
import 'overlay_bridge.dart';

/// 灵宠形态
enum PetForm { egg, baby, adult }

/// 当前行为
enum PetBehave { idle, eating, petting, poking, sleeping, hatch, evolve }

/// 玩法状态机: 饱腹度 / 心情 / 进化 / 存档 / 吐槽触发
class PetLogic {
  PetForm form = PetForm.egg;
  PetBehave behave = PetBehave.idle;

  /// 0-100
  double fullness = 80;

  /// 0-100
  double mood = 80;

  /// 喂食次数(进化进度)
  int feedCount = 0;

  /// 进化所需喂食次数
  static const int feedForBaby = 1;
  static const int feedForAdult = 6;

  /// 距离上次互动秒数
  int idleSeconds = 0;

  DateTime _lastInteract = DateTime.now();
  DateTime _lastTick = DateTime.now();
  Timer? _tickTimer;
  Timer? _sleepTimer;
  bool _sleeping = false;

  /// 每次 tick 间隔(秒)
  static const int tickIntervalSec = 20;

  /// 互动多久后开始掉饱腹(秒)
  static const int fullnessDropAfterSec = 60;

  /// 多久没互动进入睡觉(秒)
  static const int sleepAfterSec = 300;

  /// 多久没互动触发语音吐槽(秒)
  static const int tauntAfterSec = 180;

  bool get isSleeping => _sleeping;

  /// 初始化 + 读档 + 启动计时器
  Future<void> init() async {
    await _load();
    _lastTick = DateTime.now();
    _tickTimer = Timer.periodic(const Duration(seconds: tickIntervalSec), (_) => tick());
  }

  void dispose() {
    _tickTimer?.cancel();
    _sleepTimer?.cancel();
  }

  /// 周期心跳: 时间流逝掉饱腹/心情, 触发睡觉与语音吐槽
  Future<void> tick() async {
    final now = DateTime.now();
    final sinceInteract = now.difference(_lastInteract).inSeconds;
    idleSeconds = sinceInteract;

    // 掉饱腹: 互动超过 1 分钟后每 tick 掉 2
    if (sinceInteract > fullnessDropAfterSec && !_sleeping) {
      fullness = max(0, fullness - 2);
    }
    // 饿肚子掉心情更快
    if (fullness < 20) {
      mood = max(0, mood - 3);
    } else if (fullness > 60) {
      mood = min(100, mood + 1);
    } else {
      mood = max(0, mood - 1);
    }

    // 长时间没互动 → 睡觉
    if (sinceInteract > sleepAfterSec && !_sleeping) {
      _sleeping = true;
      behave = PetBehave.sleeping;
      await _pushToOverlay();
    }

    // 长时间没互动 → 语音吐槽(气泡+TTS)
    if (sinceInteract > tauntAfterSec && !_sleeping && sinceInteract % 60 < tickIntervalSec) {
      final text = fullness < 25
          ? kHungryTaunts[Random().nextInt(kHungryTaunts.length)]
          : kTaunts[Random().nextInt(kTaunts.length)];
      await OverlayBridge.update(form: _formName, behave: 'idle', bubble: text);
      await OverlayBridge.say(text);
    }

    await _save();
  }

  /// 互动动作: 喂食 / 摸头 / 戳肚子 / 点击
  Future<void> feed(int food) async {
    _interact();
    behave = PetBehave.eating;
    feedCount += 1;
    fullness = min(100, fullness + 25);
    mood = min(100, mood + 8);
    await OverlayBridge.vibrate(30);
    await _pushToOverlay(bubble: '啊呜~ 好吃!');

    // 进化判定
    if (form == PetForm.egg && feedCount >= feedForBaby) {
      await evolveTo(PetForm.baby);
    } else if (form == PetForm.baby && feedCount >= feedForAdult) {
      await evolveTo(PetForm.adult);
    }

    await _save();
    await _resetBehave();
  }

  Future<void> pet() async {
    _interact();
    behave = PetBehave.petting;
    mood = min(100, mood + 10);
    await OverlayBridge.vibrate(20);
    await _pushToOverlay(bubble: '❤ 摸摸头~');
    await _save();
    await _resetBehave();
  }

  Future<void> poke() async {
    _interact();
    behave = PetBehave.poking;
    mood = max(0, mood - 3);
    await OverlayBridge.vibrate(60);
    if (_sleeping) {
      _sleeping = false;
      final t = kAngryTaunts[Random().nextInt(kAngryTaunts.length)];
      await _pushToOverlay(bubble: t);
      await OverlayBridge.say(t);
    } else {
      await _pushToOverlay(bubble: '咕噜! 别戳我肚子!');
    }
    await _save();
    await _resetBehave();
  }

  Future<void> tap() async {
    _interact();
    if (_sleeping) {
      _sleeping = false;
      behave = PetBehave.poking;
      final t = kAngryTaunts[Random().nextInt(kAngryTaunts.length)];
      await _pushToOverlay(bubble: t);
      await OverlayBridge.say(t);
      await _resetBehave();
      return;
    }
    final t = kTaunts[Random().nextInt(kTaunts.length)];
    await _pushToOverlay(bubble: t);
    await OverlayBridge.say(t);
  }

  /// 进化: 全屏闪光逻辑由主界面播放, 这里切形态
  Future<void> evolveTo(PetForm next) async {
    behave = PetBehave.evolve;
    form = next;
    await _pushToOverlay(bubble: next == PetForm.baby ? '破壳啦! 我是谁? 我在哪?' : '完全体觉醒! 闪闪发光~');
    await OverlayBridge.say(next == PetForm.baby ? '破壳啦, 终于见到主人啦' : '进化完成, 我是完全体啦');
    await _save();
    await _resetBehave();
  }

  /// 主动睡觉(按钮触发)
  Future<void> nap() async {
    _interact();
    _sleeping = true;
    behave = PetBehave.sleeping;
    await _pushToOverlay(bubble: '晚安 ZZZ~');
    await _save();
  }

  void _interact() {
    _lastInteract = DateTime.now();
    idleSeconds = 0;
    if (_sleeping) _sleeping = false;
  }

  Future<void> _pushToOverlay({String? bubble}) async {
    await OverlayBridge.update(
      form: _formName,
      behave: _behaveName,
      bubble: bubble,
    );
  }

  Future<void> _resetBehave() async {
    await Future.delayed(const Duration(seconds: 2));
    behave = PetBehave.idle;
    if (_sleeping) behave = PetBehave.sleeping;
    await OverlayBridge.update(form: _formName, behave: _behaveName);
  }

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

  // ---------- 存档 ----------
  static const _kForm = 'pet_form';
  static const _kFullness = 'pet_fullness';
  static const _kMood = 'pet_mood';
  static const _kFeed = 'pet_feed';
  static const _kLastInteract = 'pet_last_interact';

  Future<void> _load() async {
    final sp = await SharedPreferences.getInstance();
    form = PetForm.values[sp.getInt(_kForm) ?? 0];
    fullness = (sp.getDouble(_kFullness) ?? 80).clamp(0, 100);
    mood = (sp.getDouble(_kMood) ?? 80).clamp(0, 100);
    feedCount = sp.getInt(_kFeed) ?? 0;
    final last = sp.getInt(_kLastInteract);
    if (last != null) _lastInteract = DateTime.fromMillisecondsSinceEpoch(last);
  }

  Future<void> _save() async {
    final sp = await SharedPreferences.getInstance();
    await sp.setInt(_kForm, form.index);
    await sp.setDouble(_kFullness, fullness);
    await sp.setDouble(_kMood, mood);
    await sp.setInt(_kFeed, feedCount);
    await sp.setInt(_kLastInteract, _lastInteract.millisecondsSinceEpoch);
  }
}
