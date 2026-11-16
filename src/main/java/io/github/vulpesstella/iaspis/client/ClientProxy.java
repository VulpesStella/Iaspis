package io.github.vulpesstella.iaspis.client;

import java.util.EnumSet;
import java.util.List;

import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.common.TickType;
import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.CommonProxy;
import io.github.vulpesstella.iaspis.compat.InventoryPreview;
import io.github.vulpesstella.iaspis.compat.MachinePreview;
import io.github.vulpesstella.iaspis.compat.TankPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;

public final class ClientProxy extends CommonProxy {
    private final RemoteTargetData remote = new RemoteTargetData();

    @Override
    public void init() {
        HudSettings.load();
        KeyBinding settings = new KeyBinding("key.iaspis.settings", Keyboard.KEY_NUMPAD0);
        KeyBinding toggle = new KeyBinding("key.iaspis.toggle", Keyboard.KEY_NUMPAD1);
        final KeyBinding fluidKey = new KeyBinding("key.iaspis.fluid", Keyboard.KEY_NUMPAD2);
        final Overlay overlay = new Overlay(remote, toggle);
        MinecraftForge.EVENT_BUS.register(overlay);
        final KeyBinding settingsKey = settings;
        KeyBindingRegistry.registerKeyBinding(new KeyBindingRegistry.KeyHandler(
                new KeyBinding[] { settings, toggle, fluidKey }, new boolean[] { false, false, false }) {
            @Override
            public void keyDown(EnumSet<TickType> types, KeyBinding key, boolean end, boolean repeat) {
                Minecraft game = Minecraft.getMinecraft();
                if (!end || game.currentScreen != null) return;
                if (key == settingsKey) game.displayGuiScreen(new HudSettingsScreen(overlay));
                else if (key == fluidKey) {
                    HudSettings.NumberOption mode = HudSettings.NumberOption.FLUID_TARGET;
                    mode.set((mode.get() + 1) % (mode.max + 1));
                    Iaspis.config().save();
                    if (game.thePlayer != null) {
                        game.thePlayer.addChatMessage(StatCollector.translateToLocalFormatted(
                                "iaspis.fluid.changed", StatCollector.translateToLocal(
                                        "iaspis.settings.fluid.target." + mode.get())));
                    }
                } else if (HudSettings.NumberOption.MODE.get() != HudSettings.HOLD) {
                    Iaspis.config().setBoolean("overlay.enabled", !Iaspis.config().getBoolean("overlay.enabled"));
                    Iaspis.config().save();
                }
            }

            @Override
            public void keyUp(EnumSet<TickType> types, KeyBinding key, boolean end) {}

            @Override
            public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

            @Override
            public String getLabel() { return "Iaspis"; }
        });
    }

    @Override
    public void receiveTargetData(int sequence, NBTTagCompound data, InventoryPreview inventory, List<TankPreview> tanks, MachinePreview machine) {
        remote.receive(sequence, data, inventory, tanks, machine);
    }
}
