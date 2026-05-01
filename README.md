# Seanime TV

Seanime TV is an Android TV client for [Seanime](https://github.com/5rahim/seanime), a media server for managing your local anime library, streaming, and reading manga.

This app provides a seamless way to access your Seanime server directly from your television, using a WebView-based interface optimized for Android TV.

## Features

- **Android TV Optimized**: Designed for Leanback devices with D-pad navigation support.
- **Full Seanime Experience**: Access all features of the Seanime web interface, including library management, torrent streaming, and more.
- **External Player Support**: Seamlessly open media in popular players like VLC, mpv, or MX Player for the best playback experience.
- **Simple Configuration**: Just enter your server's IP address and port to get started.

## Getting Started

### Prerequisites

- A running instance of the [Seanime Server](https://github.com/5rahim/seanime).
- An Android TV device or emulator.

### Installation

1. Download the latest APK from the [Releases](https://github.com/5rahim/seanime/releases) page.
2. Sideload the APK onto your Android TV device.
3. Open the app and enter your Seanime server URL (e.g., `http://192.168.1.5:43211`).

## Building from Source

If you want to build the app yourself, you'll need [Android Studio](https://developer.android.com/studio) or the Android SDK.

1. Clone the repository:
   ```bash
   git clone https://github.com/ljesuus/seanime-tv.git
   ```
2. Navigate to the `seanimetv` directory:
   ```bash
   cd seanime-tv
   ```
3. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
4. The generated APK will be located in `app/build/outputs/apk/debug/`.

## Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Navigation**: Leanback-ready components
- **Build System**: Gradle (Kotlin DSL)
