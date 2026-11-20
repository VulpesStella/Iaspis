# Extension API

Import public extension types from `io.github.vulpesstella.iaspis.api`. Register providers during mod initialization, and register HUD providers on the client. Callbacks read game state and must not modify the world, player, tile, entity, or selected part. Return promptly; HUD callbacks can run every frame.

## Capability version

All extensions described here belong to API **1**. `HudProviders.getApiVersion()` returns `1`; `API_VERSION` is also available for compile-time use. Compile and run the add-on with the current Iaspis build.

## Selected multipart

`MultipartTarget` is an optional extension of `HudTarget`. Its `getMultipartPart()` returns a `MultipartPart`, or `null` for an ordinary target or an unsupported hit. The descriptor provides the part type ID, current list index, actual part object and a copied NBT snapshot saved directly by that part. The snapshot is local game state; it does not synchronize server-only fields.

```java
HudProviders.registerMultipart("example:conduit", "example.hud.conduit",
        "example_conduit", 100, new HudProviders.Provider() {
    @Override
    public void update(HudTarget target, boolean detailed, HudProviders.Tooltip tooltip) {
        MultipartPart part = ((MultipartTarget) target).getMultipartPart();
        if (detailed) tooltip.addLine("Mode: " + part.getSnapshot().getInteger("mode"));
    }
});
```

`registerMultipart` uses the same Off / On / Only in detailed view settings and server locks as other named HUD registrations. The existing class-based `register` overloads also match the selected part object. Register a concrete part class only after checking that its mod is installed. Registration by type ID does not require that mod's classes.

Treat descriptors and indexes as frame-local. `sameSelection` compares the part instance, index and type. A retained target comparison must also include world, tile and block position. Removing or replacing a part or reordering its runtime list invalidates the selection. Unknown hit layouts and invalid indexes return `null` without selecting an arbitrary part. Reflection currently supports ForgeMultipart 1.0.1.268 with CodeChickenLib 1.0.0.62.

Generic tank and inventory previews read capabilities from the selected part. They do not read the aggregate multipart container. Preview requests include the metadata and part selection, which the server validates against its own sight line before publishing data. Unrecognized multipart hits expose no generic contents.

Legacy WAILA FMP body, header, footer and decorator dispatch uses this same selection. `IWailaFMPAccessor.getNBTData()` contains the selected part's local saved snapshot. `getFullNBTData()` retains the full tile NBT, including server data when available. Full tile NBT ordering does not determine the selected part. Keep using an add-on's synchronization channel for fields unavailable locally.

## Harvest providers

`HarvestProviders.Provider` reads a `Context` and returns `Changes`, or `null` to defer. The context contains the target and a defensive copy of the held item. Target access supplies world, metadata, coordinates and player. Requirements and capabilities use a separate level for each tool class.

This synthetic example treats iron ore metadata 0 as requiring a level 2 pickaxe and other metadata as requiring a level 2 axe. A held stick represents a tool with pickaxe level 3 and axe level 1, so it passes only the first requirement:

```java
HarvestProviders.register(100, new HarvestProviders.Provider() {
    @Override
    public HarvestProviders.Changes read(HarvestProviders.Context context) {
        HudTarget target = context.getTarget();
        if (target.getBlock() != Block.oreIron) return null;
        String required = target.getMetadata() == 0 ? "pickaxe" : "axe";
        ItemStack held = context.getHeldItem();
        List<HarvestProviders.Tool> abilities = Collections.emptyList();
        if (held != null && held.getItem() == Item.stick) {
            abilities = Arrays.asList(new HarvestProviders.Tool("pickaxe", 3),
                    new HarvestProviders.Tool("axe", 1));
        }
        return new HarvestProviders.Changes(
                Arrays.asList(new HarvestProviders.Tool(required, 2)),
                Boolean.TRUE, abilities, null);
    }
});
```

Import `Block`, `Item`, and `ItemStack` from Minecraft and `List`, `Collections`, and `Arrays` from `java.util`. Real integrations supply their own target requirements and held-item abilities. A `Tool` can include an optional `ItemStack` icon; known vanilla classes otherwise use built-in icons. Unknown classes use their translated `iaspis.harvest.tool.<class>` name when no icon is supplied. Level `-1` represents an unlevelled class.

Higher priorities run first. Equal priorities preserve registration order. Each field in `Changes` independently uses the first non-null value. Provider exceptions discard that callback's contribution and allow lower priorities and native behavior to continue.

1. Native requirements come from Forge harvest levels, vanilla tool effectiveness and shearability. Non-null `requirements` and `requiresTool` replace their corresponding inputs.
2. Native Forge `canHarvestBlock` supplies the initial conclusion. When a provider supplies `capabilities` for a tool-required target with listed requirements, a matching class with a sufficient independent level permits harvesting. Requirements are alternatives. An empty capability list matches none.
3. Non-null `harvestable` explicitly overrides that conclusion. Unbreakable blocks and vanilla dead bushes / vines still enforce their breakability and required-shears constraints.
4. The target caches one resolved result for the frame. Icons, text, Harvestability status and breaking-bar color use that result. Silk Touch and shearability symbols retain their separate native checks.

Supplying requirements alone changes the displayed requirement; supply capabilities or a final conclusion when the held tool needs custom matching. Providers that defer leave native Forge behavior intact.

## Contextual level names

```java
HarvestProviders.registerLevelNames(100, new HarvestProviders.LevelNames() {
    @Override
    public String getName(HarvestProviders.Context context, String toolClass, int level) {
        if (context.getTarget().getBlock() != Block.oreIron) return null;
        return "pickaxe".equals(toolClass) && level == 2 ? "Example tier" : null;
    }
});
```

