package io.github.vulpesstella.iaspis.client;

import java.util.List;

import io.github.vulpesstella.iaspis.compat.Target;
import io.github.vulpesstella.iaspis.compat.TargetDataChannel;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.tileentity.TileEntityRecordPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;

final class VanillaBlockDetails {
    private static final int BREW_TICKS = 400;
    private static final int MAX_ENCHANT_POWER = 15;
    private static final String[] NOTES = {
        "F#", "G", "G#", "A", "A#", "B", "C", "C#", "D", "D#", "E", "F"
    };

    static void append(Target target, Tooltip tooltip, List<String> lines) {
        Block block = target.getBlock();
        TileEntity tile = target.getTileEntity();
        if (block == Block.brewingStand && PreviewSample.BREWING.enabled() && tile instanceof TileEntityBrewingStand) {
            int remaining = target.getNBTInteger(target.getNBTData(), "BrewTime");
            if (remaining > 0) tooltip.brewProgress = (BREW_TICKS - Math.min(remaining, BREW_TICKS)) / (float) BREW_TICKS;
        } else if (block == Block.mobSpawner && PreviewSample.SPAWNER.enabled() && tile instanceof TileEntityMobSpawner) {
            String id = ((TileEntityMobSpawner) tile).getSpawnerLogic().getEntityNameToSpawn();
            String label = spawns(id);
            if (label != null) lines.add(label);
        } else if (block == Block.hopperBlock && PreviewSample.HOPPER.enabled()) {
            NBTTagCompound data = target.getRemoteData();
            MovingObjectPosition hit = target.getPosition();
            boolean locked = data != null && data.hasKey(TargetDataChannel.HOPPER_LOCKED)
                    ? data.getBoolean(TargetDataChannel.HOPPER_LOCKED)
                    : target.getWorld().isBlockIndirectlyGettingPowered(hit.blockX, hit.blockY, hit.blockZ);
            lines.add(hopper(locked));
        } else if (block == Block.music && PreviewSample.NOTE_BLOCK.enabled()) {
            NBTTagCompound data = target.getRemoteData();
            // The server owns the stored pitch.
            if (data != null && data.hasKey("note")) {
                String label = note(data.getByte("note"));
                if (label != null) lines.add(label);
            }
        } else if (block == Block.jukebox && PreviewSample.JUKEBOX.enabled() && target.getMetadata() != 0
                && tile instanceof TileEntityRecordPlayer) {
            ItemStack record = ((TileEntityRecordPlayer) tile).func_96097_a();
            NBTTagCompound data = target.getRemoteData();
            if (data != null && data.hasKey("Record")) {
                int id = data.getInteger("Record");
                record = id > 0 && id < Item.itemsList.length && Item.itemsList[id] instanceof ItemRecord
                        ? new ItemStack(id, 1, 0) : null;
            }
            setRecord(tooltip, record);
        } else if (block == Block.enchantmentTable && PreviewSample.ENCHANTING.enabled()) {
            MovingObjectPosition hit = target.getPosition();
            lines.add(enchanting(enchantPower(target.getWorld(), hit.blockX, hit.blockY, hit.blockZ)));
        }
    }

    static String spawns(String id) {
        if (id == null || id.isEmpty()) return null;
        String key = "entity." + id + ".name";
        String name = StatCollector.translateToLocal(key);
        return StatCollector.translateToLocalFormatted("iaspis.block.spawns", key.equals(name) ? id : name);
    }

    static String hopper(boolean locked) {
        return StatCollector.translateToLocal(locked ? "iaspis.block.hopper.locked" : "iaspis.block.hopper.unlocked");
    }

    static String blockName(Block block, ItemStack stack) {
        String name = stack == null ? block.getLocalizedName() : stack.getDisplayName();
        if (stack != null && (stack.itemID != block.blockID || stack.hasDisplayName())) return name;
        if (block == Block.dirt && ("Dirt".equals(name) || "tile.dirt.name".equals(name))) {
            return StatCollector.func_94522_b("tile.dirt.default.name")
                    ? StatCollector.translateToLocal("tile.dirt.default.name") : name;
        }
        if (block == Block.sand && ("Sand".equals(name) || "tile.sand.name".equals(name))) {
            return StatCollector.func_94522_b("tile.sand.default.name")
                    ? StatCollector.translateToLocal("tile.sand.default.name") : name;
        }
        return name;
    }

    static String note(int pitch) {
        if (pitch < 0 || pitch > 24) return null;
        String name = NOTES[pitch % NOTES.length] + (3 + (pitch + 6) / NOTES.length);
        return StatCollector.translateToLocalFormatted("iaspis.block.note", name);
    }

    static void setRecord(Tooltip tooltip, ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemRecord)) return;
        tooltip.detailItem = stack;
        tooltip.detailLabel = ((ItemRecord) stack.getItem()).getRecordTitle();
    }

    static String enchanting(int power) {
        return StatCollector.translateToLocalFormatted("iaspis.block.enchanting", power, MAX_ENCHANT_POWER);
    }

    private static int enchantPower(World world, int x, int y, int z) {
        // Match ContainerEnchantment's two-block-high gaps and Forge power contributions.
        float power = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx == 0 && dz == 0) || !world.isAirBlock(x + dx, y, z + dz)
                        || !world.isAirBlock(x + dx, y + 1, z + dz)) continue;
                power += columnPower(world, x + dx * 2, y, z + dz * 2);
                if (dx != 0 && dz != 0) {
                    power += columnPower(world, x + dx * 2, y, z + dz);
                    power += columnPower(world, x + dx, y, z + dz * 2);
                }
            }
        }
        return Math.max(0, Math.min(MAX_ENCHANT_POWER, (int) power));
    }

    private static float columnPower(World world, int x, int y, int z) {
        return ForgeHooks.getEnchantPower(world, x, y, z) + ForgeHooks.getEnchantPower(world, x, y + 1, z);
    }

    private VanillaBlockDetails() {}
}
