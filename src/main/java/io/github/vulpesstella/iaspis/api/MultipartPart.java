package io.github.vulpesstella.iaspis.api;

import net.minecraft.nbt.NBTTagCompound;

/** A frame-local selection. Indexes are valid only for the current tile and part list. */
public final class MultipartPart {
    private final String type;
    private final int index;
    private final Object part;
    private final NBTTagCompound snapshot;

    public MultipartPart(String type, int index, Object part, NBTTagCompound snapshot) {
        if (type == null || type.isEmpty() || index < 0 || part == null || snapshot == null) {
            throw new IllegalArgumentException(
                    "A multipart selection needs a type, index, instance and snapshot");
        }
        this.type = type;
        this.index = index;
        this.part = part;
        this.snapshot = (NBTTagCompound) snapshot.copy();
    }

    public String getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    /** Read-only game object. Do not retain it across target changes. */
    public Object getPart() {
        return part;
    }

    /** Data saved directly by the selected runtime part, independent of tile NBT list order. */
    public NBTTagCompound getSnapshot() {
        return (NBTTagCompound) snapshot.copy();
    }

    public boolean sameSelection(MultipartPart other) {
        return other != null
                && index == other.index
                && part == other.part
                && type.equals(other.type);
    }
}
