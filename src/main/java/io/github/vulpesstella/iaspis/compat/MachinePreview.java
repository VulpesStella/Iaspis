package io.github.vulpesstella.iaspis.compat;

public final class MachinePreview {
    public enum EnergyUnit {
        EU,
        RF,
        MJ
    }

    public static final MachinePreview EMPTY = new MachinePreview(null, 0, 0, 0);

    public final EnergyUnit unit;
    public final double stored;
    public final double capacity;
    public final float progress;

    /** Native-unit limits per tick, or -1 when unavailable. */
    public final double maxInput;

    public final double maxOutput;

    public MachinePreview(EnergyUnit unit, double stored, double capacity, float progress) {
        this(unit, stored, capacity, progress, -1, -1);
    }

    public MachinePreview(
            EnergyUnit unit,
            double stored,
            double capacity,
            float progress,
            double maxInput,
            double maxOutput) {
        boolean energy = unit != null && finite(stored) && finite(capacity) && capacity > 0;
        this.unit = energy ? unit : null;
        this.stored = energy ? Math.max(0, stored) : 0;
        this.capacity = energy ? capacity : 0;
        this.progress = finite(progress) ? Math.max(0, Math.min(1, progress)) : 0;
        this.maxInput = energy && finite(maxInput) && maxInput >= 0 ? maxInput : -1;
        this.maxOutput = energy && finite(maxOutput) && maxOutput >= 0 ? maxOutput : -1;
    }

    public boolean isEmpty() {
        return unit == null && progress == 0;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
