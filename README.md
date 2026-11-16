# Iaspis

Iaspis brings Jade-style tooltips to Minecraft 1.6.4. Look at a block or entity to see what it is, which mod it belongs to, and details such as entity health, crop growth, and furnace progress.

Entity health uses full, half, and empty hearts. Armor uses armor icons. Large values use an icon with a compact number, and furnaces show input and fuel slots, a progress arrow, and an output slot.

## Installation

1. Install Minecraft Forge 9.11.1.1345 for Minecraft 1.6.4.
2. Remove WAILA from the instance and place the Iaspis release JAR in `mods/`.
3. Press **Numpad 0** in game to open Iaspis settings and **Numpad 1** to control the overlay. Change these bindings in Controls.

Forge lists **Iaspis** (`iaspis`) and **Iaspis WAILA Compatibility** (`Waila`). Both entries come from the same JAR. Install Iaspis on the server as well to enable server-side target data.

The settings screen groups options under Style, Behavior and Content. Hover over an option to see an example in the live preview. The option list scrolls independently. Toggle mode keeps the overlay on until you switch it off. Hold mode shows it while you hold the overlay key. Lite mode shows the target name and source, with full details while you hold Sneak.

The overlay fades in when you acquire a target, resizes when you switch targets, and briefly lingers before fading out. You can adjust the linger time, choose which targets appear, and set how the HUD behaves around the boss bar or player list. Appearance options include target icon layout, mod name, position anchors, and auto-scaling for tall tooltips.

## Harvesting and inventory

The HUD shows tools that can harvest the target or speed up breaking it. When drops depend on your held tool, a green check or red cross shows whether you can harvest them. Tiers above diamond use a diamond tool icon with a numeric badge. Hold your Sneak key (Left Shift by default) to expand container previews. The default limits are 9 stacks normally and 27 in detail mode. You can change both limits, set the number of items per row, sort by stack size, and show item names for small inventories.

While you break a block, a thin bar at the bottom of the HUD shows your progress. It turns red if your tool cannot harvest the block. You can disable it under Content.

Container previews show item icons and stack counts. In multiplayer, install Iaspis on the server for containers whose contents are unavailable to the client. The `inventory.preview` setting in `config/iaspis.cfg` controls previews on the client and whether the server shares inventory data. Use `harvest.enabled` to toggle harvest hints.

## Fluids

Press **Numpad 2** to cycle fluid targeting through Off, Sources only, and All fluids. You can change the mode under Behavior and rebind the key in Controls.

For blocks that expose tanks through Forge, the HUD shows the fluid name, stored amount and capacity in mB, and a fill bar. Use Content settings to turn off the bar or show tanks only while holding Sneak. Multiplayer tank previews require Iaspis on the server. Server owners can turn off tank data sharing with `tank.preview` in `config/iaspis.cfg`.

## Machine data

Add-ons can register machine data with `MachineProviders.register` during common mod initialization. A provider supplies stored energy and capacity in its native unit, processing progress, or both. Content settings control the processing arrow and whether it appears only while holding Sneak. Multiplayer machine previews require the provider and Iaspis on the server and client.

## Vanilla block details

Content settings control brewing progress, spawner mobs, hopper locks, note block pitch, jukebox records, and enchanting power. The brewing stand shows its current progress while brewing. Enchanting power counts the usable bookshelf bonus, up to the vanilla cap of 15. Install Iaspis on the multiplayer server to share brewing time, note pitch, the inserted record, and the hopper's lock state.

## WAILA integrations

Iaspis implements the startup IMC and block and entity provider interfaces from WAILA 1.5.2a. Existing integrations can send `register` and `addconfig` messages to `Waila`. Block and entity providers can supply tooltip text, override display targets, and request selected NBT keys. Block decorators can draw in the world. Provider options are available in `config/iaspis.cfg`.

Forge Multipart-specific providers, WAILA documentation hooks, and server-locked configuration are outside the current compatibility scope.

## Building

The Retrospectum Loom build uses Java 17 for Gradle, Java 8 for compilation, and a Java 7 API baseline. Run `./setup.ps1` followed by `./build.ps1 build` on Windows, or `./setup.sh` and `./build.sh build` on Linux. Use the JAR in `build/libs/` whose name ends in the version number before `.jar`.

Run `./build.ps1 runClient` or `./build.ps1 runServer` for development launches. On Linux, use `./build.sh` with the same tasks.

## Acknowledgments

Iaspis is inspired by [Jade](https://github.com/Snownee/Jade). The legacy API declarations come from [WAILA by ProfMobius](https://github.com/Choonster-Minecraft-Mods/ProfMobius-Waila/tree/d6de00542d58c8cadce2e00d27535e7b259c0ea7) and are distributed under the [Apache License 2.0](src/main/resources/WAILA-LICENSE.txt).
