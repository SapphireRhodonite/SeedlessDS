# SeedlessDS

A Nintendo DS emulator app for Android, built for handhelds with physical controls and, above all, for devices.

The name is a joke that stuck: SeedlessDS is the smaller sibling of WatermelonDS, using the same watermelon idea with the seeds taken out.

<p align="center">
<img width="350" height="300" alt="Screenshot_20260807-202219" src="https://github.com/user-attachments/assets/9e9fbcee-28d5-4938-8b81-bbf5eb30fc36" />
<img width="350" height="300" alt="Screenshot_20260807-202227" src="https://github.com/user-attachments/assets/74c48268-3037-4c81-9abe-400f0b790b9f" />
<img width="350" height="300" alt="Screenshot_20260807-202258" src="https://github.com/user-attachments/assets/b23fb273-9a10-4244-b2b5-f05003c48c4d" />
<p/>

> **Current status:** SeedlessDS `1.0.0-beta1` is a working Android beta tested on real dual-screen hardware. The app is built around the **DraStic r2.6.0.4a** core, while the new Android frontend, UI, library, input layer, dual-screen renderer, and RetroAchievements bridge are SeedlessDS work.
>
> The long-term goal is to publish and maintain the parts of the project that are ours, document the core boundary clearly, and move toward a clean, publishable emulation-core reimplementation over time. See [Roadmap](#roadmap).

---

## What it is

SeedlessDS is **not only a wrapper around an emulator core**. It is the complete Android experience around one:

- the library
- the settings
- the input system
- the save-state system
- the cheat system
- the RetroAchievements integration
- the dual-screen presentation layer
- the renderer that drives two physical panels as one handheld console

Emulation is currently done through the **DraStic r2.6.0.4a** core.

The goal is to keep the lightweight performance benefits that make DraStic useful on low-power handhelds, while adding a modern Android interface and features that match the direction of its sister project, **WatermelonDS**.

---

## Why SeedlessDS exists

WatermelonDS remains the long-term melonDS-based project.

However, some Android handhelds and retro consoles have limited chipset performance, and melonDS-based emulation can be too demanding for them today.

SeedlessDS exists as a lighter option for those devices.

It is especially useful for low-power Android handhelds and dual-screen devices where users want:

- smooth Nintendo DS performance
- physical controller support
- a controller-first UI
- RetroAchievements support
- dual-screen behavior designed for real two-panel hardware
- a visual style that feels connected to WatermelonDS

SeedlessDS does **not** replace WatermelonDS. It is a companion project for devices that need a lighter DS option today, while WatermelonDS continues to mature.

---

## The differentiator

Many Nintendo DS emulators on Android can show one screen on an external display.

SeedlessDS is designed to treat both panels as **one handheld console**.

### DualScreen Presets

DualScreen Presets let the app split the console layout across two panels with one shared rule for:

- scaling
- aspect ratio
- alignment
- screen position
- touch behavior

The goal is to make dual-screen devices feel intentional instead of hacked together.

### Synchronized dual-panel rendering

Unlike the original DraStic Android presentation, SeedlessDS is designed around a synchronized dual-panel renderer.

Both panels are driven from the same rendering flow so the top and bottom DS screens stay visually aligned when a game uses both screens as one continuous scene.

This is especially important in games such as **Sonic Rush**, where the two DS screens are often part of a single vertical playfield.

### Controller-first interface

SeedlessDS is designed so the full app can be navigated with a physical controller.

- D-pad navigation works across the UI.
- L1/R1 can move between tabs.
- The external panel can describe the item currently highlighted while navigating menus.
- Emulator shortcuts can be mapped to physical buttons.

---

## Features

### Library

- ROM folders
- Accent-insensitive search
- Filters for All, Favorites, DS, DSiWare, and RetroAchievements
- Continue Playing section with per-game save slots
- Box art from libretro's thumbnail repository
- Box art cached on disk
- Bundled thumbnail index for faster startup

### Emulation

- Save states with screenshots
- Slot menu
- Quick save and quick load
- Cheat database
- User cheat codes
- Global settings
- Per-game overrides
- Profiles
- On-screen control layout editor
- Screen layouts
- Image filters
- Integer scaling

### RetroAchievements

SeedlessDS includes RetroAchievements integration work through [`rcheevos`](https://github.com/RetroAchievements/rcheevos), pinned to **v12.3.0**.

Current RetroAchievements-related features include:

- achievements
- leaderboards
- profile card
- hardcore score display
- casual score display
- native memory bridge work for achievement support

RetroAchievements support is still being tested, so detailed feedback is welcome.

### Built for a gamepad

Every screen is designed to be usable with a physical controller.

Physical pads can map the twelve Nintendo DS buttons and emulator-specific functions such as:

- fast-forward
- quick save
- quick load
- swap screens
- cycle layouts
- microphone
- turbo

### Front-end friendly

SeedlessDS can be launched by Android intents, making it easier to integrate with launchers and front-ends.

Supported file extensions include:

- `.nds`
- `.dsi`
- `.srl`
- `.ids`

Example:

```bash
am start -n com.seedlessds.app/com.seedlessds.app.LaunchGame \
    -e rom_path /storage/emulated/0/roms/nds/game.nds \
    --ei load_slot 1
```

---

## How it is built

| Layer | Details |
|---|---|
| App | Kotlin + Jetpack Compose, Material 3 |
| Current emulation core | DraStic `r2.6.0.4a`, prebuilt `libdrastic_arm64.so`, reached over JNI |
| Own native code | `libdrastic_bridge.so`, CMake memory bridge for RetroAchievements |
| RetroAchievements | `rcheevos` v12.3.0 |
| Rendering | Hand-rolled EGL dual-panel rendering path |
| ABI | **arm64-v8a only** |
| Android | 5.0+ / API 21+ |
| Build target | API 35 |
| Current version | `1.0.0-beta1` |
| Version code | `109` |

A note on the JNI boundary: the prebuilt core resolves a handful of classes by their hardcoded fully qualified name. Those compatibility classes must keep living in the original package. The rest of the app is `com.seedlessds.app`.

---

## Roadmap

The dependency on a proprietary core is the one thing keeping this project from being open source end to end.

The plan, in order:

1. **Publish everything that is already ours**

   This includes the UI, the library, the settings, the input layer, the renderer, and the RetroAchievements bridge.

   None of these pieces need to derive from the core. They can be read, built, and modified given a compatible core boundary.

2. **Map and document the core surface**

   The JNI contract is small and already understood at a high level:

   - frame handoff
   - input
   - configuration
   - save states
   - screen buffers
   - filter pipeline
   - memory access needed for RetroAchievements

   Documenting it precisely is what makes a replacement possible.

3. **Move toward a clean, publishable reimplementation**

   The long-term goal is to replace the current proprietary core dependency with native code that can be read, built, modified, and redistributed openly.

   A reimplementation intended for publication has to be built from a **behavioral specification**: what the core does, observed from the outside, rather than decompiler output pasted into new files.

   Decompiled listings can help understand behavior, but they are not suitable for shipping as public source.

4. **Prepare a Linux version**

   A Linux version is planned for compatible low-power handhelds.

   Devices such as the **RG35XX SP** are part of the target direction for this work.

The long-term goal is to make SeedlessDS more open, more portable, and more useful across Android and Linux handhelds, while keeping the public code clean and suitable for release.

---

## Legal

- **DraStic** is the property of its original authors.
- SeedlessDS is not affiliated with, endorsed by, or connected to the original DraStic authors.
- Nintendo DS BIOS and firmware images are not included.
- Games are not included.
- Nintendo, Nintendo DS, and any referenced game titles are trademarks of their respective owners.
- This project is not affiliated with, endorsed by, or connected to Nintendo.
- This README does not grant permission to redistribute proprietary third-party binaries.

Any public source release should contain only code, assets, and documentation that can be published safely.

---

## Current known notes

- This is an early beta project.
- Only `arm64-v8a` Android devices are supported right now.
- RetroAchievements support is still being tested.
- Some games may have compatibility issues.
- Some UI areas may still need polish.
- Low-power handheld behavior may vary by device.
- Dual-screen behavior depends on the Android device and display implementation.
- Linux support is planned but not released yet.

---

## Reporting issues

When reporting issues, please include:

- device model
- Android version
- game title
- whether one screen or two screens were used
- whether RetroAchievements was enabled
- whether touch controls or a physical controller were used
- steps to reproduce
- screenshots or video, if possible
- logs, if available

---

## License

Not chosen yet.

The license will be picked before the first source code drop, and it will be an open-source license. This is part of the roadmap above.
