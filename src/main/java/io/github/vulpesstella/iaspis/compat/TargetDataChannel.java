package io.github.vulpesstella.iaspis.compat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.IaspisConfig.DisplayMode;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHopper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidHandler;

public final class TargetDataChannel implements IPacketHandler {
    public static final String NAME = "IaspisData";
    public static final String TANK_CAPABILITY = "IaspisTanks";
    public static final String MACHINE_CAPABILITY = "IaspisMachines";
    public static final String HOPPER_LOCKED = "IaspisHopperLocked";
    private static final int MAX_PACKET = 30000;
    private static final int REQUEST_SIZE = 21;
    private static final long MIN_INTERVAL = 150000000L;
    private final Map<EntityPlayerMP, Long> lastRequest = new WeakHashMap<EntityPlayerMP, Long>();

    public static boolean canRequest(EntityPlayer player) {
        return NetworkRegistry.instance().isChannelActive(NAME, (Player) player);
    }

    public static boolean supportsTanks(EntityPlayer player) {
        return NetworkRegistry.instance().isChannelActive(TANK_CAPABILITY, (Player) player);
    }

    public static boolean supportsMachines(EntityPlayer player) {
        return NetworkRegistry.instance().isChannelActive(MACHINE_CAPABILITY, (Player) player);
    }

