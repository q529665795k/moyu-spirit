import 'dart:math';
import 'package:flutter/material.dart';

/// 全部占位绘制(豆包素材到位后替换为序列帧/精灵表)
/// 蛋: 椭圆渐变 + 光晕呼吸 + 晃动
/// 幼宠/完全体: 简单 Q 版脸 + 狐耳(占位)
/// 爱心 / ZZZ / 饿气泡: 代码绘制

/// 蛋(占位)
class EggPainter extends CustomPainter {
  final double breathe; // 0~1 呼吸相位
  final bool wobble;    // 是否晃动

  EggPainter({required this.breathe, this.wobble = false});

  @override
  void paint(Canvas canvas, Size size) {
    final c = size.center(Offset.zero);
    // 光晕
    final glow = Paint()
      ..shader = RadialGradient(colors: [
        Color(0x559C8ADF),
        Color(0x008E7CC3),
      ]).createShader(Rect.fromCircle(center: c, radius: size.width * 0.7));
    canvas.drawCircle(c, size.width * 0.62, glow);

    // 蛋体(椭圆)
    final eggRect = Rect.fromCenter(
      center: c.translate(0, -size.height * 0.02),
      width: size.width * 0.62,
      height: size.height * 0.8,
    );
    final eggPaint = Paint()
      ..shader = RadialGradient(
        center: const Alignment(-0.3, -0.4),
        colors: [Color(0xFFFFFBFF), Color(0xFFDCD0F8), Color(0xFF9C8ADF)],
      ).createShader(eggRect);
    canvas.drawOval(eggRect, eggPaint);
    // 蛋壳高光
    final hl = Paint()..color = Colors.white.withOpacity(0.65);
    canvas.drawOval(
      Rect.fromCenter(
        center: c.translate(-size.width * 0.13, -size.height * 0.16),
        width: size.width * 0.13,
        height: size.height * 0.2,
      ),
      hl,
    );
    // 表情(占位: 两点眼 + 小嘴)
    final eye = Paint()..color = const Color(0xFF5B4B8A);
    canvas.drawCircle(c.translate(-size.width * 0.12, -size.height * 0.05), size.width * 0.022, eye);
    canvas.drawCircle(c.translate(size.width * 0.12, -size.height * 0.05), size.width * 0.022, eye);
    final mouth = Paint()
      ..color = const Color(0xFF5B4B8A)
      ..style = PaintingStyle.stroke
      ..strokeWidth = size.width * 0.015
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(
      Rect.fromCenter(center: c.translate(0, size.height * 0.06), width: size.width * 0.14, height: size.height * 0.08),
      0.15 * pi, 0.7 * pi, false, mouth,
    );
  }

  @override
  bool shouldRepaint(covariant EggPainter old) =>
      old.breathe != breathe || old.wobble != wobble;
}

/// Q版小精灵脸(占位: 圆脸 + 狐耳 + 眼睛 + 腮红)
class SpiritPainter extends CustomPainter {
  final double breathe;
  final bool asleep;
  final bool evolve; // 进化状态: 加光环
  final Color furColor;

  SpiritPainter({
    required this.breathe,
    this.asleep = false,
    this.evolve = false,
    this.furColor = const Color(0xFFFFF2F8),
  });

