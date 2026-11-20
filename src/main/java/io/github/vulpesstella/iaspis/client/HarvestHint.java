package io.github.vulpesstella.iaspis.client;

import java.util.ArrayList;
import java.util.List;

import io.github.vulpesstella.iaspis.compat.Target;
import io.github.vulpesstella.iaspis.compat.HarvestState;
import io.github.vulpesstella.iaspis.compat.HarvestRegistry;
import io.github.vulpesstella.iaspis.api.HarvestProviders;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

final class HarvestHint {
    final List<ToolIcon> icons;
    final boolean harvestable;
    final boolean showStatus;
    private final boolean requiresTool;

    private HarvestHint(List<ToolIcon> icons, boolean harvestable, boolean requiresTool) {
        this.icons = icons;
        this.harvestable = harvestable;
        this.requiresTool = requiresTool;
        this.showStatus = requiresTool || !harvestable;
    }

    static HarvestHint preview(Block block, boolean harvestable) {
        boolean required = !block.blockMaterial.isToolNotRequired() || block == Block.deadBush || block == Block.vine;
        return new HarvestHint(icons(HarvestState.nativeTools(block, 0, 3, required), null), harvestable, required);
    }

    static HarvestHint create(Target target, List<String> lines) {
        if (target.getPlayer() == null || (target.getPlayer().capabilities.isCreativeMode
                && !PreviewSample.HARVEST_CREATIVE.enabled())) return null;
        HarvestState state = target.getHarvestState();
        if (!state.breakable) {
            if (PreviewSample.HARVEST_UNBREAKABLE.enabled()) {
                lines.add(StatCollector.translateToLocal("iaspis.harvest.unbreakable"));
            }
            return null;
        }
        if (!state.requiresTool && !PreviewSample.HARVEST_EFFECTIVE.enabled()) return null;
        List<ToolIcon> icons = icons(state.tools, state.context);
        if (icons.isEmpty() && !state.requiresTool && state.harvestable) return null;
        return new HarvestHint(icons, state.harvestable, state.requiresTool);
    }

    private static List<ToolIcon> icons(List<HarvestProviders.Tool> tools, HarvestProviders.Context context) {
        List<ToolIcon> icons = new ArrayList<ToolIcon>();
        for (HarvestProviders.Tool tool : tools) {
            ItemStack icon = tool.getIcon();
            if (icon == null) icon = HarvestState.icon(tool.toolClass, tool.level);
            ToolIcon display = new ToolIcon(icon, tool.toolClass, tool.level);
            display.levelName = tool.level < 0 ? null : HarvestRegistry.levelName(context, tool.toolClass, tool.level);
            icons.add(display);
        }
        return icons;
    }
    void appendText(List<String> lines) {
        if (!icons.isEmpty()) {
            StringBuilder tools = new StringBuilder();
            for (ToolIcon icon : icons) {
                if (tools.length() > 0) tools.append(", ");
                tools.append(icon.text());
            }
            lines.add(StatCollector.translateToLocalFormatted(
                    requiresTool ? "iaspis.harvest.requiredTool" : "iaspis.harvest.effectiveTool", tools.toString()));
        }
        if (showStatus) {
            lines.add((harvestable ? "\u00a7a" : "\u00a7c") + StatCollector.translateToLocal(
                    harvestable ? "iaspis.harvest.canHarvest" : "iaspis.harvest.cannotHarvest"));
        }
    }

    static boolean canHarvest(Target target) {
        return target.getHarvestState().harvestable;
    }
    static final class ToolIcon {
        final ItemStack stack;
        final String levelBadge;
        final String toolClass;
        final int level;
        String levelName;

        ToolIcon(ItemStack stack, String toolClass, int level) {
            this.stack = stack;
            this.toolClass = toolClass;
            this.level = level;
            this.levelBadge = level > 3 ? Integer.toString(level) : "";
        }

        String text() {
            if (stack != null && level < 0) return stack.getDisplayName();
            if (stack != null && level <= 3 && (levelName == null
                    || levelName.equals(HarvestRegistry.levelName(null, toolClass, level)))) return stack.getDisplayName();
            String tool = StatCollector.translateToLocal("iaspis.harvest.tool." + toolClass);
            return level < 0 ? tool : StatCollector.translateToLocalFormatted(
                    "iaspis.harvest.toolTier", tool, levelName == null ? level : levelName);
        }
    }
}
