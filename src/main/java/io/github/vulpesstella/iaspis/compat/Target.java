package io.github.vulpesstella.iaspis.compat;

import io.github.vulpesstella.iaspis.Iaspis;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaEntityAccessor;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumMovingObjectType;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;

public final class Target implements IWailaDataAccessor, IWailaEntityAccessor {
    private final World world;
    private final EntityPlayer player;
    private final MovingObjectPosition hit;
    private final double partialTicks;
    private final Vec3 renderPosition;
    private final Block block;
    private final TileEntity tile;
    private final int metadata;
    private final ItemStack stack;
    private NBTTagCompound nbt;
    private boolean readNbt;
    private InventoryPreview inventory;

    public Target(World world, EntityPlayer player, Entity camera, MovingObjectPosition hit,
                  double partialTicks, ItemStack stack) {
        this.world = world;
        this.player = player;
        this.hit = hit;
        this.partialTicks = partialTicks;
        this.stack = stack;
        boolean isBlock = hit.typeOfHit == EnumMovingObjectType.TILE;
        block = isBlock ? Block.blocksList[world.getBlockId(hit.blockX, hit.blockY, hit.blockZ)] : null;
        tile = isBlock ? world.getBlockTileEntity(hit.blockX, hit.blockY, hit.blockZ) : null;
        metadata = isBlock ? world.getBlockMetadata(hit.blockX, hit.blockY, hit.blockZ) : 0;
        Entity entity = hit.entityHit;
        double x = entity == null ? hit.blockX : entity.posX;
        double y = entity == null ? hit.blockY : entity.posY;
        double z = entity == null ? hit.blockZ : entity.posZ;
        renderPosition = Vec3.createVectorHelper(
                x - (camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks),
                y - (camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks),
                z - (camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks));
    }

    public World getWorld() { return world; }
    public EntityPlayer getPlayer() { return player; }
    public MovingObjectPosition getPosition() { return hit; }
    public Vec3 getRenderingPosition() { return renderPosition; }
    public double getPartialFrame() { return partialTicks; }
    public ForgeDirection getSide() { return ForgeDirection.getOrientation(hit.sideHit); }
    public Block getBlock() { return block; }
    public int getBlockID() { return block == null ? 0 : block.blockID; }
    public int getMetadata() { return metadata; }
    public TileEntity getTileEntity() { return tile; }
    public Entity getEntity() { return hit.entityHit; }
    public ItemStack getStack() { return stack; }

    public void setRemoteData(NBTTagCompound data) {
        nbt = data;
        readNbt = true;
    }

    public void setInventoryPreview(InventoryPreview preview) {
        inventory = preview;
    }

    public InventoryPreview getInventoryPreview() {
        if (inventory != null) return inventory;
        if (Iaspis.config().getBoolean("inventory.preview")) {
            try {
                return InventoryPreview.collect(player, block, tile);
            } catch (RuntimeException e) {
                WailaBridge.INSTANCE.report("Local inventory preview", e);
            } catch (LinkageError e) {
                WailaBridge.INSTANCE.report("Local inventory preview", e);
            }
        }
        return InventoryPreview.EMPTY;
    }

    public NBTTagCompound getNBTData() {
        if (!readNbt) {
            readNbt = true;
            NBTTagCompound tag = new NBTTagCompound();
            if (tile != null) {
                tile.writeToNBT(tag);
                nbt = tag;
            } else if (getEntity() != null) {
                getEntity().writeToNBT(tag);
                nbt = tag;
            }
        }
        return nbt;
    }

    public int getNBTInteger(NBTTagCompound tag, String key) {
        NBTBase value = tag == null ? null : tag.getTag(key);
        if (value instanceof NBTTagByte) return ((NBTTagByte) value).data;
        if (value instanceof NBTTagShort) return ((NBTTagShort) value).data;
        return value instanceof NBTTagInt ? ((NBTTagInt) value).data : 0;
    }
}