  @override
  void paint(Canvas canvas, Size size) {
    final c = size.center(Offset.zero);
    final r = size.width * 0.34 * (1 + breathe * 0.03);

    // 进化光环
    if (evolve) {
      final ring = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = size.width * 0.03
        ..color = Color(0xFFFFD54F).withOpacity(0.5 + breathe * 0.4);
      canvas.drawCircle(c.translate(0, -size.height * 0.02), r * 1.35, ring);
    }

    // 耳朵(狐耳: 两个三角)
    final ear = Path()
      ..moveTo(c.dx - r * 0.62, c.dy - r * 0.35)
      ..lineTo(c.dx - r * 0.85, c.dy - r * 1.05)
      ..lineTo(c.dx - r * 0.25, c.dy - r * 0.75)
      ..close();
    canvas.drawPath(ear, Paint()..color = furColor);
    final earR = Path()
      ..moveTo(c.dx + r * 0.62, c.dy - r * 0.35)
      ..lineTo(c.dx + r * 0.85, c.dy - r * 1.05)
      ..lineTo(c.dx + r * 0.25, c.dy - r * 0.75)
      ..close();
    canvas.drawPath(earR, Paint()..color = furColor);
    // 耳朵内粉
    final earIn = Paint()..color = const Color(0xFFFFB7D0);
    canvas.drawCircle(c.translate(-r * 0.6, -r * 0.95), r * 0.12, earIn);
    canvas.drawCircle(c.translate(r * 0.6, -r * 0.95), r * 0.12, earIn);

    // 脸
    canvas.drawCircle(c.translate(0, -r * 0.05), r, Paint()..color = furColor);

    // 眼睛
    final eyePaint = Paint()..color = const Color(0xFF4A3B6B);
    if (asleep) {
      // 闭眼: 弯线
      final line = Paint()
        ..color = const Color(0xFF4A3B6B)
        ..style = PaintingStyle.stroke
        ..strokeWidth = size.width * 0.018
        ..strokeCap = StrokeCap.round;
      canvas.drawArc(Rect.fromCenter(center: c.translate(-r * 0.4, -r * 0.05), width: r * 0.38, height: r * 0.2), 0, pi, false, line);
      canvas.drawArc(Rect.fromCenter(center: c.translate(r * 0.4, -r * 0.05), width: r * 0.38, height: r * 0.2), 0, pi, false, line);
    } else {
      canvas.drawCircle(c.translate(-r * 0.4, -r * 0.08), r * 0.11, eyePaint);
      canvas.drawCircle(c.translate(r * 0.4, -r * 0.08), r * 0.11, eyePaint);
      final hl = Paint()..color = Colors.white;
      canvas.drawCircle(c.translate(-r * 0.37, -r * 0.13), r * 0.035, hl);
      canvas.drawCircle(c.translate(r * 0.43, -r * 0.13), r * 0.035, hl);
    }

    // 腮红
    final blush = Paint()..color = const Color(0x80FF8FB1);
    canvas.drawCircle(c.translate(-r * 0.62, r * 0.18), r * 0.12, blush);
    canvas.drawCircle(c.translate(r * 0.62, r * 0.18), r * 0.12, blush);

    // 嘴
    final mouth = Paint()
      ..color = const Color(0xFF4A3B6B)
      ..style = PaintingStyle.stroke
      ..strokeWidth = size.width * 0.015
      ..strokeCap = StrokeCap.round;
    if (asleep) {
      canvas.drawArc(Rect.fromCenter(center: c.translate(0, r * 0.32), width: r * 0.3, height: r * 0.14), 0.15 * pi, 0.7 * pi, false, mouth);
    } else {
      canvas.drawArc(Rect.fromCenter(center: c.translate(0, r * 0.3), width: r * 0.2, height: r * 0.16), 0.15 * pi, 0.7 * pi, false, mouth);
    }

    // ZZZ 文字(睡觉时)
    if (asleep) {
      final tp = TextPainter(
        text: const TextSpan(
          text: 'Z z z',
          style: TextStyle(fontSize: 22, color: Color(0xFF8E7CC3), fontWeight: FontWeight.bold),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, c.translate(r * 0.55, -r * 1.2));
    }
  }

  @override
  bool shouldRepaint(covariant SpiritPainter old) =>
      old.breathe != breathe ||
      old.asleep != asleep ||
      old.evolve != evolve ||
      old.furColor != furColor;
}

/// 爱心气泡(摸头)
class HeartPainter extends CustomPainter {
  final double alpha;
  HeartPainter({required this.alpha});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = const Color(0xFFFF5C8A).withOpacity(alpha);
    final path = Path();
    final c = size.center(Offset.zero);
    final s = size.width * 0.5;
    path.moveTo(c.dx, c.dy + s * 0.6);
    path.cubicTo(c.dx - s, c.dy - s * 0.2, c.dx - s * 0.5, c.dy - s, c.dx, c.dy - s * 0.1);
    path.cubicTo(c.dx + s * 0.5, c.dy - s, c.dx + s, c.dy - s * 0.2, c.dx, c.dy + s * 0.6);
    path.close();
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant HeartPainter old) => old.alpha != alpha;
}
