package io.github.vulpesstella.iaspis.api;

import io.github.vulpesstella.iaspis.compat.HudProviderRegistry;
import net.minecraft.item.ItemStack;

/** Client-side registrations for the in-world Iaspis HUD. */
public final class HudProviders {
    private HudProviders() {}

    public interface Provider {
        /** Called while the registered target is aimed at. */
        void update(HudTarget target, boolean detailed, Tooltip tooltip);
    }

    public interface Tooltip {
        void setName(String name);
        void setIcon(ItemStack icon);
        /** Adds a body line, shown when the HUD is displaying its body. */
        void addLine(String line);
        /** Adds a measured visual row, shown when the HUD is displaying its body. */
        void addComponent(HudComponent component);
        /** Suppresses the tooltip for this target immediately. */
        void hide();
    }

    /**
     * Call during client mod initialization. The type can match a block, tile entity, or entity,
     * including subclasses and implemented interfaces. Higher priorities run first and win name
     * and icon overrides. Ties follow registration order.
     */
    public static void register(Class<?> type, int priority, Provider provider) {
        HudProviderRegistry.register(null, null, type, priority, provider);
    }

    /**
     * Registers a provider with a player setting, initially On. Use a stable ID prefixed with
     * your mod ID (for example, "example:machine_status"). Registrations sharing an ID use
     * the same setting. The label is translated using the client's language.
     */
    public static void register(String id, String label, Class<?> type, int priority, Provider provider) {
        if (id == null || !id.matches("[a-z0-9_]+:[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Expected a namespaced HUD provider ID");
        }
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("A HUD provider label is required");
        }
        HudProviderRegistry.register(id, label, type, priority, provider);
    }
}
