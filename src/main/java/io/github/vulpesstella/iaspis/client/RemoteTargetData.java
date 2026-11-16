package io.github.vulpesstella.iaspis.client;

import java.util.Collections;
import java.util.List;

import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.IaspisConfig.DisplayMode;
import io.github.vulpesstella.iaspis.compat.EntityHudData;
import io.github.vulpesstella.iaspis.compat.InventoryPreview;
import io.github.vulpesstella.iaspis.compat.MachinePreview;
import io.github.vulpesstella.iaspis.compat.MachineProviders;
import io.github.vulpesstella.iaspis.compat.Target;
import io.github.vulpesstella.iaspis.compat.TankPreview;
import io.github.vulpesstella.iaspis.compat.TargetDataChannel;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fluids.IFluidHandler;

final class RemoteTargetData {
    private static final long REFRESH = 250000000L;
    private static final long EXPIRE = 1000000000L;

    private Target requested;
    private int sequence;
    private long nextRequest;
    private long dataTime;
    private NBTTagCompound data;
    private InventoryPreview inventory = InventoryPreview.EMPTY;
    private List<TankPreview> tanks = Collections.emptyList();
    private MachinePreview machine = MachinePreview.EMPTY;

    void apply(Target target, boolean request) {
        boolean sameTarget = matches(target, requested);
        if (request && !sameTarget) {
            requested = null;
            data = null;
            inventory = InventoryPreview.EMPTY;
            tanks = Collections.emptyList();
            machine = MachinePreview.EMPTY;
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
        boolean tank = target.getTileEntity() instanceof IFluidHandler && Iaspis.config().getTankDisplay() != DisplayMode.OFF
                && TargetDataChannel.supportsTanks(target.getPlayer());
        if (fresh && tank) target.setTankPreview(tanks);
        boolean machinePreview = TargetDataChannel.supportsMachines(target.getPlayer())
                && MachineProviders.supports(target.getTileEntity());
        if (fresh && machinePreview) target.setMachinePreview(machine);
        if (!request || now - nextRequest < 0 || (!preview && !tank && !machinePreview
                && !EntityHudData.needsSync(entity) && bridge.syncedKeys(target.getBlockID(),
                entity == null ? target.getBlock() : entity, target.getTileEntity()).isEmpty())) return;

        requested = target;
        nextRequest = now + REFRESH;
        TargetDataChannel.request(target, ++sequence);
    }

    void receive(int response, NBTTagCompound tag, InventoryPreview preview, List<TankPreview> tanks, MachinePreview machine) {
        if (response == sequence && requested != null) {
            data = tag;
            inventory = preview;
            this.tanks = tanks;
            this.machine = machine;
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
