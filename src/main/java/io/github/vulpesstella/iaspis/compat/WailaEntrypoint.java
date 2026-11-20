package io.github.vulpesstella.iaspis.compat;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInterModComms.IMCEvent;

@Mod(
        modid = "Waila",
        name = "Iaspis WAILA Compatibility",
        version = "1.5.2a",
        acceptedMinecraftVersions = "[1.6.4]")
public final class WailaEntrypoint {
    @Mod.EventHandler
    public void receive(IMCEvent event) {
        // FML delivers startup IMC after both entries have finished initialization.
        WailaBridge.INSTANCE.receive(event.getMessages());
    }
}
