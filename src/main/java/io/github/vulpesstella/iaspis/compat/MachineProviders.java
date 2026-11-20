package io.github.vulpesstella.iaspis.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.vulpesstella.iaspis.api.HudProviders;

import net.minecraft.tileentity.TileEntity;

/** Registers read-only sources of machine data for the HUD. */
public final class MachineProviders {
    public interface Provider {
        MachinePreview read(TileEntity tile);
    }

    private static volatile List<Source> sources = Collections.emptyList();

    /**
     * Call during common mod initialization. The nearest registered superclass also covers subclasses.
     * The provider runs on the server, while clients use the registration to recognize the target.
     */
    public static synchronized void register(Class<? extends TileEntity> type, Provider provider) {
        if (type == null || provider == null) throw new NullPointerException("A machine type and source are required");
        List<Source> next = new ArrayList<Source>(sources);
        for (int i=next.size()-1;i>=0;i--) if (next.get(i).id == null && next.get(i).type == type) next.remove(i);
        next.add(new Source(null,type,0,provider));
        sources=Collections.unmodifiableList(next);
    }

    /** Named API 1 source. Interfaces are supported; matching concrete classes take precedence. */
    public static synchronized void register(String id, String label, Class<?> type, int priority, Provider provider) {
        if (type == null || type == TileEntity.class || provider == null
                || (!type.isInterface() && !TileEntity.class.isAssignableFrom(type))) {
            throw new IllegalArgumentException("A specific tile class or interface and source are required");
        }
        HudProviders.registerSetting(id,label);
        List<Source> next=new ArrayList<Source>(sources);
        next.add(new Source(id,type,priority,provider));
        sources=Collections.unmodifiableList(next);
    }

    public static boolean supports(TileEntity tile) {
        return source(tile) != null;
    }

    public static Source source(TileEntity tile) {
        if (tile == null) return null;
        Source best=null;
        for (Source candidate:sources) {
            if (!candidate.type.isInstance(tile)) continue;
            if (best == null || rank(candidate,tile)<rank(best,tile)
                    || (rank(candidate,tile)==rank(best,tile) && candidate.priority>best.priority)) best=candidate;
        }
        return best;
    }

    private static int rank(Source source, TileEntity tile) {
        if (source.type == TileEntity.class) return 20000;
        if (source.type.isInterface()) return 10000;
        int distance=0;
        for(Class<?> type=tile.getClass();type!=null;type=type.getSuperclass(),distance++) if(type==source.type)return distance;
        return Integer.MAX_VALUE;
    }

    public static boolean visible(Source source, boolean detailed, boolean server) {
        return source != null && (source.id == null || DetailRegistry.enabled(source.id,detailed,server));
    }

    static MachinePreview collect(TileEntity tile) {
        return collect(tile,source(tile),false);
    }

    public static MachinePreview collect(TileEntity tile, Source source, boolean detailed) {
        if (tile == null || !visible(source,detailed,true)) return MachinePreview.EMPTY;
        try {
            MachinePreview preview = source.provider.read(tile);
            if (preview != null) return preview;
        } catch (RuntimeException e) {
            WailaBridge.INSTANCE.report("Machine preview for " + tile.getClass().getName(), e);
        } catch (LinkageError e) {
            WailaBridge.INSTANCE.report("Machine preview for " + tile.getClass().getName(), e);
        }
        return MachinePreview.EMPTY;
    }

    public static final class Source {
        public final String id;
        final Class<?> type;
        final int priority;
        final Provider provider;
        Source(String id,Class<?> type,int priority,Provider provider){this.id=id;this.type=type;this.priority=priority;this.provider=provider;}
    }

    private MachineProviders() {}
}
