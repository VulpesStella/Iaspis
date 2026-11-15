package io.github.vulpesstella.iaspis.client;

import java.util.ArrayList;
import java.util.List;

import io.github.vulpesstella.iaspis.compat.Target;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;

final class HarvestHint {
    private static final String[] TOOLS = { "pickaxe", "axe", "shovel" };
    private static final Item[][] ICONS = {
            { Item.pickaxeWood, Item.pickaxeStone, Item.pickaxeIron, Item.pickaxeDiamond },
            { Item.axeWood, Item.axeStone, Item.axeIron, Item.axeDiamond },
            { Item.shovelWood, Item.shovelStone, Item.shovelIron, Item.shovelDiamond }
    };

    final List<ToolIcon> icons;
    final boolean harvestable;
    final boolean showStatus;

    private HarvestHint(List<ToolIcon> icons, boolean harvestable, boolean showStatus) {
        this.icons = icons;
        this.harvestable = harvestable;
        this.showStatus = showStatus;
    }

    static HarvestHint create(Target target, List<String> lines) {
        Block block = target.getBlock();
        if (target.getPlayer() == null) return null;
        MovingObjectPosition hit = target.getPosition();
        float hardness = block.getBlockHardness(target.getWorld(), hit.blockX, hit.blockY, hit.blockZ);
        if (hardness < 0) {
            lines.add(StatCollector.translateToLocal("iaspis.harvest.unbreakable"));
            return null;
        }

        boolean requiresTool = !block.blockMaterial.isToolNotRequired();
        List<ToolIcon> icons = findTools(block, target.getMetadata(), hardness, requiresTool);
        if (icons.isEmpty()) return null;
        boolean harvestable = block.canHarvestBlock(target.getPlayer(), target.getMetadata());
        return new HarvestHint(icons, harvestable, requiresTool || !harvestable);
    }

    private static List<ToolIcon> findTools(Block block, int metadata, float hardness, boolean requiresTool) {
        List<ToolIcon> icons = new ArrayList<ToolIcon>();
        if (hardness > 0 || requiresTool) {
            for (int i = 0; i < TOOLS.length; i++) {
                int level = MinecraftForge.getBlockHarvestLevel(block, metadata, TOOLS[i]);
                Item[] tiers = ICONS[i];
                if (level >= 0) {
                    int highest = tiers.length - 1;
                    icons.add(new ToolIcon(new ItemStack(tiers[Math.min(level, highest)]),
                            level > highest ? Integer.toString(level) : ""));
                    continue;
                }
                for (Item tool : tiers) {
                    if (isEffective(tool, block, metadata, requiresTool)) {
                        icons.add(new ToolIcon(new ItemStack(tool), ""));
                        break;
                    }
                }
            }
            if (isEffective(Item.swordWood, block, metadata, requiresTool)) {
                icons.add(new ToolIcon(new ItemStack(Item.swordWood), ""));
            }
        }
        if (isEffective(Item.shears, block, metadata, requiresTool)) {
            icons.add(new ToolIcon(new ItemStack(Item.shears), ""));
        }
        return icons;
    }

    private static boolean isEffective(Item tool, Block block, int metadata, boolean requiresTool) {
        if (tool.canHarvestBlock(block)) return true;
        // A faster tool may still be too weak to obtain the block's drops.
        return !requiresTool && tool.getStrVsBlock(new ItemStack(tool), block, metadata) > 1.0F;
    }

    static final class ToolIcon {
        final ItemStack stack;
        final String levelBadge;

        ToolIcon(ItemStack stack, String levelBadge) {
            this.stack = stack;
            this.levelBadge = levelBadge;
        }
    }
}
