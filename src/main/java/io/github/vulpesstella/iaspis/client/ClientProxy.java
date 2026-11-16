package io.github.vulpesstella.iaspis.client;

import java.util.EnumSet;

import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.common.TickType;
import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.CommonProxy;
import io.github.vulpesstella.iaspis.compat.InventoryPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;

public final class ClientProxy extends CommonProxy {
    private final RemoteTargetData remote = new RemoteTargetData();

    @Override
    public void init() {
        HudSettings.load();
        KeyBinding settings = new KeyBinding("key.iaspis.settings", Keyboard.KEY_NUMPAD0);
        KeyBinding toggle = new KeyBinding("key.iaspis.toggle", Keyboard.KEY_NUMPAD1);
        final Overlay overlay = new Overlay(remote, toggle);
        MinecraftForge.EVENT_BUS.register(overlay);
        final KeyBinding settingsKey = settings;
        KeyBindingRegistry.registerKeyBinding(new KeyBindingRegistry.KeyHandler(
                new KeyBinding[] { settings, toggle }, new boolean[] { false, false }) {
            @Override
            public void keyDown(EnumSet<TickType> types, KeyBinding key, boolean end, boolean repeat) {
                Minecraft game = Minecraft.getMinecraft();
                if (!end || game.currentScreen != null) return;
                if (key == settingsKey) game.displayGuiScreen(new HudSettingsScreen(overlay));
                else if (HudSettings.NumberOption.MODE.get() != HudSettings.HOLD) {
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
    public void receiveTargetData(int sequence, NBTTagCompound data, InventoryPreview inventory) {
        remote.receive(sequence, data, inventory);
    }
}
