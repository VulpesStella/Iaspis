package io.github.vulpesstella.iaspis.compat;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.World;

public final class InventoryPreview {
    public static final int COMPACT_LIMIT = 9;
    public static final int DETAIL_LIMIT = 27;
    private static final int SCAN_LIMIT = 4096;
    private static final int FURNACE_SLOT_COUNT = 3;
    public static final InventoryPreview EMPTY = new InventoryPreview(
            Collections.<ItemStack>emptyList(), false);

    public final List<ItemStack> stacks;
    public final boolean truncated;
    public final List<ItemStack> furnaceSlots;

    private InventoryPreview(List<ItemStack> stacks, boolean truncated) {
        this(stacks, truncated, Collections.<ItemStack>emptyList());
    }

    private InventoryPreview(List<ItemStack> stacks, boolean truncated, List<ItemStack> furnaceSlots) {
        this.stacks = Collections.unmodifiableList(stacks);
        this.truncated = truncated;
        this.furnaceSlots = Collections.unmodifiableList(furnaceSlots);
    }

    static InventoryPreview collect(EntityPlayer player, Block block, TileEntity tile) {
        if (!(tile instanceof IInventory) || player == null) return EMPTY;
        World world = player.worldObj;
        IInventory inventory = block == Block.chest || block == Block.chestTrapped
                ? ((BlockChest) block).getInventory(world, tile.xCoord, tile.yCoord, tile.zCoord)
                : (IInventory) tile;
        if (inventory == null || !inventory.isUseableByPlayer(player)) return EMPTY;

        List<ItemStack> icons = new ArrayList<ItemStack>();
        List<ItemStack> furnaceSlots = new ArrayList<ItemStack>(FURNACE_SLOT_COUNT);
        int size = inventory.getSizeInventory();
        for (int i = 0; i < Math.min(size, SCAN_LIMIT); i++) {
            ItemStack stack = previewStack(inventory.getStackInSlot(i));
            if (tile instanceof TileEntityFurnace && i < FURNACE_SLOT_COUNT) furnaceSlots.add(stack);
            if (stack == null) continue;
            if (icons.size() == DETAIL_LIMIT) return new InventoryPreview(icons, true);
            icons.add(stack);
        }
        return icons.isEmpty() ? EMPTY : new InventoryPreview(icons, size > SCAN_LIMIT, furnaceSlots);
    }

    private static ItemStack previewStack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || stack.getItem() == null) return null;
        return new ItemStack(stack.itemID, stack.stackSize, stack.getItemDamage());
    }

    void write(DataOutput output) throws IOException {
        output.writeByte(stacks.size());
        output.writeBoolean(truncated);
        for (ItemStack stack : stacks) writeStack(output, stack);
        // Optional trailing slot order preserves the original compact preview on older clients.
        if (furnaceSlots.size() == FURNACE_SLOT_COUNT) {
            for (ItemStack stack : furnaceSlots) writeStack(output, stack);
        }
    }

    private static void writeStack(DataOutput output, ItemStack stack) throws IOException {
        output.writeInt(stack == null ? 0 : stack.itemID);
        output.writeInt(stack == null ? 0 : stack.stackSize);
        output.writeInt(stack == null ? 0 : stack.getItemDamage());
    }

    static InventoryPreview read(DataInputStream input) throws IOException {
        int size = input.readUnsignedByte();
        if (size > DETAIL_LIMIT) throw new IOException("Too many inventory preview stacks");
        boolean truncated = input.readBoolean();
        List<ItemStack> icons = new ArrayList<ItemStack>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = readStack(input);
            if (stack != null) icons.add(stack);
        }
        List<ItemStack> furnaceSlots = new ArrayList<ItemStack>(FURNACE_SLOT_COUNT);
        int slotBytes = FURNACE_SLOT_COUNT * 3 * (Integer.SIZE / Byte.SIZE);
        boolean orderedSlots = input.available() == slotBytes;
        if (input.available() > slotBytes) {
            input.mark(4);
            orderedSlots = input.readInt() != TankPreview.MARKER;
            input.reset();
        }
        if (orderedSlots) {
            for (int i = 0; i < FURNACE_SLOT_COUNT; i++) furnaceSlots.add(readStack(input));
        }
        return new InventoryPreview(icons, truncated, furnaceSlots);
    }

    private static ItemStack readStack(DataInput input) throws IOException {
        int id = input.readInt();
        int count = input.readInt();
        int damage = input.readInt();
        return id > 0 && id < Item.itemsList.length && Item.itemsList[id] != null && count > 0
                ? new ItemStack(id, count, damage) : null;
    }
}
