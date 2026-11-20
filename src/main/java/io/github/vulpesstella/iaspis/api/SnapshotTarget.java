package io.github.vulpesstella.iaspis.api;

import net.minecraft.nbt.NBTTagCompound;

/** Optional API 1 target. Only current, requested server snapshots are exposed. */
public interface SnapshotTarget extends HudTarget {
    /** Defensive copy, or null when unavailable, disabled or expired. */
    NBTTagCompound getSnapshot(String providerId);
}
