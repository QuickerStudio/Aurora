# Aurora - Video Live Wallpaper

<div align="center">

![Aurora Icon](app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp)

**A powerful and elegant Android video wallpaper application**

[Features](#features) • [Installation](#installation) • [Usage](#usage) • [Technical Details](#technical-details)

</div>

---

## Features

### 🎨 Theme System
- **4 Beautiful Themes**: Classic, Modern, Elegant, and Vibrant
- **System Theme Support**: Follow system dark/light mode
- **Contrasting Color Design**: Each theme uses carefully selected contrasting colors
- **Real-time Switching**: Changes apply immediately without restart

### 📹 Video Management
- **Wallpaper History**: Automatically tracks all videos you've set as wallpaper
- **Local Video Library**: Browse and select videos from your device
- **Smart Scanning**: Supports MP4, MKV, AVI, MOV, WebM formats
- **Incremental Loading**: Load videos in batches for optimal performance
- **Quick Switching**: Tap any history card to instantly switch wallpaper
- **Long-press Delete**: Hold history cards to remove them with smooth animation

### 🎯 User Interface
- **Aurora Branding**: Custom Mistral font title with icon
- **Dual Navigation**: History grid view + horizontal scrolling local library
- **Auto-hide Library**: Configurable timer (5s/10s/15s) with manual control
- **Scroll-to-hide**: Library automatically hides when scrolling history
- **Swipe-to-show**: Swipe left on the bottom bar to reveal hidden library

### ⚙️ Settings & Customization
- **Audio Control**: Toggle video sound on/off
- **Launcher Icon**: Show/hide app icon from launcher
- **Fullscreen on Unlock**: Jump to fullscreen video when device unlocks
- **FAB Button**: Toggle add video button visibility
- **Auto-hide Timer**: Choose between 5, 10, or 15 second delays

---

## Installation

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 26 (Android 8.0) or higher
- Kotlin 1.9+

### Build Steps

1. Clone the repository:
```bash
git clone https://github.com/QuickerStudio/Aurora.git
cd Aurora
```

2. Open the project in Android Studio

3. Sync Gradle and build the project

4. Run on your device or emulator

---

## Usage

### Setting a Wallpaper

**From Local Library:**
1. Browse videos in the bottom horizontal scrolling library
2. Tap any video card to set it as wallpaper
3. The video is automatically added to your history

**From History:**
1. View your wallpaper history in the main grid
2. Tap any card to switch to that wallpaper instantly

**Manual Selection:**
1. Tap the + button in the top-right corner (if enabled)
2. Select a video from your device
3. Choose "Aurora" from the system wallpaper picker

### Managing History

- **Delete**: Long-press any history card to remove it (with fade-out animation)
- **Switch**: Tap any card to change wallpaper immediately

### Customizing Appearance

1. Open the settings drawer (☰ menu)
2. Choose your preferred theme
3. Toggle "Follow system theme" to match your device
4. Adjust auto-hide timer for the video library

---

## Technical Details

### Architecture
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material3
- **Video Playback**: ExoPlayer for smooth video rendering
- **Storage**: JSON-based history management
- **Media Access**: MediaStore API for local video scanning

### Key Components
- [MainActivity.kt](app/src/main/java/ai/wallpaper/aurora/MainActivity.kt) - Main UI and navigation
- [VideoLiveWallpaperService.kt](app/src/main/java/ai/wallpaper/aurora/service/VideoLiveWallpaperService.kt) - Live wallpaper engine
- [WallpaperHistory.kt](app/src/main/java/ai/wallpaper/aurora/data/WallpaperHistory.kt) - History management system
- [LocalVideoScanner.kt](app/src/main/java/ai/wallpaper/aurora/utils/LocalVideoScanner.kt) - Device video scanning
- [ThemeColors.kt](app/src/main/java/ai/wallpaper/aurora/ui/theme/ThemeColors.kt) - Theme color configuration

### Permissions Required
- `READ_MEDIA_VIDEO` (Android 13+) or `READ_EXTERNAL_STORAGE`
- `SET_WALLPAPER`

---

## Themes

| Theme | Primary | Accent | Description |
|-------|---------|--------|-------------|
| **Classic** | Purple | Gold | Timeless and elegant |
| **Modern** | Cyan | Coral Orange | Fresh and contemporary |
| **Elegant** | Lavender | Rose Pink | Soft and sophisticated |
| **Vibrant** | Rose Red | Mint Green | Bold and energetic |

---

## Gradle 镜像配置

为了加快构建速度，项目已配置国内镜像源（按速度排序）：

1. **腾讯云** - 平均延迟 4ms
   - `https://mirrors.cloud.tencent.com/nexus/repository/maven-public/`

2. **阿里云** - 平均延迟 14ms
   - `https://maven.aliyun.com/repository/public/`
   - `https://maven.aliyun.com/repository/google/`

3. **华为云** - 平均延迟 27ms
   - `https://mirrors.huaweicloud.com/repository/maven/`

镜像配置位于 [settings.gradle.kts](settings.gradle.kts)，已同时配置插件仓库和依赖仓库。

---

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---

## License

**Personal use only. Commercial use is prohibited.**

Copyright © 2026 QuickerStudio. All rights reserved.

---

## Links

- **GitHub**: https://github.com/QuickerStudio/Aurora
- **Issues**: https://github.com/QuickerStudio/Aurora/issues

---

## Acknowledgments

## ✨ **cyunrei** ✨

### [Video-Live-Wallpaper](https://github.com/cyunrei/Video-Live-Wallpaper)

The original author provided the foundational codebase that made Aurora possible. This project started from that foundation and gradually evolved into a product we are truly satisfied with.

Built with ❤️ using:

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)
- [ExoPlayer](https://exoplayer.dev/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

---

**Version**: 1.0.0
