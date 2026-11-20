# Extension API

Import public extension types from `io.github.vulpesstella.iaspis.api`. Register providers during mod initialization, and register HUD providers on the client. Callbacks read game state and must not modify the world, player, tile, entity, or selected part. Return promptly; HUD callbacks can run every frame.

## Capability version

The APIs below require extension API **1**, exposed by `HudProviders.getApiVersion()`. The method returns the capability level of the running Iaspis build. `API_VERSION` is also available for compile-time use. Earlier builds without these APIs do not expose the method. Add-ons that support those builds can check for it reflectively before loading classes that reference the new types:

```java
boolean supportsApi1;
try {
    Class<?> api = Class.forName("io.github.vulpesstella.iaspis.api.HudProviders");
    supportsApi1 = ((Integer) api.getMethod("getApiVersion").invoke(null)).intValue() >= 1;
} catch (Exception e) {
    supportsApi1 = false;
} catch (LinkageError e) {
    supportsApi1 = false;
}
```

Compile against an API 1 build and install that build on the client. Existing `HudTarget` implementations and WAILA interfaces retain their original methods, so old providers remain binary compatible.

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
