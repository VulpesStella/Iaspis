package io.github.vulpesstella.iaspis.compat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import io.github.vulpesstella.iaspis.Iaspis;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;

public final class WailaOptionsChannel {
    public static final String NAME = "IaspisOptions";
    private static final int MAX_KEYS = 128;
    private static final int MAX_KEY_LENGTH = 64;
    private static final int MAX_BYTES = 25000;

    public static boolean available(EntityPlayer player) {
        return NetworkRegistry.instance().isChannelActive(NAME, (Player) player);
    }

    public static void request() {
        PacketDispatcher.sendPacketToServer(new Packet250CustomPayload(NAME, new byte[] {0}));
    }

    static void receive(INetworkManager manager, Packet250CustomPayload packet, Player player) throws IOException {
        if (packet.data == null || packet.data.length == 0 || packet.data.length > MAX_BYTES) return;
        if (player instanceof EntityPlayerMP) {
            if (packet.data.length == 1 && packet.data[0] == 0 && available((EntityPlayerMP) player)) {
                send(player, WailaBridge.INSTANCE.lockedOptions());
            }
            return;
        }
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(packet.data));
        if (data.readUnsignedByte() != 1) return;
        int size = data.readUnsignedShort();
        if (size > MAX_KEYS) throw new IOException("Too many WAILA option locks");
        Map<String, Boolean> values = new LinkedHashMap<String, Boolean>();
        for (int i = 0; i < size; i++) {
            String key = data.readUTF();
            if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) throw new IOException("Invalid WAILA option key");
            values.put(key, data.readBoolean());
        }
        if (data.available() != 0) throw new IOException("Unexpected WAILA option data");
        Iaspis.proxy.receiveWailaOptions(manager, values);
    }

    private static void send(Player player, Map<String, Boolean> options) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(1);
        output.writeShort(0);
        int count = 0;
        for (Map.Entry<String, Boolean> option : options.entrySet()) {
            String key = option.getKey();
            if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) continue;
            output.writeUTF(key);
            output.writeBoolean(option.getValue());
            if (++count == MAX_KEYS) break;
        }
        byte[] payload = bytes.toByteArray();
        payload[1] = (byte) (count >>> 8);
        payload[2] = (byte) count;
        PacketDispatcher.sendPacketToPlayer(new Packet250CustomPayload(NAME, payload), player);
    }

    private WailaOptionsChannel() {}
}
