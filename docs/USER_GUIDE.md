# User guide

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

Set **Harvest style** under Content to **Icons**, **Text**, or **Harvestability**. All three styles use Iaspis's harvest checks. The Harvestability style uses the original symbols and layout for harvest status, Silk Touch and shearing, with separate lines for the effective tool and its tier. Iaspis suppresses the Waila Harvestability 1.1.0 tooltip provider to avoid duplicate hints when that add-on is installed.

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

Named sources share their Off / On / Only in detailed view mode and server lock with other extensions. They can supply input and output limits per tick. Client projections let an add-on follow each player's unit preference while keeping stored energy, capacity and limits consistent.

## Vanilla block details

Content settings control brewing progress, spawner mobs, hopper locks, note block pitch, jukebox records, and enchanting power. The brewing stand shows its current progress while brewing. Enchanting power counts the usable bookshelf bonus, up to the vanilla cap of 15. Install Iaspis on the multiplayer server to share brewing time, note pitch, the inserted record, and the hopper's lock state.

## Vanilla entity details

The HUD shows animal growth and breeding cooldowns, estimated horse speed and jump height, pet owners, and villager professions. It also shows status effect icons and durations, item frame contents, and dropped item stacks. Content settings control each detail and the maximum number of status effects shown. A multiplayer server with Iaspis supplies dropped item despawn times, active breeding status, and up-to-date mob effects.

## Mod compatibility

Iaspis provides WAILA 1.5.2a compatibility for integrations supplied by other mods. ForgeMultipart tooltips follow the part under the crosshair, including covers and conduits sharing a block. Generic inventory and tank previews also follow the selected part.

Extra machine information, such as stored energy and processing details, is supplied by add-ons. Install the corresponding add-on on both client and server for synchronized machine data.

Extension options appear under **Extensions**. Choose **Off**, **On**, or **Only in detailed view** for named providers. Server-controlled options are marked in the settings screen.

For supported integration hooks and configuration details, see the [extension API guide](EXTENSION_API.md).
