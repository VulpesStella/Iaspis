package io.github.vulpesstella.iaspis.compat;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.IaspisConfig.DisplayMode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;

public final class WailaOptionsChannel {
    public static final String NAME = "IaspisOptions";
    private static final int REQUEST = 0;
    private static final int WAILA_LOCKS = 1;
    private static final int HUD_PROVIDER_LOCKS = 2;
    private static final int MAX_KEYS = 128;
    private static final int MAX_KEY_LENGTH = 64;
    private static final int MAX_BYTES = 25000;

    public static boolean available(EntityPlayer player) {
        return NetworkRegistry.instance().isChannelActive(NAME, (Player) player);
    }

    public static void request() {
        PacketDispatcher.sendPacketToServer(new Packet250CustomPayload(NAME, new byte[] {REQUEST}));
    }

    static void receive(INetworkManager manager, Packet250CustomPayload packet, Player player)
            throws IOException {
        if (packet.data == null || packet.data.length == 0 || packet.data.length > MAX_BYTES)
            return;
        if (player instanceof EntityPlayerMP) {
            if (packet.data.length == 1
                    && packet.data[0] == REQUEST
                    && available((EntityPlayerMP) player)) {
                sendLocks(player);
            }
            return;
        }
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(packet.data));
        int type = data.readUnsignedByte();
        if (type != WAILA_LOCKS && type != HUD_PROVIDER_LOCKS) return;
        int size = data.readUnsignedShort();
        if (size > MAX_KEYS) throw new IOException("Too many extension option locks");
        Map<String, Boolean> waila = new LinkedHashMap<String, Boolean>();
        Map<String, DisplayMode> providers = new LinkedHashMap<String, DisplayMode>();
        DisplayMode[] modes = DisplayMode.values();
        for (int i = 0; i < size; i++) {
            String key = data.readUTF();
            if (key.isEmpty() || key.length() > MAX_KEY_LENGTH)
                throw new IOException("Invalid extension option key");
            int value = data.readUnsignedByte();
            if (type == WAILA_LOCKS) {
                waila.put(key, value != 0);
            } else if (value < modes.length) {
                providers.put(key, modes[value]);
            } else {
                throw new IOException("Invalid HUD provider display mode");
            }
        }
        if (data.available() != 0) throw new IOException("Unexpected extension option data");
        if (type == WAILA_LOCKS) Iaspis.proxy.receiveWailaOptions(manager, waila);
        else Iaspis.proxy.receiveHudProviderModes(manager, providers);
    }

    private static void sendLocks(Player player) throws IOException {
        Map<String, Integer> providers = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, DisplayMode> entry :
                Iaspis.config().lockedHudProviderModes().entrySet()) {
            providers.put(entry.getKey(), entry.getValue().ordinal());
        }
        if (!providers.isEmpty()) send(player, HUD_PROVIDER_LOCKS, providers);

        Map<String, Integer> waila = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Boolean> entry : WailaBridge.INSTANCE.lockedOptions().entrySet()) {
            waila.put(entry.getKey(), entry.getValue() ? 1 : 0);
        }
        send(player, WAILA_LOCKS, waila);
    }

    private static void send(Player player, int type, Map<String, Integer> options)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(type);
        output.writeShort(0);
        int count = 0;
        for (Map.Entry<String, Integer> option : options.entrySet()) {
            String key = option.getKey();
            if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) continue;
            output.writeUTF(key);
            output.writeByte(option.getValue());
            if (++count == MAX_KEYS) break;
        }
        byte[] payload = bytes.toByteArray();
        payload[1] = (byte) (count >>> 8);
        payload[2] = (byte) count;
        PacketDispatcher.sendPacketToPlayer(new Packet250CustomPayload(NAME, payload), player);
    }

    private WailaOptionsChannel() {}
}
