package io.github.vulpesstella.iaspis.api;

import io.github.vulpesstella.iaspis.compat.DetailRegistry;
import net.minecraft.nbt.NBTTagCompound;

/** Read-only, server-side details registered on both sides during common initialization. */
public final class DetailProviders {
    public interface Provider {
        /** Return only necessary fields; null defers to another registration for the same ID. */
        NBTTagCompound read(HudTarget target);
    }

    public static void register(
            String id, String label, Class<?> type, int priority, Provider provider) {
        if (type == null || provider == null)
            throw new NullPointerException("A target type and source are required");
        HudProviders.registerSetting(id, label);
        DetailRegistry.register(id, type, priority, provider);
    }

    public static void registerMultipart(
            String id, String label, String partType, int priority, Provider provider) {
        if (partType == null || partType.isEmpty() || provider == null)
            throw new IllegalArgumentException("A part type and source are required");
        HudProviders.registerSetting(id, label);
        DetailRegistry.register(id, partType, priority, provider);
    }

    public static NBTTagCompound get(HudTarget target, String id) {
        return target instanceof SnapshotTarget ? ((SnapshotTarget) target).getSnapshot(id) : null;
    }

    private DetailProviders() {}
}
