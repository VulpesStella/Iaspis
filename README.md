# Iaspis

Iaspis brings Jade-style tooltips to Minecraft 1.6.4. Look at a block or entity to see what it is, which mod it belongs to, and details such as entity health, crop growth, and furnace progress.

Entity health uses full, half, and empty hearts. Armor uses armor icons. Large values use an icon with a compact number, and furnaces show input and fuel slots, a progress arrow, and an output slot.

## Installation

1. Install Minecraft Forge 9.11.1.1345 for Minecraft 1.6.4.
2. Remove WAILA from the instance and place the Iaspis release JAR in `mods/`.
3. Press **Numpad +** in game to open Iaspis settings and **Numpad 1** to control the overlay. Change these bindings in Controls.

Forge lists **Iaspis** (`iaspis`) and **Iaspis WAILA Compatibility** (`Waila`). Both entries come from the same JAR. Install Iaspis on the server as well to enable server-side target data.

The settings screen groups options under Style, Behavior and Content. Hover over an option to see an example in the live preview. The option list scrolls independently. Toggle mode keeps the overlay on until you switch it off. Hold mode shows it while you hold the overlay key. Lite mode shows the target name and source. Hold **Numpad 3** for full details, or change the Iaspis details binding in Controls.

The overlay fades in when you acquire a target, resizes when you switch targets, and briefly lingers before fading out. You can adjust the linger time, choose which targets appear, and set how the HUD behaves around the boss bar or player list. Appearance options include target icon layout, mod name, position anchors, and auto-scaling for tall tooltips.

## Harvesting and inventory

The HUD shows tools that can harvest the target or speed up breaking it. When drops depend on your held tool, a green check or red cross shows whether you can harvest them. Tiers above diamond use a diamond tool icon with a numeric badge. Hold the Iaspis details key to expand container previews. The default limits are 9 stacks normally and 27 in detail mode. You can change both limits, set the number of items per row, sort by stack size, and show item names for small inventories.

While you break a block, a thin bar at the bottom of the HUD shows your progress. It turns red if your tool cannot harvest the block. You can disable it under Content.

Container previews show item icons and stack counts. In multiplayer, install Iaspis on the server for containers whose contents are unavailable to the client. The `inventory.preview` setting in `config/iaspis.cfg` controls previews on the client and whether the server shares inventory data. Use `harvest.enabled` to toggle harvest hints.

## Fluids

Press **Numpad 2** to cycle fluid targeting through Off, Sources only, and All fluids. You can change the mode under Behavior and rebind the key in Controls.

For blocks that expose tanks through Forge, the HUD shows the fluid name, stored amount and capacity in mB, and a fill bar. In Content settings, set **Tank contents** to Off, On, or Only in detailed view. The last mode shows tanks while you hold the details key. Multiplayer tank previews require Iaspis on the server. Server owners can set `I:tank.display=0` in `config/iaspis.cfg` to turn off tank data sharing.

## Lookup and narration

With [NotEnoughItems for 1.6.4](https://www.curseforge.com/minecraft/mc-mods/notenoughitems/files/2207574), aim at a block, dropped item, or item in a frame and press **Numpad 4** for recipes or **Numpad 5** for uses. Both bindings are available in Controls.

Enable **Narrate target names** under Behavior to read the selected target aloud. Narration uses the system speech voice on Windows or macOS and `espeak-ng` or `espeak` on Linux. Available languages depend on installed voices.

## Machine data

Add-ons can register machine data with `MachineProviders.register` during common mod initialization. A provider supplies stored energy and capacity in its native unit, processing progress, or both. Multiplayer machine previews require the provider and Iaspis on the server and client.

## Vanilla block details

Content settings control brewing progress, spawner mobs, hopper locks, note block pitch, jukebox records, and enchanting power. The brewing stand shows its current progress while brewing. Enchanting power counts the usable bookshelf bonus, up to the vanilla cap of 15. Install Iaspis on the multiplayer server to share brewing time, note pitch, the inserted record, and the hopper's lock state.

## Vanilla entity details

The HUD shows animal growth and breeding cooldowns, estimated horse speed and jump height, pet owners, and villager professions. It also shows status effect icons and durations, item frame contents, and dropped item stacks. Content settings control each detail and the maximum number of status effects shown. A multiplayer server with Iaspis supplies dropped item despawn times, active breeding status, and up-to-date mob effects.

## WAILA integrations

Iaspis implements the startup IMC and provider interfaces from WAILA 1.5.2a. Existing integrations can send `register` and `addconfig` messages to `Waila`. Block and entity providers can supply tooltip text, override display targets, and request selected NBT keys. Block decorators can draw in the world. With ForgeMultipart installed, part providers and decorators are called for the parts in the targeted block.

Options registered by extensions appear under **WAILA extensions** in Iaspis settings and are saved in `config/iaspis.cfg`. Options registered with `addConfigRemote` require Iaspis on the server. To enforce a registered option, set its value in the server’s `display` category and set the same key to `true` in the `waila_server` category. Clients show that setting as server-controlled.

WAILA documentation and item summary hooks are outside the current compatibility scope.

## Iaspis HUD providers

Add-ons can register a client-side provider with `io.github.vulpesstella.iaspis.api.HudProviders`. Register during client initialization. The target type can be a block, tile entity, entity, or an interface implemented by one of them. Install the add-on on the client to show its tooltip changes. Providers work with client-side target data.

```java
HudProviders.register(MyMachineTile.class, 100, new HudProviders.Provider() {
    @Override
    public void update(HudTarget target, boolean detailed, HudProviders.Tooltip tooltip) {
        if (detailed) tooltip.addLine("Machine serial: " + ((MyMachineTile) target.getTileEntity()).serial());
    }
});
```

Import `HudProviders` and `HudTarget` from `io.github.vulpesstella.iaspis.api`. The highest-priority provider that supplies a name or icon wins that override. Body lines from every provider appear in priority order below the standard details. Equal priorities follow registration order. `hide()` suppresses the HUD for the current target immediately.

Use `tooltip.addComponent(component)` to add a visual row. Implement `HudComponent` in the same package and report its width and height in GUI pixels. Draw inside that rectangle from the supplied top-left coordinates. Iaspis lays out component rows in provider order above the inventory grid. Components appear in full and detailed tooltips.

## Building

The Retrospectum Loom build uses Java 17 for Gradle, Java 8 for compilation, and a Java 7 API baseline. Run `./setup.ps1` followed by `./build.ps1 build` on Windows, or `./setup.sh` and `./build.sh build` on Linux. Use the JAR in `build/libs/` whose name ends in the version number before `.jar`.

Run `./build.ps1 runClient` or `./build.ps1 runServer` for development launches. On Linux, use `./build.sh` with the same tasks.

## Acknowledgments

Iaspis is inspired by [Jade](https://github.com/Snownee/Jade). The legacy API declarations come from [WAILA by ProfMobius](https://github.com/Choonster-Minecraft-Mods/ProfMobius-Waila/tree/d6de00542d58c8cadce2e00d27535e7b259c0ea7) and are distributed under the [Apache License 2.0](src/main/resources/WAILA-LICENSE.txt).
