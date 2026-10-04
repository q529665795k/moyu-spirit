import 'package:flutter/material.dart';
import 'home_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const SpiritApp());
}

class SpiritApp extends StatelessWidget {
  const SpiritApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '桌面灵宠',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: const Color(0xFF8E7CC3),
      ),
      home: const HomePage(),
    );
  }
}
