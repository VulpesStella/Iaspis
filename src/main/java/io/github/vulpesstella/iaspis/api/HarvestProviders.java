package io.github.vulpesstella.iaspis.api;

import io.github.vulpesstella.iaspis.compat.HarvestRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.ItemStack;

/** Contextual, read-only harvest extensions. Higher priorities run first. */
public final class HarvestProviders {
    public interface Provider {
        /** Return null to defer. Each non-null field overrides the corresponding native input. */
        Changes read(Context context);
    }

    public interface LevelNames {
        /** Return null to use the next provider, built-in translation or numeric fallback. */
        String getName(Context context, String toolClass, int level);
    }

    public static final class Context {
        private final HudTarget target;
        private final ItemStack held;

        public Context(HudTarget target) {
            this.target = target;
            ItemStack equipped =
                    target.getPlayer() == null ? null : target.getPlayer().getCurrentEquippedItem();
            held = equipped == null ? null : equipped.copy();
        }

        public HudTarget getTarget() {
            return target;
        }

        public ItemStack getHeldItem() {
            return held == null ? null : held.copy();
        }
    }

    /**
     * One tool class with its independent level. Icon is optional for capabilities and
     * requirements.
     */
    public static final class Tool {
        public final String toolClass;
        public final int level;
        private final ItemStack icon;

        public Tool(String toolClass, int level) {
            this(toolClass, level, null);
        }

        public Tool(String toolClass, int level, ItemStack icon) {
            if (toolClass == null || toolClass.isEmpty() || level < -1) {
                throw new IllegalArgumentException("A tool class and level >= -1 are required");
            }
            this.toolClass = toolClass;
            this.level = level;
            this.icon = icon == null ? null : icon.copy();
        }

        public ItemStack getIcon() {
            return icon == null ? null : icon.copy();
        }
    }

    public static final class Changes {
        public final List<Tool> requirements;
        public final Boolean requiresTool;
        public final List<Tool> capabilities;
        public final Boolean harvestable;

        /**
         * Null means defer. Requirements are alternatives, matched by class AND level. Empty
         * capabilities represent a held item with no harvest abilities. Explicit harvestable
         * overrides capability matching and the native Forge result. Unbreakable blocks and vanilla
         * required-shears targets retain their constraints.
         */
        public Changes(
                List<Tool> requirements,
                Boolean requiresTool,
                List<Tool> capabilities,
                Boolean harvestable) {
            this.requirements = copy(requirements);
            this.requiresTool = requiresTool;
            this.capabilities = copy(capabilities);
            this.harvestable = harvestable;
        }

        private static List<Tool> copy(List<Tool> tools) {
            if (tools == null) return null;
            if (tools.contains(null)) throw new IllegalArgumentException("Null tool entry");
            return Collections.unmodifiableList(new ArrayList<Tool>(tools));
        }
    }

    public static void register(int priority, Provider provider) {
        HarvestRegistry.register(priority, provider);
    }

    public static void registerLevelNames(int priority, LevelNames provider) {
        HarvestRegistry.registerNames(priority, provider);
    }

    private HarvestProviders() {}
}
