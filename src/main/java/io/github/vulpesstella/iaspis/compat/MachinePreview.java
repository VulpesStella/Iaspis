package io.github.vulpesstella.iaspis.compat;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;

public final class MachinePreview {
    public enum EnergyUnit {
        EU(1),
        RF(2),
        MJ(3);

        final int id;

        EnergyUnit(int id) {
            this.id = id;
        }

        static EnergyUnit read(int id) throws IOException {
            if (id == 0) return null;
            for (EnergyUnit unit : values()) if (unit.id == id) return unit;
            throw new IOException("Unknown machine energy unit");
        }
    }

    private static final int MARKER = -2;
    private static final int TRAILER_BYTES = Integer.SIZE / Byte.SIZE + 1
            + 2 * Double.SIZE / Byte.SIZE + Float.SIZE / Byte.SIZE;
    public static final MachinePreview EMPTY = new MachinePreview(null, 0, 0, 0);

    public final EnergyUnit unit;
    public final double stored;
    public final double capacity;
    public final float progress;

    public MachinePreview(EnergyUnit unit, double stored, double capacity, float progress) {
        boolean energy = unit != null && finite(stored) && finite(capacity) && capacity > 0;
        this.unit = energy ? unit : null;
        this.stored = energy ? Math.max(0, stored) : 0;
        this.capacity = energy ? capacity : 0;
        this.progress = finite(progress) ? Math.max(0, Math.min(1, progress)) : 0;
    }

    public boolean isEmpty() {
        return unit == null && progress == 0;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    void write(DataOutput output) throws IOException {
        if (isEmpty()) return;
        output.writeInt(MARKER);
        output.writeByte(unit == null ? 0 : unit.id);
        output.writeDouble(stored);
        output.writeDouble(capacity);
        output.writeFloat(progress);
    }

    static MachinePreview read(DataInputStream input) throws IOException {
        if (input.available() == 0) return EMPTY;
        if (input.available() != TRAILER_BYTES || input.readInt() != MARKER) {
            throw new IOException("Invalid machine preview");
        }
        EnergyUnit unit = EnergyUnit.read(input.readUnsignedByte());
        return new MachinePreview(unit, input.readDouble(), input.readDouble(), input.readFloat());
    }
}
