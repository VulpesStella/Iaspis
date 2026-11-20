package io.github.vulpesstella.iaspis.api;

import io.github.vulpesstella.iaspis.compat.HudProviderRegistry;
import net.minecraft.item.ItemStack;

/** Client-side registrations for the in-world Iaspis HUD. */
public final class HudProviders {
    public static final int API_VERSION = 1;
    /** Runtime capability check; unlike a constant, this call is not inlined into add-ons. */
    public static int getApiVersion() { return API_VERSION; }
    private HudProviders() {}

    /** Shares a display setting with server data sources without installing a HUD callback. */
    public static void registerSetting(String id, String label) {
        validate(id, label);
        HudProviderRegistry.registerSetting(id, label);
    }

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
     * Call during client mod initialization. The type can match a block, tile entity, entity or selected multipart,
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
        validate(id, label);
        HudProviderRegistry.register(id, label, type, priority, provider);
    }

    /** Register for the uniquely selected ForgeMultipart type ID, with the usual display setting. */
    public static void registerMultipart(String id, String label, String partType, int priority, Provider provider) {
        validate(id, label);
        if (partType == null || partType.isEmpty()) throw new IllegalArgumentException("A multipart type ID is required");
        HudProviderRegistry.registerMultipart(id, label, partType, priority, provider);
    }

    private static void validate(String id, String label) {
        if (id == null || id.length() > 64 || !id.matches("[a-z0-9_]+:[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Expected a namespaced HUD provider ID");
        }
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("A HUD provider label is required");
        }
    }
}
