# SeedlessDS

A Nintendo DS emulator for Android handhelds with physical controls, including devices with two displays.

SeedlessDS is the smaller sibling of WatermelonDS: the same watermelon idea, with the seeds taken out.

<p align="center">
<img width="350" height="300" alt="SeedlessDS library" src="https://github.com/user-attachments/assets/9e9fbcee-28d5-4938-8b81-bbf5eb30fc36" />
<img width="350" height="300" alt="SeedlessDS interface" src="https://github.com/user-attachments/assets/74c48268-3037-4c81-9abe-400f0b790b9f" />
<img width="350" height="300" alt="SeedlessDS settings" src="https://github.com/user-attachments/assets/b23fb273-9a10-4244-b2b5-f05003c48c4d" />
</p>

> **Current status:** `1.0.0`. The Android app uses [SeedlessCoreDS](core/README.md), a Nintendo DS emulation library reconstructed from DraStic r2.6.0.4a and built from source. The original DraStic APK and prebuilt core are not required to build or run SeedlessDS.

## What it is

SeedlessDS combines a game library, settings, input mapping, save states, cheats and RetroAchievements with a renderer for one or two physical displays. The Android frontend and reconstructed core communicate through JNI and the core's C API.

## Why SeedlessDS exists

The project focuses on Android handhelds where emulation performance and physical controls matter. It complements the melonDS-based WatermelonDS project with a different core. Performance depends on the game, device and internal resolution; higher resolutions require more processing power.

## The differentiator

### DualScreen Presets

Presets coordinate scale, aspect ratio, alignment, position and touch mapping across both displays. Layouts can also be adjusted for a single display.

### Dual-panel rendering

The app coordinates each emulated frame and its presentation through EGL/OpenGL ES, with a separate presentation thread for the external display. This helps games that connect the two DS screens into one scene. Behavior still depends on the device's display implementation.

### Controller-first interface

The interface supports D-pad navigation, shoulder-button tab switching and configurable emulator shortcuts. On supported dual-display layouts, the second panel can show information about the selected item.

## Features

### Library

- User-selected game folders, favorites and accent-insensitive search.
- Library filters, a Continue Playing section and per-game save slots.
- Box art from libretro thumbnails, with a bundled index and disk cache.

### Emulation

- Save states with screenshots, quick save and quick load.
- Bundled cheat database and custom cheat codes.
- Global settings, per-game overrides and profiles.
- Editable on-screen controls, screen layouts, image filters and integer scaling.
- Internal resolution settings from 1x to 8x.

### RetroAchievements

The integration uses [`rcheevos`](https://github.com/RetroAchievements/rcheevos), pinned to v12.3.0. It provides achievements, leaderboards and profile information, including casual and hardcore score displays. Compatibility is still being tested; a score display alone does not imply that every Hardcore feature is supported.

### Built for a gamepad

Map DS buttons and emulator actions to physical controls: fast-forward, quick save/load, swapping screens, cycling layouts, microphone input and turbo.

### Front-end friendly

Launchers can start a game through the `LaunchGame` activity alias:

```sh
am start -n com.seedlessds.app/com.seedlessds.app.LaunchGame \
    -e rom_path /storage/emulated/0/roms/nds/game.nds \
    --ei load_slot 1
```

The app recognizes `.nds`, `.dsi`, `.srl` and `.ids` game files and includes archive handling for ZIP, 7z and RAR. File recognition does not guarantee that a game or every archive layout is compatible. Device testing of the 7z/RAR paths remains in progress.

## How it is built

| Layer | Implementation |
|---|---|
| Android app | Kotlin, Jetpack Compose, Material 3 and Android Views |
| Emulation | [SeedlessCoreDS](core/README.md), in the `core/` submodule |
| Core library | `librecon_fn.so`, compiled from C and AArch64 assembly |
| JNI | `librecon.so`, connecting Android to the core |
| App helpers and achievements | `libseedless_bridge.so` and rcheevos |
| Display and audio | EGL/OpenGL ES and OpenSL ES |
| Supported ABI | `arm64-v8a` |
| Android | API 21 minimum; API 35 compile/target SDK |
| Version | `1.0.0`, version code 110 |

### Build from source

Initialize the submodules from the repository root:

```sh
git submodule update --init --recursive
```

Use JDK 17, Android SDK 35, NDK `25.2.9519653` and CMake `3.22.1`. The Gradle wrapper selects Gradle `8.11.1`. Set the SDK location through `local.properties` or your Android SDK environment. The native build downloads pinned UnRAR and LZMA SDK sources with SHA-256 verification; see [third-party licenses](core/third_party/LICENSES.md).

Build the debug app (`com.seedlessds.app.debug`):

```sh
./gradlew assembleDebug
```

For the signed release (`com.seedlessds.app`), configure `MELONDS_KEYSTORE`, `MELONDS_KEYSTORE_PASSWORD`, `MELONDS_KEY_ALIAS` and `MELONDS_KEY_PASSWORD` in your untracked `local.properties`, then run:

```sh
./gradlew assembleRelease
```

On Windows, use `gradlew.bat` with the same arguments. APKs are written under `app/build/outputs/apk/`. Keep signing credentials and the release keystore outside version control. The tracked debug keystore is only for debug builds.

## Roadmap

The reconstructed core is now built from source. Future work includes broader compatibility testing, performance improvements and continued refinement of the Android interface. A Linux frontend remains a future direction; there is no released Linux application in this repository.

## Credits and distribution

DraStic was created by its original authors, including Exophase. SeedlessCoreDS is reconstructed from DraStic r2.6.0.4a; this is not a claim of an independently designed core. SeedlessDS is a separate project and is not an official DraStic release.

Games and original Nintendo BIOS/firmware images are not included. The app includes replacement BIOS assets. Third-party source and assets retain their respective attribution and license terms. Nintendo and game names belong to their respective owners; the project is not affiliated with Nintendo.

## Current known notes

- This release supports ARM64 Android devices.
- Game compatibility, performance and dual-display behavior vary by device.
- RetroAchievements and archive handling need further compatibility testing.
- The interface is still being refined.

## Reporting issues

Include the device model, Android version, game, internal resolution, display layout, input method and whether RetroAchievements was enabled. Add reproduction steps and, where useful, screenshots or logs. Do not attach games, BIOS images, firmware or signing credentials.

## License

A project-wide license has not yet been selected. The included dependencies retain their own licenses; see [rcheevos](app/src/main/cpp/rcheevos/LICENSE) and the [core dependency list](core/third_party/LICENSES.md).
