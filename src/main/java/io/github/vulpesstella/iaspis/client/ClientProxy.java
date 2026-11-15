package io.github.vulpesstella.iaspis.client;

import java.util.EnumSet;

import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.common.TickType;
import io.github.vulpesstella.iaspis.CommonProxy;
import io.github.vulpesstella.iaspis.compat.InventoryPreview;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;

public final class ClientProxy extends CommonProxy {
    private final RemoteTargetData remote = new RemoteTargetData();

    @Override
    public void init() {
        MinecraftForge.EVENT_BUS.register(new Overlay(remote));
        KeyBinding toggle = new KeyBinding("key.iaspis.toggle", Keyboard.KEY_NUMPAD1);
        KeyBindingRegistry.registerKeyBinding(new KeyBindingRegistry.KeyHandler(
                new KeyBinding[] { toggle }, new boolean[] { false }) {
            @Override
            public void keyDown(EnumSet<TickType> types, KeyBinding key, boolean end, boolean repeat) {
                if (end && Minecraft.getMinecraft().currentScreen == null) {
                    WailaBridge.INSTANCE.toggleOverlay();
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
