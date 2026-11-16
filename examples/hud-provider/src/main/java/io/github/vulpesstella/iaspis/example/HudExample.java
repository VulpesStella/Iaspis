package io.github.vulpesstella.iaspis.example;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;

@Mod(modid = HudExample.ID, name = "Iaspis HUD Example", version = "1.0",
        dependencies = "required-after:iaspis", acceptedMinecraftVersions = "[1.6.4]")
public final class HudExample {
    static final String ID = "iaspishudexample";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) RedstoneHud.register();
    }
}
