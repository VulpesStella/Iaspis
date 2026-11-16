package io.github.vulpesstella.iaspis.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.api.HudComponent;
import io.github.vulpesstella.iaspis.api.HudProviders;
import io.github.vulpesstella.iaspis.api.HudTarget;
import net.minecraft.item.ItemStack;

public final class HudProviderRegistry {
    private static volatile List<Registration> registrations = Collections.emptyList();
    private static final Map<String, String> SETTINGS = new LinkedHashMap<String, String>();

    private HudProviderRegistry() {}

    public static synchronized void register(String id, String label, Class<?> type, int priority, HudProviders.Provider provider) {
        if (type == null || provider == null) throw new NullPointerException("HUD target type and provider are required");
        List<Registration> updated = new ArrayList<Registration>(registrations);
        updated.add(new Registration(id, type, priority, provider));
        Collections.sort(updated, new Comparator<Registration>() {
            @Override
            public int compare(Registration a, Registration b) {
                return Integer.compare(b.priority, a.priority);
            }
        });
        registrations = updated;
        if (id != null && !SETTINGS.containsKey(id)) SETTINGS.put(id, label);
    }

    public static synchronized Map<String, String> settings() {
        return new LinkedHashMap<String, String>(SETTINGS);
    }

    public static Changes collect(HudTarget target, boolean detailed) {
        Changes result = new Changes();
        for (Registration registration : registrations) {
            if (!registration.matches(target)) continue;
            if (registration.id != null && !Iaspis.config().getHudProviderMode(registration.id).shows(detailed)) continue;
            Changes changes = new Changes();
            try {
                registration.provider.update(target, detailed, changes);
            } catch (RuntimeException e) {
                report(registration, e);
                continue;
            } catch (LinkageError e) {
                report(registration, e);
                continue;
            }
            if (changes.hidden) return changes;
            if (result.name == null) result.name = changes.name;
            if (result.icon == null) result.icon = changes.icon;
            result.lines.addAll(changes.lines);
            result.components.addAll(changes.components);
        }
        return result;
    }

    private static void report(Registration registration, Throwable error) {
        WailaBridge.INSTANCE.report("Iaspis HUD provider " + registration.provider.getClass().getName(), error);
    }

    private static final class Registration {
        final String id;
        final Class<?> type;
        final int priority;
        final HudProviders.Provider provider;

        Registration(String id, Class<?> type, int priority, HudProviders.Provider provider) {
            this.id = id;
            this.type = type;
            this.priority = priority;
            this.provider = provider;
        }

        boolean matches(HudTarget target) {
            return type.isInstance(target.getBlock()) || type.isInstance(target.getTileEntity())
                    || type.isInstance(target.getEntity());
        }
    }

    public static final class Changes implements HudProviders.Tooltip {
        public String name;
        public ItemStack icon;
        public final List<String> lines = new ArrayList<String>();
        public final List<HudComponent> components = new ArrayList<HudComponent>();
        public boolean hidden;

        @Override
        public void setName(String name) { this.name = name; }

        @Override
        public void setIcon(ItemStack icon) {
            this.icon = icon == null || icon.getItem() == null ? null : icon.copy();
        }

        @Override
        public void addLine(String line) {
            if (line != null && !line.isEmpty()) lines.add(line);
        }

        @Override
        public void addComponent(HudComponent component) {
            if (component != null) components.add(component);
        }

        @Override
        public void hide() { hidden = true; }
    }
}
