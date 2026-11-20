package io.github.vulpesstella.iaspis;

import java.util.List;
import java.util.Map;

import io.github.vulpesstella.iaspis.IaspisConfig.DisplayMode;
import io.github.vulpesstella.iaspis.compat.InventoryPreview;
import io.github.vulpesstella.iaspis.compat.TankPreview;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetworkManager;

public class CommonProxy {
    public void receiveDetails(INetworkManager manager,int sequence,NBTTagCompound data) {}
    public void init() {
    }

    public boolean hasWailaServer() { return true; }

    public Boolean forcedWailaOption(String key) { return null; }

    public void receiveWailaOptions(INetworkManager manager, Map<String, Boolean> options) {}

    public DisplayMode forcedHudProviderMode(String id) { return null; }

    public void receiveHudProviderModes(INetworkManager manager, Map<String, DisplayMode> modes) {}

    public void receiveTargetData(int sequence, NBTTagCompound data, InventoryPreview inventory, List<TankPreview> tanks) {
    }
}