    public static void request(Target target, int sequence) {
        MovingObjectPosition hit = target.getPosition();
        boolean entity = target.getEntity() != null;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(REQUEST_SIZE);
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            out.writeInt(sequence);
            out.writeInt(target.getPlayer().dimension);
            out.writeBoolean(entity);
            out.writeInt(entity ? target.getEntity().entityId : hit.blockX);
            out.writeInt(entity ? 0 : hit.blockY);
            out.writeInt(entity ? 0 : hit.blockZ);
            PacketDispatcher.sendPacketToServer(new Packet250CustomPayload(NAME, bytes.toByteArray()));
        } catch (IOException e) {
            WailaBridge.INSTANCE.report("Could not encode Iaspis target request", e);
        }
    }

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player) {
        if (WailaOptionsChannel.NAME.equals(packet.channel)) {
            try {
                if (!(player instanceof EntityPlayerMP) || permitRequest((EntityPlayerMP) player)) {
                    WailaOptionsChannel.receive(manager, packet, player);
                }
            } catch (IOException e) {
                WailaBridge.INSTANCE.report("Invalid Iaspis WAILA options packet", e);
            }
            return;
        }
        if (!NAME.equals(packet.channel) || packet.data == null || packet.data.length > MAX_PACKET) return;
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(packet.data));
        try {
            int sequence = input.readInt();
            if (!(player instanceof EntityPlayerMP)) {
                NBTTagCompound data = CompressedStreamTools.read(input);
                InventoryPreview inventory = InventoryPreview.EMPTY;
                List<TankPreview> tanks = Collections.emptyList();
                MachinePreview machine = MachinePreview.EMPTY;
                // Older Iaspis servers end the response after the legacy NBT compound.
                if (input.available() > 0) {
                    if (!input.readBoolean()) data = null;
                    inventory = InventoryPreview.read(input);
                    tanks = TankPreview.read(input);
                    machine = MachinePreview.read(input);
                }
                Iaspis.proxy.receiveTargetData(sequence, data, inventory, tanks, machine);
                return;
            }
            if (packet.data.length != REQUEST_SIZE) return;
            EntityPlayerMP sender = (EntityPlayerMP) player;
            int dimension = input.readInt();
            if (sender.dimension != dimension || !permitRequest(sender)) return;
            boolean entity = input.readBoolean();
            int x = input.readInt();
            int y = input.readInt();
            int z = input.readInt();
            if (!entity) {
                sendBlockData(sender, sequence, x, y, z);
                return;
            }
            NBTTagCompound data = entityData(sender, x);
            if (data != null) sendResponse(sender, sequence, data, InventoryPreview.EMPTY,
                    Collections.<TankPreview>emptyList(), MachinePreview.EMPTY);
        } catch (IOException e) {
            WailaBridge.INSTANCE.report("Invalid Iaspis target data packet", e);
        } catch (RuntimeException e) {
            WailaBridge.INSTANCE.report("Could not read Iaspis target data", e);
        }
    }

    private static void sendResponse(EntityPlayerMP player, int sequence, NBTTagCompound tag,
                                     InventoryPreview inventory, List<TankPreview> tanks, MachinePreview machine) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(sequence);
        CompressedStreamTools.write(tag == null ? new NBTTagCompound() : tag, output);
        output.writeBoolean(tag != null);
        inventory.write(output);
        TankPreview.write(output, tanks);
        machine.write(output);
        if (bytes.size() > MAX_PACKET) {
            if (tag != null) sendResponse(player, sequence, null, inventory, tanks, machine);
            return;
        }
        PacketDispatcher.sendPacketToPlayer(new Packet250CustomPayload(NAME, bytes.toByteArray()), (Player) player);
    }

    private boolean permitRequest(EntityPlayerMP player) {
        long now = System.nanoTime();
        Long last = lastRequest.get(player);
        if (last != null && now - last < MIN_INTERVAL) return false;
        lastRequest.put(player, now);
        return true;
    }

    private static void sendBlockData(EntityPlayerMP player, int sequence, int x, int y, int z) throws IOException {
        World world = player.worldObj;
        if (player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > 64 || !world.blockExists(x, y, z)) return;
        TileEntity tile = world.getBlockTileEntity(x, y, z);
        if (tile == null) return;

        int id = world.getBlockId(x, y, z);
        Block block = Block.blocksList[id];
        Vec3 eyes = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        // The brewing stand's selectable base sits near the bottom of the block.
        double hitHeight = block == Block.brewingStand ? 0.0625 : 0.5;
        Vec3 point = Vec3.createVectorHelper(x + 0.5, y + hitHeight, z + 0.5);
        boolean multipart = WailaBridge.isMultipart(block);
        MovingObjectPosition hit;
        if (multipart) {
            Vec3 look = player.getLook(1);
            Vec3 end = eyes.addVector(look.xCoord * 8, look.yCoord * 8, look.zCoord * 8);
            hit = world.rayTraceBlocks_do_do(eyes, end, false, false);
        } else {
            hit = world.rayTraceBlocks_do_do(eyes, point, false, true);
        }
        if (!hitsBlock(hit, x, y, z)) return;

        WailaBridge bridge = WailaBridge.INSTANCE;
        List<String> keys = bridge.syncedKeys(id, block, tile);
        NBTTagCompound data = null;
        if (!keys.isEmpty()) {
            NBTTagCompound full = new NBTTagCompound();
            tile.writeToNBT(full);
            data = select(full, keys);
        }
        if (block == Block.hopperBlock) {
            if (data == null) data = new NBTTagCompound();
            data.setBoolean(HOPPER_LOCKED,
                    !BlockHopper.getIsBlockNotPoweredFromMetadata(world.getBlockMetadata(x, y, z)));
        }
        InventoryPreview inventory = InventoryPreview.EMPTY;
        if (tile instanceof IInventory && Iaspis.config().getBoolean("inventory.preview")) {
            try {
                inventory = InventoryPreview.collect(player, block, tile);
            } catch (RuntimeException e) {
                bridge.report("Inventory preview for " + tile.getClass().getName(), e);
            } catch (LinkageError e) {
                bridge.report("Inventory preview for " + tile.getClass().getName(), e);
            }
        }
        List<TankPreview> tanks = Collections.emptyList();
        if (tile instanceof IFluidHandler && Iaspis.config().getTankDisplay() != DisplayMode.OFF
                && supportsTanks(player)) {
            try {
                tanks = TankPreview.collect((IFluidHandler) tile);
            } catch (RuntimeException e) {
                bridge.report("Tank preview for " + tile.getClass().getName(), e);
            } catch (LinkageError e) {
                bridge.report("Tank preview for " + tile.getClass().getName(), e);
            }
        }
        MachinePreview machine = supportsMachines(player)
                ? MachineProviders.collect(tile)
                : MachinePreview.EMPTY;
        sendResponse(player, sequence, data, inventory, tanks, machine);
    }

    private static NBTTagCompound entityData(EntityPlayerMP player, int id) {
        Entity entity = player.worldObj.getEntityByID(id);
        if (entity == null || entity instanceof EntityPlayer || player.getDistanceSqToEntity(entity) > 64
                || !player.canEntityBeSeen(entity)) return null;
        List<String> keys = WailaBridge.INSTANCE.syncedKeys(-1, entity, null);
        if (keys.isEmpty() && !EntityHudData.needsSync(entity)) return null;
        NBTTagCompound data = new NBTTagCompound();
        if (!keys.isEmpty()) {
            entity.writeToNBT(data);
            data = select(data, keys);
        }
        EntityHudData.append(entity, data);
        return data;
    }

    private static boolean hitsBlock(MovingObjectPosition hit, int x, int y, int z) {
        return hit != null && hit.blockX == x && hit.blockY == y && hit.blockZ == z;
    }

    private static NBTTagCompound select(NBTTagCompound source, List<String> keys) {
        NBTTagCompound result = new NBTTagCompound();
        for (String key : keys) copyPath(source, result, key);
        return result;
    }

    private static void copyPath(NBTTagCompound source, NBTTagCompound result, String path) {
        int separator = path.indexOf('.');
        String key = separator < 0 ? path : path.substring(0, separator);
        if ("*".equals(key) && separator < 0) {
            for (Object entry : source.getTags()) {
                NBTBase tag = (NBTBase) entry;
                result.setTag(tag.getName(), tag.copy());
            }
        } else if (separator < 0) {
            NBTBase tag = source.getTag(key);
            if (tag != null) result.setTag(key, tag.copy());
        } else if (source.getTag(key) instanceof NBTTagCompound) {
            NBTTagCompound nested = result.getCompoundTag(key);
            copyPath(source.getCompoundTag(key), nested, path.substring(separator + 1));
            result.setCompoundTag(key, nested);
        }
    }
}
