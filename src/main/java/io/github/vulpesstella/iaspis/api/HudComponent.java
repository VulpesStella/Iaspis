package io.github.vulpesstella.iaspis.api;

import net.minecraft.client.gui.FontRenderer;

/** A custom HUD body row in scaled GUI coordinates. */
public interface HudComponent {
    int getWidth(FontRenderer font);
    int getHeight(FontRenderer font);

    /** Draw within the measured rectangle. The component may be rendered again during a HUD fade. */
    void render(FontRenderer font, int x, int y);
}