Return `null` to defer. Iaspis tries lower priorities, then `iaspis.harvestability.tier.<level>`, then a numeric fallback. Contextual names appear in text and Harvestability tier lines. The icon style retains tool icons and numeric badges above diamond. Name mappings should check the owning target or tool so that one mod's tier numbering does not rename another mod's tiers.

## Server detail snapshots

Register a source on both sides during common initialization. Its callback runs on the server after target validation. Class matching covers blocks, tiles, interfaces and the selected multipart object; `registerMultipart` matches a selected part type ID without linking to its classes.

```java
DetailProviders.register("example:temperature", "example.hud.temperature",
        MyMachineTile.class, 100, new DetailProviders.Provider() {
    @Override
    public NBTTagCompound read(HudTarget target) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("temperature", ((MyMachineTile) target.getTileEntity()).temperature());
        return data;
    }
});
```

Return only the fields needed for display. Returning `null` allows a lower-priority source with the same ID to supply the snapshot. Exceptions are isolated. The generic layer does not interpret business fields or copy tile inventories automatically.

Register the client HUD callback under the same ID to share its setting:

```java
HudProviders.register("example:temperature", "example.hud.temperature",
        MyMachineTile.class, 100, new HudProviders.Provider() {
    @Override
    public void update(HudTarget target, boolean detailed, HudProviders.Tooltip tooltip) {
        NBTTagCompound data = DetailProviders.get(target, "example:temperature");
        if (data != null) tooltip.addLine("Temperature: " + data.getInteger("temperature"));
    }
});
```

`DetailProviders.get` returns a defensive copy or `null` when data is unavailable. Core targets also implement the optional `SnapshotTarget` interface. A server-only field can be absent before the first response, when the provider is disabled, after expiration, or when Iaspis or the source is not installed on the server. Keep client-visible fallback text appropriate for missing data.

Display settings use the existing **Extensions** modes and `hud_providers_server` locks. Off stops requests for that source. Only in detailed view requests and displays its data while the player holds the details key. Server-side configured modes also restrict publishing. An add-on's startup integration switch still controls whether it registers the source.

The negotiated `IaspisDetails` channel carries detail and machine snapshots. Requests identify at most 32 sources and carry the dimension, position, metadata and selected part index/type. The server checks loaded chunks, an eight-block distance bound and the player's real sight line, then matches each registered source again. Multipart selection mismatches are rejected. Provider IDs are namespaced and limited to 64 characters. Responses are bounded to 30,000 bytes; an oversized snapshot is omitted.

Clients request updates at most every 250 ms, retain data for at most one second, and wait for a pending response before retrying, with a one-second timeout. The server limits this channel to one request per 150 ms per player. Selection, metadata, tile, world, connection or requested-mode changes invalidate the cache and pending sequence. Late responses cannot populate a newer selection. This API currently synchronizes block/tile targets and selected parts.

## Named machine sources

`MachineProviders` and `MachinePreview` are in `io.github.vulpesstella.iaspis.compat`. Class-based registration and a four-argument preview constructor are also available. A named registration also accepts interfaces and exposes a display setting:

```java
MachineProviders.register("example:machine_energy", "example.hud.machine_energy",
        MyMachineTile.class, 100, new MachineProviders.Provider() {
    @Override
    public MachinePreview read(TileEntity tile) {
        MyMachineTile machine = (MyMachineTile) tile;
        return new MachinePreview(MachinePreview.EnergyUnit.MJ,
                machine.stored(), machine.capacity(), machine.progress(),
                machine.maxInput(), machine.maxOutput());
    }
});
```

The optional final two values are maximum input and output per tick, expressed in the same native energy unit. Use `-1` when unknown. The core HUD displays supplied limits alongside the machine details. Existing four-argument previews have no limits.

The nearest matching concrete tile class wins. Equal class distances use priority, then registration order. Specific class sources precede interface sources; interface sources use priority and registration order. Unnamed `TileEntity.class` registrations are a final fallback. Named sources require a specific tile class or interface. Disabling the selected source also suppresses its machine data; a generic source does not replace a disabled specific source.

Register on both sides so the client can recognize the machine and its ID. The associated Off / On / Only in detailed view setting and server lock apply to energy, progress, limits and their requests. A named source can share its ID with ordinary HUD and detail providers. Unnamed registrations have no separate display setting. Machine snapshots carry their source ID and optional tick limits.

## Client energy projections

Register a projection on the client for a named machine source. It reads the local player's unit preference and receives the original immutable server preview:

```java
MachineProjections.register("example:machine_energy", 100, new MachineProjections.Projection() {
    @Override
    public MachineProjections.Conversion project(HudTarget target, MachinePreview serverValue) {
        if (ClientConfig.showRf && serverValue.unit == MachinePreview.EnergyUnit.MJ) {
            return new MachineProjections.Conversion(MachinePreview.EnergyUnit.RF, 10);
        }
        if (!ClientConfig.showRf && serverValue.unit == MachinePreview.EnergyUnit.RF) {
            return new MachineProjections.Conversion(MachinePreview.EnergyUnit.MJ, 0.1);
        }
        return null;
    }
});
```

Return `null` to try lower priorities or preserve the received value. A conversion changes stored energy, capacity, input and output limits together. Progress is unchanged, and the energy bar keeps the same fill ratio. Every read starts from the original server value, so repeated rendering does not compound the conversion. Invalid conversions and callback exceptions fall back safely. The core does not define conversions between RF, MJ and EU; the add-on supplies the rule for its own source.

Core targets implement the optional `MachineTarget` interface. `getMachinePreview()` returns the client projection, and `getMachineProviderId()` identifies its source. Additional HUD rows should read this value to use the same unit as the core energy display. The game object's actual energy storage and server snapshot are unchanged.
