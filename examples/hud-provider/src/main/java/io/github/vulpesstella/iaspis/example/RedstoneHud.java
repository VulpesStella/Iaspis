package io.github.vulpesstella.iaspis.example;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.github.vulpesstella.iaspis.api.HudComponent;
import io.github.vulpesstella.iaspis.api.HudProviders;
import io.github.vulpesstella.iaspis.api.HudTarget;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.StatCollector;

@SideOnly(Side.CLIENT)
final class RedstoneHud {
    private RedstoneHud() {}

    static void register() {
        HudProviders.register(
                HudExample.ID + ":redstone_signal",
                "iaspishudexample.redstone",
                BlockRedstoneWire.class,
                0,
                new HudProviders.Provider() {
                    @Override
                    public void update(
                            HudTarget target, boolean detailed, HudProviders.Tooltip tooltip) {
                        tooltip.addComponent(new SignalBar(target.getMetadata()));
                    }
                });
    }

    private static final class SignalBar extends Gui implements HudComponent {
        private static final int BAR_WIDTH = 32;
        private static final int MAX_SIGNAL = 15;
        private final int signal;
        private final String label;

        SignalBar(int signal) {
            this.signal = signal;
            label =
                    StatCollector.translateToLocalFormatted(
                            "iaspishudexample.signal", signal, MAX_SIGNAL);
        }

        @Override
        public int getWidth(FontRenderer font) {
            return BAR_WIDTH + 5 + font.getStringWidth(label);
        }

        @Override
        public int getHeight(FontRenderer font) {
            return font.FONT_HEIGHT + 2;
        }

        @Override
        public void render(FontRenderer font, int x, int y) {
            drawRect(x, y + 2, x + BAR_WIDTH, y + 8, 0xff392525);
            int fill = BAR_WIDTH * signal / MAX_SIGNAL;
            if (fill > 0) drawRect(x, y + 2, x + fill, y + 8, 0xffe64a39);
            font.drawStringWithShadow(label, x + BAR_WIDTH + 5, y + 1, 0xffffff);
        }
    }
}
