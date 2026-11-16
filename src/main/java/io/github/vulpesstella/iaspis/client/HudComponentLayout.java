package io.github.vulpesstella.iaspis.client;

import java.util.ArrayList;
import java.util.List;

import io.github.vulpesstella.iaspis.api.HudComponent;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import net.minecraft.client.gui.FontRenderer;

final class HudComponentLayout {
    private static final HudComponentLayout EMPTY = new HudComponentLayout();
    private final List<Row> rows = new ArrayList<Row>();
    int width;
    int height;

    static HudComponentLayout measure(List<HudComponent> components, FontRenderer font) {
        if (components.isEmpty()) return EMPTY;
        HudComponentLayout layout = new HudComponentLayout();
        for (HudComponent component : components) {
            RenderScope scope = new RenderScope();
            try {
                int width = component.getWidth(font);
                int height = component.getHeight(font);
                if (width <= 0 || height <= 0) continue;
                layout.rows.add(new Row(component, height));
                layout.width = Math.max(layout.width, width);
                layout.height += height;
            } catch (RuntimeException e) {
                report("measuring", component, e);
            } catch (LinkageError e) {
                report("measuring", component, e);
            } finally {
                scope.close();
            }
        }
        return layout;
    }

    void render(FontRenderer font, int x, int y) {
        for (Row row : rows) {
            RenderScope scope = new RenderScope();
            try {
                RenderScope.hud();
                row.component.render(font, x, y);
            } catch (RuntimeException e) {
                report("drawing", row.component, e);
            } catch (LinkageError e) {
                report("drawing", row.component, e);
            } finally {
                scope.close();
            }
            y += row.height;
        }
    }

    private static void report(String phase, HudComponent component, Throwable error) {
        WailaBridge.INSTANCE.report("Iaspis HUD component " + phase + ": " + component.getClass().getName(), error);
    }

    private static final class Row {
        final HudComponent component;
        final int height;

        Row(HudComponent component, int height) {
            this.component = component;
            this.height = height;
        }
    }
}
