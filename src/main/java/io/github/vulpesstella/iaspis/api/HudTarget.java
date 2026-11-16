package io.github.vulpesstella.iaspis.api;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/** The current client-side HUD target. Treat the returned game objects as read-only. */
public interface HudTarget {
    World getWorld();
    EntityPlayer getPlayer();
    MovingObjectPosition getPosition();
    Block getBlock();
    int getMetadata();
    TileEntity getTileEntity();
    Entity getEntity();
    ItemStack getStack();
}
