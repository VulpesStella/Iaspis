package io.github.vulpesstella.iaspis.client;

import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.compat.InventoryPreview;
import io.github.vulpesstella.iaspis.compat.Target;
import io.github.vulpesstella.iaspis.compat.TargetDataChannel;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MovingObjectPosition;

final class RemoteTargetData {
    private static final long REFRESH = 250000000L;
    private static final long EXPIRE = 1000000000L;

    private Target requested;
    private int sequence;
    private long nextRequest;
    private long dataTime;
    private NBTTagCompound data;
    private InventoryPreview inventory = InventoryPreview.EMPTY;

    void apply(Target target, boolean request) {
        boolean sameTarget = matches(target, requested);
        if (request && !sameTarget) {
            requested = null;
            data = null;
            inventory = InventoryPreview.EMPTY;
        }
        Entity entity = target.getEntity();
        if ((entity == null && target.getTileEntity() == null) || entity instanceof EntityPlayer
                || !TargetDataChannel.canRequest(target.getPlayer())) return;

        long now = System.nanoTime();
        boolean fresh = sameTarget && now - dataTime < EXPIRE;
        if (fresh && data != null) target.setRemoteData(data);
        WailaBridge bridge = WailaBridge.INSTANCE;
        boolean preview = target.getTileEntity() instanceof IInventory && Iaspis.config().getBoolean("inventory.preview");
        target.setInventoryPreview(fresh && preview ? inventory : InventoryPreview.EMPTY);
        if (!request || now - nextRequest < 0 || (!preview && bridge.syncedKeys(target.getBlockID(),
                entity == null ? target.getBlock() : entity, target.getTileEntity()).isEmpty())) return;

        requested = target;
        nextRequest = now + REFRESH;
        TargetDataChannel.request(target, ++sequence);
    }

    void receive(int response, NBTTagCompound tag, InventoryPreview preview) {
        if (response == sequence && requested != null) {
            data = tag;
            inventory = preview;
            dataTime = System.nanoTime();
        }
    }

    private static boolean matches(Target a, Target b) {
        if (b == null || a.getWorld() != b.getWorld() || a.getPlayer() != b.getPlayer()) return false;
        if (a.getEntity() != null || b.getEntity() != null) return a.getEntity() == b.getEntity();
        MovingObjectPosition left = a.getPosition();
        MovingObjectPosition right = b.getPosition();
        return left.blockX == right.blockX && left.blockY == right.blockY && left.blockZ == right.blockZ
                && a.getBlock() == b.getBlock() && a.getTileEntity() == b.getTileEntity();
    }
}
