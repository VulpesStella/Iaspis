package io.github.vulpesstella.iaspis.api;

import net.minecraft.nbt.NBTTagCompound;

/** Optional target exposing only current, requested server snapshots. */
public interface SnapshotTarget extends HudTarget {
    /** Defensive copy, or null when unavailable, disabled or expired. */
    NBTTagCompound getSnapshot(String providerId);
}
