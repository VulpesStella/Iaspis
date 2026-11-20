package io.github.vulpesstella.iaspis.compat;

import mcp.mobius.waila.api.IWailaFMPAccessor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public final class MultipartAccessor implements IWailaFMPAccessor {
    private final Target target;
    private final String id;
    private final NBTTagCompound part;

    MultipartAccessor(Target target, String id, NBTTagCompound part) {
        this.target = target;
        this.id = id;
        this.part = part;
    }

    public World getWorld() {
        return target.getWorld();
    }

    public EntityPlayer getPlayer() {
        return target.getPlayer();
    }

    public TileEntity getTileEntity() {
        return target.getTileEntity();
    }

    public MovingObjectPosition getPosition() {
        return target.getPosition();
    }

    public NBTTagCompound getNBTData() {
        return part;
    }

    public NBTTagCompound getFullNBTData() {
        return target.getNBTData();
    }

    public int getNBTInteger(NBTTagCompound tag, String key) {
        return target.getNBTInteger(tag, key);
    }

    public double getPartialFrame() {
        return target.getPartialFrame();
    }

    public Vec3 getRenderingPosition() {
        return target.getRenderingPosition();
    }

    public String getID() {
        return id;
    }
}
