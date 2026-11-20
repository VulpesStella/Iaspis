# Iaspis

**A Jade-style information HUD for Minecraft 1.6.4.**

![Minecraft 1.6.4](https://img.shields.io/badge/Minecraft-1.6.4-62b47a)
![Forge 9.11.1.1345](https://img.shields.io/badge/Forge-9.11.1.1345-3b5b8a)
[![Build mod](https://github.com/VulpesStella/Iaspis/actions/workflows/build.yml/badge.svg)](https://github.com/VulpesStella/Iaspis/actions/workflows/build.yml)

[User guide](docs/USER_GUIDE.md) · [Extension API](docs/EXTENSION_API.md) · [Changelog](CHANGELOG.md) · [Report an issue](https://github.com/VulpesStella/Iaspis/issues)

Iaspis shows information about the block or entity under your crosshair. Inspired by [Jade](https://github.com/Snownee/Jade), it brings an illustrated, configurable HUD to Forge 1.6.4 and supports existing WAILA integrations.

## Features

- **Know what you are looking at.** Block and entity names, item icons, and mod names, with precise targeting for ForgeMultipart parts.
- **Check before you mine.** Tool icons, harvest tiers, and a check or cross for your held tool. Choose Icons, Text, or Harvestability style.
- **See contents and progress.** Item-stack previews, fluid tank bars, furnace slots and progress, and brewing progress.
- **Read entity details.** Health hearts, armor icons, status effects, animal growth, pet owners, and villager professions.
- **Make the HUD your own.** Position anchors, scale, background opacity, smooth transitions, and option previews in the settings screen.
- **Use your mod integrations.** WAILA-compatible providers, optional NEI recipe lookup, and add-on support for machine energy and other mod-specific details.

## Installation

Iaspis is in development and has no published release yet. You can [build it from source](#building) to try it.

1. Install **Minecraft 1.6.4** with **Forge 9.11.1.1345**.
2. Place the Iaspis JAR in your instance's `mods/` folder. If you use WAILA, replace it with Iaspis.
3. Launch the game and press **Numpad +** to open the settings.

Iaspis works on the client. Install it on the server as well for synchronized inventories, tanks, and additional block and entity details. Machine telemetry requires an add-on providing the corresponding integration.

Forge lists both **Iaspis** (`iaspis`) and **Iaspis WAILA Compatibility** (`Waila`). They are supplied by the same JAR.

## Controls

| Default key | Action |
| --- | --- |
| **Numpad +** | Open settings |
| **Numpad 1** | Toggle the HUD; can also be configured as a hold key |
| **Numpad 2** | Cycle fluid targeting: Off / Sources only / All fluids |
| **Numpad 3** | Hold for detailed information |
| **Numpad 4** | Look up recipes with NEI installed |
| **Numpad 5** | Look up uses with NEI installed |

All bindings can be changed in Minecraft's Controls menu. The [user guide](docs/USER_GUIDE.md) covers display modes, content options, narration, and multiplayer settings.

## For add-on developers

Use the [Extension API](docs/EXTENSION_API.md) to add text or visual components, read validated server snapshots, extend harvest checks, and supply machine data with client-side unit preferences. Existing mods can also register through the WAILA 1.5.2a API and startup IMC.

The [HUD example](examples/hud-provider) demonstrates a redstone signal component. See the [API guide](docs/EXTENSION_API.md#iaspis-hud-providers) for its build command, provider settings, and server locks.

## Building

On Windows:

```powershell
./setup.ps1
./build.ps1 build
```

On Linux:

```sh
./setup.sh
./build.sh build
```

The build uses Retrospectum Loom with Java 17 for Gradle, Java 8 for compilation, and a Java 7 API baseline. The setup scripts configure the required JDKs. Install the JAR from `build/libs/` that has no `-dev` suffix.

Run `./build.ps1 runClient` or `./build.ps1 runServer` for development launches; use `./build.sh` on Linux. Java formatting uses Spotless with Google Java Format's four-space AOSP style: run `spotlessApply` to format or `spotlessCheck` to check. Formatting is also checked by `build` and CI.

## Credits and license

Inspired by [Jade](https://github.com/Snownee/Jade) by Snownee. The WAILA API declarations come from [WAILA by ProfMobius](https://github.com/Choonster-Minecraft-Mods/ProfMobius-Waila/tree/d6de00542d58c8cadce2e00d27535e7b259c0ea7).

Iaspis is distributed under the [MIT License](LICENSE). The included WAILA declarations retain their [Apache License 2.0](src/main/resources/WAILA-LICENSE.txt).
