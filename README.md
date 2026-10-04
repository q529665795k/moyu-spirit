# 桌面灵宠 moyu-spirit

从一颗蛋破壳、进化成狐耳少女的安卓桌面宠物 APP。纯本地单机、零网络、原创代码 + 原创素材(豆包 AI 生成)。

- 目标:自用 + 抖音展示,不上架、不收费、不接广告
- 开发:写代码 → 本仓库 → GitHub Actions 自动打包 APK → 手机安装
- 悬浮窗:原生 Android Service 自写实现(不依赖第三方悬浮窗插件)
- 素材:当前全部为代码绘制占位图;豆包 70 张序列帧到货后一次性导入替换

## 已实现功能(第 2~6 步全部代码)

| 模块 | 说明 |
|---|---|
| 悬浮窗 | 原生 Kotlin 前台服务 + WindowManager:单指拖动、双指缩放、透明度调节、点击弹出互动菜单 |
| 蛋形态 | 呼吸光效 + 晃动 + 点击吐槽气泡 |
| 破壳进化 | 喂食推进:蛋 → 狐耳幼宠(1 次) → 完全体(6 次),全屏闪光 |
| 喂食 | 苹果/小鱼/糖果 3 种,饱腹度系统(吃涨、时间推移掉) |
| 互动 | 摸头冒爱心、戳肚子打滚/发脾气、点击随机吐槽 |
| 睡觉 | 长时间不理自动睡(冒 ZZZ),可戳醒发脾气 |
| 心情 | 开心/一般/饿/困状态条,心情好自动回涨 |
| 语音吐槽 | 系统 TTS,30 分钟没互动随机触发 20+ 句吐槽,气泡同步 |
| 存档 | shared_preferences 本地存形态/饱腹/心情/喂食次数,杀进程不丢 |
| 权限引导 | 内置"显示在其他应用上层"权限申请与手动引导 |

## 技术栈

Flutter + 原生 Kotlin 悬浮窗 Service + shared_preferences + GitHub Actions

## 打包

每次 push 到 main 自动触发 Actions,APK 产物在 Actions 页面 Artifacts 下载。

## 目录

- `lib/` Flutter 层:主界面 / 玩法状态机 / 吐槽库 / 悬浮窗桥接 / 占位绘制
- `android_src/` 原生层:PetOverlayService.kt(悬浮窗) / MainActivity.kt(权限+桥) / AndroidManifest.xml
- `.github/workflows/build-apk.yml` 自动打包(先 flutter create 骨架,再覆盖原生源)
