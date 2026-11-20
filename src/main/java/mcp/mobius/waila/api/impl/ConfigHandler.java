package mcp.mobius.waila.api.impl;

import io.github.vulpesstella.iaspis.compat.WailaBridge;
import java.util.HashMap;
import java.util.Set;
import mcp.mobius.waila.api.IWailaConfigHandler;

/** Compatibility entry point for legacy extensions that seed option defaults directly. */
public final class ConfigHandler implements IWailaConfigHandler {
    private static final ConfigHandler INSTANCE = new ConfigHandler();

    private ConfigHandler() {}

    public static ConfigHandler instance() {
        return INSTANCE;
    }

    @Override
    public boolean getConfig(String key, boolean defaultValue) {
        return WailaBridge.INSTANCE.getConfig(key, defaultValue);
    }

    @Override
    public boolean getConfig(String key) {
        return WailaBridge.INSTANCE.getConfig(key);
    }

    @Override
    public Set<String> getModuleNames() {
        return WailaBridge.INSTANCE.getModuleNames();
    }

    @Override
    public HashMap<String, String> getConfigKeys(String modName) {
        return WailaBridge.INSTANCE.getConfigKeys(modName);
    }
}
