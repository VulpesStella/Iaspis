package io.github.vulpesstella.iaspis.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.IaspisConfig.DisplayMode;
import io.github.vulpesstella.iaspis.api.DetailProviders;
import net.minecraft.nbt.NBTTagCompound;

public final class DetailRegistry {
    private static volatile List<Entry> entries = Collections.emptyList();

    public static synchronized void register(String id, Object type, int priority, DetailProviders.Provider provider) {
        List<Entry> next = new ArrayList<Entry>(entries);
        next.add(new Entry(id, type, priority, provider));
        Collections.sort(next, new Comparator<Entry>() {
            public int compare(Entry a, Entry b) { return Integer.compare(b.priority, a.priority); }
        });
        entries = Collections.unmodifiableList(next);
    }

    public static boolean matches(Object type, Target target) {
        if (type instanceof String) return target.getMultipartPart() != null
                && type.equals(target.getMultipartPart().getType());
        Class<?> cls = (Class<?>) type;
        return cls.isInstance(target.getBlock()) || cls.isInstance(target.getTileEntity())
                || (target.getMultipartPart() != null && cls.isInstance(target.getMultipartPart().getPart()));
    }

    public static boolean enabled(String id, boolean detailed, boolean server) {
        DisplayMode mode = server ? Iaspis.config().lockedHudProviderModes().get(id) : HudProviderRegistry.mode(id);
        if (mode == null) mode = Iaspis.config().getHudProviderMode(id);
        return mode.shows(detailed);
    }

    public static List<String> ids(Target target, boolean detailed) {
        Set<String> ids = new LinkedHashSet<String>();
        for (Entry entry : entries) {
            if (matches(entry.type, target) && enabled(entry.id, detailed, false)) ids.add(entry.id);
        }
        return new ArrayList<String>(ids);
    }

    public static NBTTagCompound collect(String id, Target target, boolean detailed) {
        if (!enabled(id, detailed, true)) return null;
        for (Entry entry : entries) {
            if (!entry.id.equals(id) || !matches(entry.type, target)) continue;
            try {
                NBTTagCompound tag = entry.provider.read(target);
                if (tag != null) return (NBTTagCompound) tag.copy();
            } catch (RuntimeException e) { WailaBridge.INSTANCE.report("Detail source " + id, e);
            } catch (LinkageError e) { WailaBridge.INSTANCE.report("Detail source " + id, e); }
        }
        return null;
    }

    private static final class Entry {
        final String id;
        final Object type;
        final int priority;
        final DetailProviders.Provider provider;
        Entry(String id, Object type, int priority, DetailProviders.Provider provider) {
            this.id=id; this.type=type; this.priority=priority; this.provider=provider;
        }
    }
    private DetailRegistry() {}
}
