package io.github.vulpesstella.iaspis;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import io.github.vulpesstella.iaspis.compat.TargetDataChannel;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.common.Configuration;

@Mod(modid = BuildConstants.MOD_ID, name = BuildConstants.MOD_NAME,
        version = BuildConstants.MOD_VERSION, acceptedMinecraftVersions = "[1.6.4]")
@NetworkMod(channels = TargetDataChannel.NAME, packetHandler = TargetDataChannel.class,
        clientSideRequired = false, serverSideRequired = false)
public final class Iaspis {
    @SidedProxy(clientSide = "io.github.vulpesstella.iaspis.client.ClientProxy",
            serverSide = "io.github.vulpesstella.iaspis.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        WailaBridge.INSTANCE.configure(config, event.getModLog());
        WailaBridge.INSTANCE.registerSyncedNBTKey("CookTime", TileEntityFurnace.class);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}
