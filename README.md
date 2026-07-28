# Virulent Client

A Minecraft 1.21.4 utility client built on Fabric.

## Requirements

- **JDK 21** (required for Minecraft 1.21.4)
- **Fabric Loader** + **Fabric API** (included as dev dependencies)

## Setup

1. Install [JDK 21](https://adoptium.net/) and ensure `java` is on your PATH.
2. Open this folder in IntelliJ IDEA (import the Gradle project).
3. Run the client:

```powershell
.\gradlew.bat runClient
```

On first launch, a **Microsoft login dialog** will appear — follow the on-screen steps to link your account. After that, you'll have your real username and skin in dev. Tokens are cached in `~/.devlogin/` so you usually only log in once.

4. Build a release JAR:

```powershell
.\gradlew.bat build
```

The mod JAR will be in `build/libs/`.

## In-Game

| Key | Action |
|-----|--------|
| **Right Shift** | Open / close Click GUI |
| **Left click** (in GUI) | Toggle module |
| **Right click** (in GUI) | Expand module settings |
| **Left click** (on setting) | Cycle setting value |

## Modules

| Module | Category | Description |
|--------|----------|-------------|
| KillAura | Combat | Auto-attacks nearby entities |
| Sprint | Movement | Auto-sprint while moving |
| Flight | Movement | Survival flight |
| Fullbright | Render | Max gamma |
| ESP | Render | Box / tracer ESP |

Config is saved to `.minecraft/virulent/config.json`.

## Project Structure

```
src/client/java/dev/virulent/client/
├── VirulentClient.java      # Entry point
├── event/                   # Event bus
├── module/                  # Module system + modules
├── setting/                 # Module settings
├── gui/clickgui/            # Click GUI
├── gui/hud/                 # Arraylist HUD
├── config/                  # JSON config persistence
├── util/                    # Render helpers
└── mixin/                   # Keyboard hooks
```

## Why Fabric?

For Minecraft 1.21.4, a mod loader (Fabric) is the practical way to hook into the game. Alternatives like JNI injection (DLL-based clients) are possible but require C++/Rust, manual obfuscation mapping, and an injector — significantly more complex to develop and maintain.
