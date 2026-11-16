package io.github.vulpesstella.iaspis;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.common.Configuration;

public final class IaspisConfig {
    public enum DisplayMode {
        OFF, ON, DETAILS;

        public boolean shows(boolean details) {
            return this == ON || (this == DETAILS && details);
        }
    }

    public static final String TANK_DISPLAY = "tank.display";
    private static final String DISPLAY = "display";
    private static final String HUD_PROVIDERS = "hud_providers";
    private final Configuration file;
    private final Map<String, Boolean> booleans = new HashMap<String, Boolean>();

    IaspisConfig(File path) {
        file = new Configuration(path);
        file.load();
        if (file.hasCategory(DISPLAY)) {
            file.getCategory(DISPLAY).remove("machine.energy");
            file.getCategory(DISPLAY).remove("machine.energyBar");
            file.getCategory(DISPLAY).remove("machine.progress");
            file.getCategory(DISPLAY).remove("machine.details");
            save();
        }
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, true);
    }

    public synchronized boolean getBoolean(String key, boolean fallback) {
        Boolean cached = booleans.get(key);
        if (cached != null) return cached;
        boolean value = file.get(DISPLAY, key, fallback).getBoolean(fallback);
        booleans.put(key, value);
        save();
        return value;
    }

    public synchronized void setBoolean(String key, boolean value) {
        file.get(DISPLAY, key, value).set(value);
        booleans.put(key, value);
    }

    public synchronized int getInt(String key, int fallback, int minimum, int maximum) {
        int value = file.get(DISPLAY, key, fallback).getInt(fallback);
        return Math.max(minimum, Math.min(maximum, value));
    }

    public synchronized void setInt(String key, int value) {
        file.get(DISPLAY, key, value).set(value);
    }

    public DisplayMode getTankDisplay() {
        return DisplayMode.values()[getInt(TANK_DISPLAY, DisplayMode.ON.ordinal(), 0, DisplayMode.DETAILS.ordinal())];
    }

    public synchronized DisplayMode getHudProviderMode(String id) {
        int fallback = DisplayMode.ON.ordinal();
        if (!file.hasKey(HUD_PROVIDERS, id)) return DisplayMode.ON;
        int value = file.get(HUD_PROVIDERS, id, fallback).getInt(fallback);
        return value >= 0 && value < DisplayMode.values().length ? DisplayMode.values()[value] : DisplayMode.ON;
    }

    public synchronized void setHudProviderMode(String id, DisplayMode mode) {
        file.get(HUD_PROVIDERS, id, DisplayMode.ON.ordinal()).set(mode.ordinal());
        save();
    }

    public synchronized boolean isWailaOptionLocked(String key) {
        return file.hasKey("waila_server", key) && file.get("waila_server", key, false).getBoolean(false);
    }

    public synchronized void save() {
        if (file.hasChanged()) file.save();
    }
}
