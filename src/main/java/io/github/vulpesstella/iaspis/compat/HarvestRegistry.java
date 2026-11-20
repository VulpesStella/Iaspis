package io.github.vulpesstella.iaspis.compat;

import io.github.vulpesstella.iaspis.api.HarvestProviders;
import io.github.vulpesstella.iaspis.api.HarvestProviders.Changes;
import io.github.vulpesstella.iaspis.api.HarvestProviders.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.StatCollector;

public final class HarvestRegistry {
    private static volatile List<Entry<HarvestProviders.Provider>> providers =
            Collections.emptyList();
    private static volatile List<Entry<HarvestProviders.LevelNames>> names =
            Collections.emptyList();

    public static synchronized void register(int priority, HarvestProviders.Provider provider) {
        providers = add(providers, priority, provider);
    }

    public static synchronized void registerNames(
            int priority, HarvestProviders.LevelNames provider) {
        names = add(names, priority, provider);
    }

    private static <T> List<Entry<T>> add(List<Entry<T>> current, int priority, T provider) {
        if (provider == null) throw new NullPointerException("A harvest provider is required");
        List<Entry<T>> updated = new ArrayList<Entry<T>>(current);
        updated.add(new Entry<T>(priority, provider));
        Collections.sort(
                updated,
                new Comparator<Entry<T>>() {
                    @Override
                    public int compare(Entry<T> a, Entry<T> b) {
                        return Integer.compare(b.priority, a.priority);
                    }
                });
        return Collections.unmodifiableList(updated);
    }

    public static Changes read(Context context) {
        Changes combined = new Changes(null, null, null, null);
        for (Entry<HarvestProviders.Provider> entry : providers) {
            try {
                Changes value = entry.provider.read(context);
                if (value == null) continue;
                combined =
                        new Changes(
                                combined.requirements == null
                                        ? value.requirements
                                        : combined.requirements,
                                combined.requiresTool == null
                                        ? value.requiresTool
                                        : combined.requiresTool,
                                combined.capabilities == null
                                        ? value.capabilities
                                        : combined.capabilities,
                                combined.harvestable == null
                                        ? value.harvestable
                                        : combined.harvestable);
            } catch (RuntimeException e) {
                report(entry.provider, e);
            } catch (LinkageError e) {
                report(entry.provider, e);
            }
        }
        return combined;
    }

    public static String levelName(Context context, String tool, int level) {
        if (context != null) {
            for (Entry<HarvestProviders.LevelNames> entry : names) {
                try {
                    String name = entry.provider.getName(context, tool, level);
                    if (name != null && !name.isEmpty()) return name;
                } catch (RuntimeException e) {
                    report(entry.provider, e);
                } catch (LinkageError e) {
                    report(entry.provider, e);
                }
            }
        }
        String key = "iaspis.harvestability.tier." + level;
        String translated = StatCollector.translateToLocal(key);
        return key.equals(translated) ? Integer.toString(level) : translated;
    }

    private static void report(Object provider, Throwable error) {
        WailaBridge.INSTANCE.report("Harvest provider " + provider.getClass().getName(), error);
    }

    private static final class Entry<T> {
        final int priority;
        final T provider;

        Entry(int priority, T provider) {
            this.priority = priority;
            this.provider = provider;
        }
    }

    private HarvestRegistry() {}
}
