package io.github.vulpesstella.iaspis;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import io.github.vulpesstella.iaspis.compat.DetailsChannel;
import io.github.vulpesstella.iaspis.compat.TargetDataChannel;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import io.github.vulpesstella.iaspis.compat.WailaOptionsChannel;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityNote;
import net.minecraft.tileentity.TileEntityRecordPlayer;

@Mod(
        modid = BuildConstants.MOD_ID,
        name = BuildConstants.MOD_NAME,
        version = BuildConstants.MOD_VERSION,
        acceptedMinecraftVersions = "[1.6.4]")
@NetworkMod(
        channels = {
            TargetDataChannel.NAME,
            TargetDataChannel.TANK_CAPABILITY,
            WailaOptionsChannel.NAME,
            DetailsChannel.NAME
        },
        packetHandler = TargetDataChannel.class,
        clientSideRequired = false,
        serverSideRequired = false)
public final class Iaspis {
    private static IaspisConfig config;

    @SidedProxy(
            clientSide = "io.github.vulpesstella.iaspis.client.ClientProxy",
            serverSide = "io.github.vulpesstella.iaspis.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new IaspisConfig(event.getSuggestedConfigurationFile());
        config.getBoolean("overlay.enabled");
        config.getBoolean("overlay.animation");
        config.getBoolean("inventory.preview");
        config.getTankDisplay();
        config.getBoolean("harvest.enabled");
        WailaBridge.INSTANCE.initialize(event.getModLog());
        WailaBridge.INSTANCE.registerSyncedNBTKey("CookTime", TileEntityFurnace.class);
        WailaBridge.INSTANCE.registerSyncedNBTKey("BrewTime", TileEntityBrewingStand.class);
        WailaBridge.INSTANCE.registerSyncedNBTKey(
                TargetDataChannel.HOPPER_LOCKED, TileEntityHopper.class);
        WailaBridge.INSTANCE.registerSyncedNBTKey("note", TileEntityNote.class);
        WailaBridge.INSTANCE.registerSyncedNBTKey("Record", TileEntityRecordPlayer.class);
    }

    public static IaspisConfig config() {
        return config;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}
