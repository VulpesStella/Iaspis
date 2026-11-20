package io.github.vulpesstella.iaspis.api;

import io.github.vulpesstella.iaspis.compat.MachinePreview;
import io.github.vulpesstella.iaspis.compat.MachinePreview.EnergyUnit;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Client-only unit preferences. Every callback receives the original server value. */
public final class MachineProjections {
    public interface Projection {
        /** Null defers. Inspect local configuration here; do not modify game state. */
        Conversion project(HudTarget target, MachinePreview serverValue);
    }

    public static final class Conversion {
        public final EnergyUnit unit;
        public final double multiplier;

        public Conversion(EnergyUnit unit, double multiplier) {
            if (unit == null
                    || Double.isNaN(multiplier)
                    || Double.isInfinite(multiplier)
                    || multiplier <= 0) {
                throw new IllegalArgumentException(
                        "A unit and positive finite multiplier are required");
            }
            this.unit = unit;
            this.multiplier = multiplier;
        }
    }

    private static volatile List<Entry> entries = Collections.emptyList();

    public static synchronized void register(String id, int priority, Projection projection) {
        if (id == null
                || id.length() > 64
                || !id.matches("[a-z0-9_]+:[a-z0-9_.-]+")
                || projection == null) {
            throw new IllegalArgumentException(
                    "A namespaced machine source ID and projection are required");
        }
        List<Entry> next = new ArrayList<Entry>(entries);
        next.add(new Entry(id, priority, projection));
        Collections.sort(
                next,
                new Comparator<Entry>() {
                    public int compare(Entry a, Entry b) {
                        return Integer.compare(b.priority, a.priority);
                    }
                });
        entries = Collections.unmodifiableList(next);
    }

    public static MachinePreview project(HudTarget target, String id, MachinePreview raw) {
        if (raw.unit == null || id == null) return raw;
        for (Entry entry : entries) {
            if (!entry.id.equals(id)) continue;
            try {
                Conversion conversion = entry.projection.project(target, raw);
                if (conversion == null) continue;
                double stored = raw.stored * conversion.multiplier,
                        capacity = raw.capacity * conversion.multiplier;
                double input = raw.maxInput < 0 ? -1 : raw.maxInput * conversion.multiplier;
                double output = raw.maxOutput < 0 ? -1 : raw.maxOutput * conversion.multiplier;
                if (!finite(stored)
                        || !finite(capacity)
                        || capacity <= 0
                        || !finite(input)
                        || !finite(output)) continue;
                return new MachinePreview(
                        conversion.unit, stored, capacity, raw.progress, input, output);
            } catch (RuntimeException e) {
                WailaBridge.INSTANCE.report("Machine projection " + id, e);
            } catch (LinkageError e) {
                WailaBridge.INSTANCE.report("Machine projection " + id, e);
            }
        }
        return raw;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class Entry {
        final String id;
        final int priority;
        final Projection projection;

        Entry(String id, int priority, Projection projection) {
            this.id = id;
            this.priority = priority;
            this.projection = projection;
        }
    }

    private MachineProjections() {}
}
