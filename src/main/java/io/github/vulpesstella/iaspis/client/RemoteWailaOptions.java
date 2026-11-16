package io.github.vulpesstella.iaspis.client;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import io.github.vulpesstella.iaspis.compat.WailaOptionsChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.INetworkManager;

final class RemoteWailaOptions implements ITickHandler {
    private static final long RETRY = 1000000000L;
    private final Minecraft game = Minecraft.getMinecraft();
    private INetworkManager connection;
    private Map<String, Boolean> options;
    private long nextRequest;

    synchronized Boolean forced(String key) {
        INetworkManager current = currentConnection();
        if (current == null || !WailaOptionsChannel.available(game.thePlayer)) return null;
        if (connection != current || options == null) return Boolean.FALSE;
        return options.get(key);
    }

    synchronized void receive(INetworkManager manager, Map<String, Boolean> values) {
        if (currentConnection() == manager && manager == connection) {
            options = Collections.unmodifiableMap(values);
        }
    }

    @Override
    public synchronized void tickEnd(EnumSet<TickType> types, Object... data) {
        INetworkManager current = currentConnection();
        if (current != connection) {
            connection = current;
            options = null;
            nextRequest = 0;
        }
        if (connection == null || options != null || !WailaOptionsChannel.available(game.thePlayer)) return;
        long now = System.nanoTime();
        if (nextRequest == 0 || now - nextRequest >= 0) {
            nextRequest = now + RETRY;
            WailaOptionsChannel.request();
        }
    }

    private INetworkManager currentConnection() {
        return game.theWorld == null || game.thePlayer == null || game.getNetHandler() == null
                ? null : game.getNetHandler().getNetManager();
    }

    @Override
    public void tickStart(EnumSet<TickType> types, Object... data) {}

    @Override
    public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

    @Override
    public String getLabel() { return "Iaspis WAILA options"; }
}
