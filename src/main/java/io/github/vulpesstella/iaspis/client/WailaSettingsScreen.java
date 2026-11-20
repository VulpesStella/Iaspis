package io.github.vulpesstella.iaspis.client;

import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.IaspisConfig.DisplayMode;
import io.github.vulpesstella.iaspis.compat.HudProviderRegistry;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.StatCollector;

final class WailaSettingsScreen extends GuiScreen {
    private static final WailaBridge BRIDGE = WailaBridge.INSTANCE;
    private static final DisplayMode[] DISPLAY_MODES = DisplayMode.values();
    private final GuiScreen parent;
    private final List<OptionRow> rows = new ArrayList<OptionRow>();
    private GuiSlot options;

    WailaSettingsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        rows.clear();
        Map<String, String> nativeProviders = HudProviderRegistry.settings();
        if (!nativeProviders.isEmpty()) {
            rows.add(new OptionRow(null, translate("iaspis.extensions.providers"), true));
            for (Map.Entry<String, String> entry : nativeProviders.entrySet()) {
                rows.add(new OptionRow(entry.getKey(), translate(entry.getValue()), true));
            }
        }
        for (String module : BRIDGE.getModuleNames()) {
            rows.add(new OptionRow(null, module, false));
            for (Map.Entry<String, String> entry : BRIDGE.getConfigKeys(module).entrySet()) {
                rows.add(new OptionRow(entry.getKey(), entry.getValue(), false));
            }
        }
        buttonList.clear();
        buttonList.add(
                new GuiButton(0, width / 2 - 60, height - 29, 120, 20, translate("gui.done")));
        options =
                new GuiSlot(mc, width, height, 30, height - 38, 22) {
                    protected int getSize() {
                        return rows.size();
                    }

                    protected void elementClicked(int rowIndex, boolean doubleClick) {
                        OptionRow row = rows.get(rowIndex);
                        if (row.key == null || locked(row) || doubleClick) return;
                        if (row.nativeProvider) {
                            DisplayMode current = Iaspis.config().getHudProviderMode(row.key);
                            Iaspis.config()
                                    .setHudProviderMode(
                                            row.key,
                                            DISPLAY_MODES[
                                                    (current.ordinal() + 1)
                                                            % DISPLAY_MODES.length]);
                        } else {
                            Iaspis.config().setBoolean(row.key, !BRIDGE.getConfig(row.key));
                            Iaspis.config().save();
                        }
                    }

                    protected boolean isSelected(int rowIndex) {
                        return false;
                    }

                    protected void drawBackground() {
                        WailaSettingsScreen.this.drawDefaultBackground();
                    }

                    protected void drawSlot(
                            int rowIndex, int x, int y, int slotHeight, Tessellator tessellator) {
                        OptionRow row = rows.get(rowIndex);
                        if (row.key == null) {
                            drawString(fontRenderer, row.label, x, y + 6, 0xffbcade8);
                            return;
                        }
                        boolean locked = locked(row);
                        String value =
                                row.nativeProvider
                                        ? translate(
                                                "iaspis.extensions.mode."
                                                        + HudProviderRegistry.mode(row.key)
                                                                .ordinal())
                                        : translate(
                                                BRIDGE.getConfig(row.key)
                                                        ? "options.on"
                                                        : "options.off");
                        if (locked)
                            value =
                                    StatCollector.translateToLocalFormatted(
                                            "iaspis.waila.locked", value);
                        int valueX = x + 210 - fontRenderer.getStringWidth(value);
                        drawString(
                                fontRenderer,
                                fontRenderer.trimStringToWidth(
                                        row.label, Math.max(1, valueX - x - 16)),
                                x + 8,
                                y + 6,
                                locked ? 0xffaaaaaa : 0xffffffff);
                        drawString(
                                fontRenderer,
                                value,
                                valueX,
                                y + 6,
                                locked ? 0xffaaaaaa : 0xffffffff);
                    }
                };
    }

    private static boolean locked(OptionRow row) {
        if (row.nativeProvider) return Iaspis.proxy.forcedHudProviderMode(row.key) != null;
        return BRIDGE.forcedOption(row.key) != null
                || (BRIDGE.requiresServer(row.key) && !Iaspis.proxy.hasWailaServer());
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        mc.displayGuiScreen(parent);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == 1) mc.displayGuiScreen(parent);
        else super.keyTyped(character, key);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        options.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(
                fontRenderer, translate("iaspis.extensions.settings"), width / 2, 12, 0xffffffff);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static final class OptionRow {
        final String key;
        final String label;
        final boolean nativeProvider;

        OptionRow(String key, String label, boolean nativeProvider) {
            this.key = key;
            this.label = label;
            this.nativeProvider = nativeProvider;
        }
    }
}
