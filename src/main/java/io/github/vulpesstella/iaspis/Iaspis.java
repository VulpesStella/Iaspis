package io.github.vulpesstella.iaspis;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import io.github.vulpesstella.iaspis.compat.TargetDataChannel;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import net.minecraft.tileentity.TileEntityFurnace;

@Mod(modid = BuildConstants.MOD_ID, name = BuildConstants.MOD_NAME,
        version = BuildConstants.MOD_VERSION, acceptedMinecraftVersions = "[1.6.4]")
@NetworkMod(channels = TargetDataChannel.NAME, packetHandler = TargetDataChannel.class,
        clientSideRequired = false, serverSideRequired = false)
public final class Iaspis {
    private static IaspisConfig config;

    @SidedProxy(clientSide = "io.github.vulpesstella.iaspis.client.ClientProxy",
            serverSide = "io.github.vulpesstella.iaspis.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new IaspisConfig(event.getSuggestedConfigurationFile());
        config.getBoolean("overlay.enabled");
        config.getBoolean("overlay.animation");
        config.getBoolean("inventory.preview");
        config.getBoolean("harvest.enabled");
        WailaBridge.INSTANCE.initialize(event.getModLog());
        WailaBridge.INSTANCE.registerSyncedNBTKey("CookTime", TileEntityFurnace.class);
    }

    public static IaspisConfig config() {
        return config;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}
