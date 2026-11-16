package io.github.vulpesstella.iaspis.client;

import io.github.vulpesstella.iaspis.Iaspis;

enum PreviewSample {
    DEFAULT(""),
    HORIZONTAL("overlay.x"),
    VERTICAL("overlay.y"),
    SCALE("overlay.scale"),
    OPACITY("overlay.opacity"),
    THEME("overlay.theme"),
    MODE("overlay.mode"),
    VISIBILITY("overlay.enabled"),
    ANIMATION("overlay.animation"),
    BOSS_BAR("overlay.bossMode"),
    INVENTORY("inventory.preview"),
    HARVEST("harvest.enabled"),
    HEALTH("health.enabled"),
    ARMOR("armor.enabled"),
    GROWTH("growth.enabled"),
    FURNACE("furnace.enabled"),
    ICON("overlay.icon"),
    MOD_NAME("overlay.modName"),
    ANCHOR_X("overlay.anchorX"),
    ANCHOR_Y("overlay.anchorY"),
    AUTO_SCALE("overlay.autoScale"),
    LINGER("overlay.linger"),
    BLOCKS("overlay.blocks"),
    ENTITIES("overlay.entities"),
    BOSSES("overlay.bosses"),
    TAB_LIST("overlay.hideWithTab", false),
    INVENTORY_LIMIT("inventory.limit"),
    INVENTORY_DETAILS("inventory.detailLimit"),
    INVENTORY_COLUMNS("inventory.columns"),
    INVENTORY_NAMES("inventory.names"),
    INVENTORY_SORT("inventory.sort", false),
    HARVEST_EFFECTIVE("harvest.effective"),
    HARVEST_CREATIVE("harvest.creative"),
    HARVEST_UNBREAKABLE("harvest.unbreakable"),
    HARVEST_NEW_LINE("harvest.newLine"),
    HEALTH_LIMIT("health.iconLimit"),
    HEALTH_COLUMNS("health.columns"),
    HEALTH_FRACTION("health.fraction", false),
    ARMOR_LIMIT("armor.iconLimit"),
BREAKING("breaking.enabled"),
    FLUID_TARGET("fluid.target"),
    TANK("tank.preview"),
    TANK_DETAILS("tank.details", false),
    TANK_BAR("tank.bar"),
    PROCESSING("machine.progress"),
    PROCESSING_DETAILS("machine.details", false),
    BREWING("vanilla.brewing"),
    SPAWNER("vanilla.spawner"),
    HOPPER("vanilla.hopper"),
    NOTE_BLOCK("vanilla.note"),
    JUKEBOX("vanilla.jukebox"),
    ENCHANTING("vanilla.enchanting");

    final String key;
    final boolean defaultEnabled;

    PreviewSample(String key) {
        this(key, true);
    }

    PreviewSample(String key, boolean defaultEnabled) {
        this.key = key;
        this.defaultEnabled = defaultEnabled;
    }

    boolean enabled() {
        return Iaspis.config().getBoolean(key, defaultEnabled);
    }

    int buttonId() { return ordinal() - 1; }

    static PreviewSample forButton(int id) {
        return values()[id + 1];
    }

    boolean visible(long millis) {
        if (this == VISIBILITY || this == BLOCKS || this == ENTITIES || this == BOSSES) return enabled();
        if (this == FLUID_TARGET) {
            int mode = HudSettings.NumberOption.FLUID_TARGET.get();
            return mode == FluidTarget.ALL || (mode == FluidTarget.SOURCE && millis % 3200 < 1600);
        }
        if (this == TAB_LIST) return !enabled() || millis % 2800 < 1400;
        if (this == LINGER) return millis % 3600 < 1250 + HudSettings.NumberOption.LINGER.get();
        return this != MODE || HudSettings.NumberOption.MODE.get() != HudSettings.HOLD || millis % 2600 < 1800;
    }
}
