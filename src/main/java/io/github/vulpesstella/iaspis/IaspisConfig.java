package io.github.vulpesstella.iaspis;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.common.Configuration;

public final class IaspisConfig {
    private static final String DISPLAY = "display";
    private final Configuration file;
    private final Map<String, Boolean> booleans = new HashMap<String, Boolean>();

    IaspisConfig(File path) {
        file = new Configuration(path);
        file.load();
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

    public synchronized void save() {
        if (file.hasChanged()) file.save();
    }
}
