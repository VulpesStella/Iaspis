package io.github.vulpesstella.iaspis.client;

import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import io.github.vulpesstella.iaspis.CommonProxy;
import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.IaspisConfig.DisplayMode;
import io.github.vulpesstella.iaspis.compat.InventoryPreview;
import io.github.vulpesstella.iaspis.compat.TankPreview;
import io.github.vulpesstella.iaspis.compat.TargetDataChannel;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetworkManager;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;

public final class ClientProxy extends CommonProxy {
    private final RemoteTargetData remote = new RemoteTargetData();
    private RemoteWailaOptions remoteOptions;

    @Override
    public void receiveDetails(INetworkManager manager, int sequence, NBTTagCompound data) {
        remote.details.receive(manager, sequence, data);
    }

    @Override
    public void init() {
        HudSettings.load();
        remoteOptions = new RemoteWailaOptions();
        TickRegistry.registerTickHandler(remoteOptions, Side.CLIENT);
        KeyBinding settings = new KeyBinding("key.iaspis.settings", Keyboard.KEY_ADD);
        final KeyBinding toggle = new KeyBinding("key.iaspis.toggle", Keyboard.KEY_NUMPAD1);
        final KeyBinding fluidKey = new KeyBinding("key.iaspis.fluid", Keyboard.KEY_NUMPAD2);
        final KeyBinding details = new KeyBinding("key.iaspis.details", Keyboard.KEY_NUMPAD3);
        final KeyBinding recipe = new KeyBinding("key.iaspis.recipe", Keyboard.KEY_NUMPAD4);
        final KeyBinding uses = new KeyBinding("key.iaspis.uses", Keyboard.KEY_NUMPAD5);
        final Overlay overlay = new Overlay(remote, toggle, details);
        MinecraftForge.EVENT_BUS.register(overlay);
        final KeyBinding settingsKey = settings;
        KeyBindingRegistry.registerKeyBinding(
                new KeyBindingRegistry.KeyHandler(
                        new KeyBinding[] {settings, toggle, fluidKey, details, recipe, uses},
                        new boolean[] {false, false, false, false, false, false}) {
                    @Override
                    public void keyDown(
                            EnumSet<TickType> types, KeyBinding key, boolean end, boolean repeat) {
                        Minecraft game = Minecraft.getMinecraft();
                        if (!end || game.currentScreen != null) return;
                        if (key == settingsKey)
                            game.displayGuiScreen(new HudSettingsScreen(overlay));
                        else if (key == fluidKey) {
                            HudSettings.NumberOption mode = HudSettings.NumberOption.FLUID_TARGET;
                            mode.set((mode.get() + 1) % (mode.max + 1));
                            Iaspis.config().save();
                            if (game.thePlayer != null) {
                                game.thePlayer.addChatMessage(
                                        StatCollector.translateToLocalFormatted(
                                                "iaspis.fluid.changed",
                                                StatCollector.translateToLocal(
                                                        "iaspis.settings.fluid.target."
                                                                + mode.get())));
                            }
                        } else if (key == recipe || key == uses) {
                            NeiLookup.open(game, overlay, key == uses);
                        } else if (key == toggle
                                && HudSettings.NumberOption.MODE.get() != HudSettings.HOLD) {
                            Iaspis.config()
                                    .setBoolean(
                                            "overlay.enabled",
                                            !Iaspis.config().getBoolean("overlay.enabled"));
                            Iaspis.config().save();
                        }
                    }

                    @Override
                    public void keyUp(EnumSet<TickType> types, KeyBinding key, boolean end) {}

                    @Override
                    public EnumSet<TickType> ticks() {
                        return EnumSet.of(TickType.CLIENT);
                    }

                    @Override
                    public String getLabel() {
                        return "Iaspis";
                    }
                });
    }

    @Override
    public boolean hasWailaServer() {
        Minecraft game = Minecraft.getMinecraft();
        return game.thePlayer != null && TargetDataChannel.canRequest(game.thePlayer);
    }

    @Override
    public Boolean forcedWailaOption(String key) {
        return remoteOptions == null ? null : remoteOptions.forced(key);
    }

    @Override
    public void receiveWailaOptions(INetworkManager manager, Map<String, Boolean> options) {
        if (remoteOptions != null) remoteOptions.receive(manager, options);
    }

    @Override
    public DisplayMode forcedHudProviderMode(String id) {
        return remoteOptions == null ? null : remoteOptions.forcedHudProviderMode(id);
    }

    @Override
    public void receiveHudProviderModes(INetworkManager manager, Map<String, DisplayMode> modes) {
        if (remoteOptions != null) remoteOptions.receiveHudProviderModes(manager, modes);
    }

    @Override
    public void receiveTargetData(
            int sequence,
            NBTTagCompound data,
            InventoryPreview inventory,
            List<TankPreview> tanks) {
        remote.receive(sequence, data, inventory, tanks);
    }
}
