# ClutchMod — Minecraft 1.8.9

> Advanced utility mod with **Clutch** and **Silent Aim** for Minecraft 1.8.9 Forge.

---

## Requirements

| Tool | Version |
|---|---|
| Minecraft Forge | 1.8.9 – 11.15.1.2318 |
| SpongePowered Mixin | 0.7.11-SNAPSHOT |
| Java | 8 (JDK 8) |
| Gradle | 2.14+ |

---

## Build

```bash
# 1. Install ForgeGradle wrapper
./gradlew setupDecompWorkspace

# 2. Build the mod JAR
./gradlew build

# Output: build/libs/ClutchMod-1.0.0.jar
```

---

## Install

1. Install **Minecraft Forge 1.8.9** (`11.15.1.2318`)
2. Drop `ClutchMod-1.0.0.jar` into `.minecraft/mods/`
3. Launch the game

---

## Usage

| Action | Effect |
|---|---|
| Press **Right Shift** | Opens the ClutchMod menu |
| Click a toggle once | **Enables** the option |
| Click a toggle again | **Disables** the option |

---

## Source Layout

```
src/main/java/com/clutchmod/
├── ClutchMod.java                         ← Forge mod entry point
├── event/
│   ├── KeyInputHandler.java               ← Right Shift → open menu
│   ├── TickHandler.java                   ← Per-tick module updates
│   └── RenderHandler.java                 ← Block-count HUD overlay
├── gui/
│   └── ModMenuScreen.java                 ← Full dark-theme GUI
├── mixins/
│   ├── ClutchCoreMod.java                 ← FML CoreMod plugin
│   ├── MixinNetHandlerPlayClient.java     ← Silent Aim packet injection
│   └── MixinEntityPlayerSP.java           ← PROPER movement correction
└── modules/
    ├── combat/
    │   └── SilentAimModule.java           ← Server-side angle spoofing
    └── movement/
        └── ClutchModule.java              ← Block-placing fall-saver
```

---

## Module Details

### Clutch
Automatically places blocks beneath your feet when:
- You are above the **void** with nothing below
- Fall damage would be **lethal** at current distance
- You have fallen more than **X blocks** (configurable)

Settings: Silent Aim delegation, block count HUD, angle reset, slot return, staircase jump, move delay, max blocks cap, blacklist/whitelist filter.

### Silent Aim
Sends server-side yaw/pitch packets while keeping your visual angles unchanged.

- **PROPER** — anticheat-safe; movement direction matches server angles
- **NONE** — no correction (unsafe, flagged by most ACs)
- **SLOW** — gradual visual lerp (still unsafe)

Advanced: Reach integration, Hitboxes integration (both require separate modules).
