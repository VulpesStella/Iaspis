package io.github.vulpesstella.iaspis.client;

import io.github.vulpesstella.iaspis.Iaspis;

final class HudSettings {

    enum NumberOption {
        X("overlay.x", 50, 0, 100, "%"),
        Y("overlay.y", 0, 0, 100, "%"),
        SCALE("overlay.scale", 100, 50, 200, "%"),
        OPACITY("overlay.opacity", 87, 0, 100, "%"),
        THEME("overlay.theme", 0, 2),
        MODE("overlay.mode", 0, 2),
        ICON("overlay.icon", 0, 2),
        ANCHOR_X("overlay.anchorX", 0, 3),
        ANCHOR_Y("overlay.anchorY", 0, 3),
        AUTO_SCALE("overlay.autoScale", 100, 30, 100, "%"),
        LINGER("overlay.linger", 100, 0, 2000, " ms"),
        BOSS_MODE("overlay.bossMode", 0, 2),
        INVENTORY_LIMIT("inventory.limit", 9, 0, 27, ""),
        INVENTORY_DETAILS("inventory.detailLimit", 27, 0, 27, ""),
        INVENTORY_COLUMNS("inventory.columns", 9, 1, 18, ""),
        INVENTORY_NAMES("inventory.names", 0, 0, 9, ""),
        HEALTH_LIMIT("health.iconLimit", 40, 0, 200, ""),
        HEALTH_COLUMNS("health.columns", 10, 1, 20, ""),
        ARMOR_LIMIT("armor.iconLimit", 20, 0, 200, ""),
        FLUID_TARGET("fluid.target", FluidTarget.OFF, FluidTarget.ALL),
        EFFECT_LIMIT("vanilla.effectLimit", 5, 0, 12, "");

        final String key;
        final int fallback;
        final int min;
        final int max;
        final String unit;
        private int value;

        NumberOption(String key, int fallback, int max) {
            this(key, fallback, 0, max, null);
        }

        NumberOption(String key, int fallback, int min, int max, String unit) {
            this.key = key;
            this.fallback = fallback;
            this.min = min;
            this.max = max;
            this.unit = unit;
        }

        int get() { return value; }

        boolean isSlider() { return unit != null; }

        static NumberOption forKey(String key) {
            for (NumberOption option : values()) {
                if (option.key.equals(key)) return option;
            }
            return null;
        }

        void set(int value) {
            this.value = Math.max(min, Math.min(max, value));
            Iaspis.config().setInt(key, this.value);
        }
    }

    static final int TOGGLE = 0;
    static final int HOLD = 1;
    static final int LITE = 2;
    static final int ICON_INLINE = 1;
    static final int ICON_HIDDEN = 2;
    static final int BOSS_MOVE = 0;
    static final int BOSS_HIDE = 1;
    static final int BOSS_OVERLAP = 2;

    static void load() {
        for (NumberOption option : NumberOption.values()) {
            int fallback = option == NumberOption.BOSS_MODE && !Iaspis.config().getBoolean("overlay.avoidBossBar")
                    ? BOSS_OVERLAP : option.fallback;
            option.value = Iaspis.config().getInt(option.key, fallback, option.min, option.max);
        }
        Iaspis.config().save();
    }

    static int background() {
        int theme = NumberOption.THEME.get();
        int rgb = theme == 1 ? 0x121212 : theme == 2 ? 0x18301f : 0x12121c;
        return (Math.round(NumberOption.OPACITY.get() * 2.55F) << 24) | rgb;
    }

    static int accent() {
        int theme = NumberOption.THEME.get();
        int rgb = theme == 1 ? 0xb0b0b0 : theme == 2 ? 0x72ae81 : 0x8c78b8;
        return (Math.round(NumberOption.OPACITY.get() * 2.55F) << 24) | rgb;
    }

    private HudSettings() {}
}
