package io.github.vulpesstella.iaspis.compat;

import io.github.vulpesstella.iaspis.api.HarvestProviders;
import io.github.vulpesstella.iaspis.api.HarvestProviders.Tool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.common.MinecraftForge;

/** One resolved harvest result per target frame, shared by every HUD style and the breaking bar. */
public final class HarvestState {
    private static final String[] CLASSES = {"pickaxe", "axe", "shovel"};
    private static final Item[][] ICONS = {
        {Item.pickaxeWood, Item.pickaxeStone, Item.pickaxeIron, Item.pickaxeDiamond},
        {Item.axeWood, Item.axeStone, Item.axeIron, Item.axeDiamond},
        {Item.shovelWood, Item.shovelStone, Item.shovelIron, Item.shovelDiamond}
    };

    public final HarvestProviders.Context context;
    public final List<Tool> tools;
    public final boolean requiresTool;
    public final boolean harvestable;
    public final boolean breakable;

    public HarvestState(Target target) {
        context = new HarvestProviders.Context(target);
        Block block = target.getBlock();
        MovingObjectPosition hit = target.getPosition();
        float hardness =
                block.getBlockHardness(target.getWorld(), hit.blockX, hit.blockY, hit.blockZ);
        breakable = hardness >= 0;
        HarvestProviders.Changes changes = HarvestRegistry.read(context);
        requiresTool =
                changes.requiresTool == null
                        ? !block.blockMaterial.isToolNotRequired() || requiresShears(block)
                        : changes.requiresTool.booleanValue();
        List<Tool> requirements =
                changes.requirements == null
                        ? nativeTools(block, target.getMetadata(), hardness, requiresTool)
                        : new ArrayList<Tool>(changes.requirements);
        if (changes.requirements == null
                && block instanceof IShearable
                && !requiresShears(block)
                && !effective(Item.shears, block, target.getMetadata(), requiresTool)) {
            ItemStack shears = new ItemStack(Item.shears);
            if (((IShearable) block)
                    .isShearable(shears, target.getWorld(), hit.blockX, hit.blockY, hit.blockZ)) {
                requirements.add(new Tool("shears", -1, shears));
            }
        }
        tools = Collections.unmodifiableList(requirements);
        boolean allowed = block.canHarvestBlock(target.getPlayer(), target.getMetadata());
        if (changes.capabilities != null && requiresTool && !tools.isEmpty()) {
            allowed = matches(changes.capabilities, tools);
        }
        if (changes.harvestable != null) allowed = changes.harvestable.booleanValue();
        ItemStack held = context.getHeldItem();
        harvestable =
                breakable
                        && allowed
                        && (!requiresShears(block)
                                || (held != null && held.getItem() == Item.shears));
    }

    private static boolean matches(List<Tool> capabilities, List<Tool> requirements) {
        for (Tool requirement : requirements) {
            for (Tool capability : capabilities) {
                if (requirement.toolClass.equals(capability.toolClass)
                        && capability.level >= requirement.level) return true;
            }
        }
        return false;
    }

    public static List<Tool> nativeTools(
            Block block, int metadata, float hardness, boolean requiresTool) {
        List<Tool> tools = new ArrayList<Tool>();
        if (hardness > 0 || requiresTool) {
            for (int i = 0; i < CLASSES.length; i++) {
                int level = MinecraftForge.getBlockHarvestLevel(block, metadata, CLASSES[i]);
                if (level >= 0) {
                    tools.add(new Tool(CLASSES[i], level, icon(CLASSES[i], level)));
                    continue;
                }
                for (int tier = 0; tier < ICONS[i].length; tier++) {
                    if (effective(ICONS[i][tier], block, metadata, requiresTool)) {
                        tools.add(new Tool(CLASSES[i], tier, new ItemStack(ICONS[i][tier])));
                        break;
                    }
                }
            }
            if (effective(Item.swordWood, block, metadata, requiresTool)) {
                tools.add(new Tool("sword", -1, new ItemStack(Item.swordWood)));
            }
        }
        if (requiresShears(block)
                || (block != Block.redstoneWire
                        && effective(Item.shears, block, metadata, requiresTool))) {
            tools.add(new Tool("shears", -1, new ItemStack(Item.shears)));
        }
        return tools;
    }

    public static ItemStack icon(String tool, int level) {
        for (int i = 0; i < CLASSES.length; i++) {
            if (CLASSES[i].equals(tool))
                return new ItemStack(ICONS[i][Math.max(0, Math.min(level, 3))]);
        }
        if ("sword".equals(tool)) return new ItemStack(Item.swordWood);
        if ("shears".equals(tool)) return new ItemStack(Item.shears);
        return null;
    }

    private static boolean requiresShears(Block block) {
        return block == Block.deadBush || block == Block.vine;
    }

    private static boolean effective(Item tool, Block block, int metadata, boolean requiresTool) {
        return tool.canHarvestBlock(block)
                || (!requiresTool
                        && tool.getStrVsBlock(new ItemStack(tool), block, metadata) > 1.0F);
    }
}
