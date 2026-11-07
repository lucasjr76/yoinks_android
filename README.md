# Yoinks Android

**English** · [Português](README.pt-BR.md)

An Android initiative based on [Yoinks by Pablo Stanley](https://github.com/pablostanley/yoinks)
("yoink any video from your terminal"). It brings the same idea to the phone:
download an MP4 video or extract an MP3 audio track with yt-dlp and FFmpeg,
running entirely on the device. It also saves the images of TikTok photo posts.

This is an independent project. It is not affiliated with or endorsed by the
original Yoinks author.

See the [functional and technical specification](SPECIFICATION.md).

## Download

Get the APK from the [latest release](https://github.com/lucasjr76/yoinks_android/releases/latest).
It is ARM64 and requires Android 10 or later.

The APK is a debug build signed with a different certificate than 0.2.1.
Uninstall any previous version before installing it.

## Use

1. Open Yoinks Android, copy an HTTPS link and tap **Colar link** (Paste link),
   or use **Share → Yoinks Android** in the browser or source app.
2. Tap **Baixar vídeo MP4** (MP4 video) or **Baixar áudio MP3** (MP3 audio).
3. Keep the app open until it finishes. The file is in **Downloads/Yoinks**.
4. For a TikTok photo post, the video button becomes **Baixar fotos (JPG)**
   and saves the post's images.

One download at a time, automatic quality, Portuguese UI. Site coverage
depends on the bundled yt-dlp version.

## Build

Requirements: JDK 17, Android SDK 35 and Gradle 8.9 (or the included wrapper).
In Android Studio, open this folder and sync the project. From the command line:

```sh
./gradlew :app:assembleDebug                  # ARM64 + x86_64 (emulator)
./gradlew :app:assembleDebug -Pabi=arm64-v8a  # phone-only APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Unit tests: `./gradlew :app:testDebugUnitTest`.

## yt-dlp engine

The resource `app/src/main/res/raw/ytdlp` replaces the library's default copy
with the portable [2026.10.26](https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26)
build. Its SHA-256 matches the one published in the release:

```text
e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3
```

## Licenses

Code licensed under [GPL-3.0-only](LICENSE). The original Yoinks MIT notice is
in [`LICENSE-YOINKS`](LICENSE-YOINKS). The Android engine is
[youtubedl-android](https://github.com/yausername/youtubedl-android), licensed
under GPL-3.0, and bundles yt-dlp and FFmpeg. The source code for each release
APK is the matching tag in this repository.
