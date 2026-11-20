package io.github.vulpesstella.iaspis.client;

import java.util.List;
import java.util.Random;

import io.github.vulpesstella.iaspis.compat.Target;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import net.minecraft.block.Block;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.IShearable;

final class HarvestabilityStyle {
    private static final String GREEN = "\u00a72";
    private static final String DARK_RED = "\u00a74";
    private static final String RED = "\u00a7c";
    private static final String RESET = "\u00a7r";

    static void append(Target target, HarvestHint hint, List<String> lines) {
        if (target.getPlayer() == null || (target.getPlayer().capabilities.isCreativeMode
                && !PreviewSample.HARVEST_CREATIVE.enabled())) return;
        append(hint, silkTouch(target), shears(target), lines);
    }

    static void append(HarvestHint hint, String silk, String scissors, List<String> lines) {
        StringBuilder status = new StringBuilder();
        if (hint != null && hint.showStatus) {
            status.append(color(hint.harvestable)).append(hint.harvestable ? '\u2714' : '\u2718')
                    .append(RESET).append(' ').append(translate("currentlyHarvestable"));
        }
        appendMark(status, silk);
        appendMark(status, scissors);
        if (status.length() > 0) lines.add(status.toString());
        if (hint == null || hint.icons.isEmpty()) return;

        StringBuilder tools = new StringBuilder();
        StringBuilder tiers = new StringBuilder();
        String color = color(hint.harvestable);
        for (HarvestHint.ToolIcon tool : hint.icons) {
            String toolName = StatCollector.translateToLocal("iaspis.harvest.tool." + tool.toolClass);
            if (tools.length() > 0) tools.append(RESET).append(", ");
            tools.append(color).append(toolName);
            if (tool.level >= 0) {
                if (tiers.length() > 0) tiers.append(RESET).append(", ");
                if (hint.icons.size() > 1) tiers.append(toolName).append(' ');
                tiers.append(color).append(tool.levelName == null ? tierName(tool.level) : tool.levelName);
            }
        }
        lines.add(translate("effectiveTool") + " : " + tools);
        if (tiers.length() > 0) lines.add(translate("harvestLevel") + " : " + tiers);
    }

    private static void appendMark(StringBuilder status, String mark) {
        if (mark.isEmpty()) return;
        if (status.length() > 0) status.append(' ');
        status.append(mark);
    }

    private static String silkTouch(Target target) {
        Block block = target.getBlock();
        MovingObjectPosition hit = target.getPosition();
        int metadata = target.getMetadata();
        try {
            if (block.canSilkHarvest(target.getWorld(), target.getPlayer(), hit.blockX, hit.blockY, hit.blockZ, metadata)) {
                Random sample = new Random(0);
                if (block.idDropped(metadata, sample, 0) != block.blockID || block.quantityDropped(sample) == 0) {
                    return color(EnchantmentHelper.getSilkTouchModifier(target.getPlayer())) + "\u2712";
                }
            }
        } catch (RuntimeException e) {
            WailaBridge.INSTANCE.report("Silk Touch preview for " + block.getClass().getName(), e);
        } catch (LinkageError e) {
            WailaBridge.INSTANCE.report("Silk Touch preview for " + block.getClass().getName(), e);
        }
        return "";
    }

    private static String shears(Target target) {
        Block block = target.getBlock();
        if (!(block instanceof IShearable) && block != Block.deadBush) return "";
        ItemStack held = target.getPlayer().getCurrentEquippedItem();
        if (held == null || !(held.getItem() instanceof ItemShears)) return DARK_RED + "\u2702";
        if (block == Block.deadBush) return GREEN + "\u2702";
        MovingObjectPosition hit = target.getPosition();
        try {
            return (((IShearable) block).isShearable(held, target.getWorld(), hit.blockX, hit.blockY, hit.blockZ)
                    ? GREEN : RED) + "\u2702";
        } catch (RuntimeException e) {
            WailaBridge.INSTANCE.report("Shearability preview for " + block.getClass().getName(), e);
        } catch (LinkageError e) {
            WailaBridge.INSTANCE.report("Shearability preview for " + block.getClass().getName(), e);
        }
        return "";
    }

    static String color(boolean active) {
        return active ? GREEN : DARK_RED;
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal("iaspis.harvestability." + key);
    }

    private static String tierName(int level) {
        String key = "iaspis.harvestability.tier." + level;
        String name = StatCollector.translateToLocal(key);
        return key.equals(name) ? Integer.toString(level) : name;
    }

    private HarvestabilityStyle() {}
}
