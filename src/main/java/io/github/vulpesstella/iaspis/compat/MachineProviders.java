package io.github.vulpesstella.iaspis.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.tileentity.TileEntity;

/** Registers read-only sources of machine data for the HUD. */
public final class MachineProviders {
    public interface Provider {
        MachinePreview read(TileEntity tile);
    }

    private static final Map<Class<? extends TileEntity>, Provider> PROVIDERS =
            new ConcurrentHashMap<Class<? extends TileEntity>, Provider>();

    /**
     * Call during common mod initialization. The nearest registered superclass also covers subclasses.
     * The provider runs on the server, while clients use the registration to recognize the target.
     */
    public static void register(Class<? extends TileEntity> type, Provider provider) {
        PROVIDERS.put(type, provider);
    }

    public static boolean supports(TileEntity tile) {
        return tile != null && providerFor(tile) != null;
    }

    static MachinePreview collect(TileEntity tile) {
        if (tile == null) return MachinePreview.EMPTY;
        Provider provider = providerFor(tile);
        if (provider == null) return MachinePreview.EMPTY;
        try {
            MachinePreview preview = provider.read(tile);
            if (preview != null) return preview;
        } catch (RuntimeException e) {
            WailaBridge.INSTANCE.report("Machine preview for " + tile.getClass().getName(), e);
        } catch (LinkageError e) {
            WailaBridge.INSTANCE.report("Machine preview for " + tile.getClass().getName(), e);
        }
        return MachinePreview.EMPTY;
    }

    private static Provider providerFor(TileEntity tile) {
        for (Class<?> type = tile.getClass(); type != TileEntity.class; type = type.getSuperclass()) {
            Provider provider = PROVIDERS.get(type);
            if (provider != null) return provider;
        }
        return PROVIDERS.get(TileEntity.class);
    }

    private MachineProviders() {}
}
