# TurnIt IDE

TurnIt IDE is an Android app that blends a lightweight on-device development workspace with an AI assistant. It pairs a terminal-style shell, basic editor, file tree, and chat-driven help with cloud authentication and biometric unlocking to create a mobile-first IDE experience.

## What this project is

This repository contains the full Android application for TurnIt IDE. It is built with Jetpack Compose, runs a native shell session on-device, and bootstraps an Ubuntu root filesystem from app assets to prepare a Linux-like workspace for future tooling.

## Core features

- **AI assistant** with OpenAI compatible chat endpoints and model selection.
- **Terminal console** backed by a native `/system/bin/sh` session with streamed output.
- **Root filesystem bootstrap** that extracts an Ubuntu tarball on first run.
- **Basic code editor** with line numbers and monospaced styling.
- **Local file tree** for the app’s private storage.
- **Cloud authentication** via Firebase email/password and Firestore profile sync.
- **Biometric unlock** after authentication for secure access.
- **Crash capture screen** that surfaces stack traces when a fatal error occurs.
- **Native/JNI bridge** prepared for future PRoot-style tooling.

## Project structure

- `app/src/main/java/com/turnit/ide/ui` — Compose UI for the IDE shell, editor, chat, and auth screens.
- `app/src/main/java/com/turnit/ide/engine` — Shell process handling, rootfs extraction, and download helpers.
- `app/src/main/java/com/turnit/ide/ai` — AI model definitions and chat client.
- `app/src/main/java/com/turnit/ide/auth` — Firebase auth helpers and encrypted token storage.
- `app/src/main/cpp` — Native library stub and CMake config.
- `app/src/main/assets/ubuntu.tar.gz` — Ubuntu rootfs packaged for first-run extraction.

## How it works

On first launch, the app checks for an extracted root filesystem under the app’s private files directory. If it is missing, `ExtractionEngine` unpacks the bundled Ubuntu archive while streaming progress to the terminal console. A native shell session (`ShellEngine`) runs `/system/bin/sh` in the app’s sandbox, with output displayed in the terminal pane. The UI combines a tabbed terminal/editor/file tree with an AI chat pane, and the app gates access behind Firebase login plus biometric verification.

## Building the app

**Requirements**
- Android Studio or the Android SDK with Gradle.
- JDK 17.
- Firebase configuration (add your `google-services.json` to `app/` for auth).
- Android NDK is optional unless you build native components.

**Common commands**
```bash
./gradlew assembleDebug
./gradlew lint
./gradlew test
```

## AI configuration

The chat pane supports OpenAI compatible APIs. Default model entries are pre-filled with common endpoints, but you must supply your own API key. Use the “Add Custom Model” dialog in the UI to point to other providers.

## Roadmap notes

See [`NDK_TOOLCHAIN_ARCHITECTURE.md`](./NDK_TOOLCHAIN_ARCHITECTURE.md) for the planned native toolchain pipeline and packaging strategy.
