package io.github.vulpesstella.iaspis.client;

import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.client.HudSettings.NumberOption;
import io.github.vulpesstella.iaspis.compat.HudProviderRegistry;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StatCollector;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

final class HudSettingsScreen extends GuiScreen {
    private static final int DONE = 100;
    private static final int RESET = 101;
    private static final int WAILA = 102;
    private static final int CATEGORY_BUTTON = 200;
    private static final int ROW_HEIGHT = 23;
    private static final int BUTTON_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 19;
    private static final int LIST_TOP = 53;
    private static final int MARGIN = 10;
    private static final int GAP = 10;
    private static final int TRACK_WIDTH = 7;
    private static final int PADDING = 6;
    private static final int PANEL = 0xc0161720;
    private static final int EDGE = 0xff434351;
    private static final int ACCENT = 0xffbcade8;

    private enum Category {
        APPEARANCE(
                "appearance",
                PreviewSample.HORIZONTAL,
                PreviewSample.VERTICAL,
                PreviewSample.SCALE,
                PreviewSample.OPACITY,
                PreviewSample.THEME,
                PreviewSample.ICON,
                PreviewSample.MOD_NAME,
                PreviewSample.ANCHOR_X,
                PreviewSample.ANCHOR_Y,
                PreviewSample.AUTO_SCALE),
        BEHAVIOR(
                "behavior",
                PreviewSample.MODE,
                PreviewSample.VISIBILITY,
                PreviewSample.ANIMATION,
                PreviewSample.BOSS_BAR,
                PreviewSample.LINGER,
                PreviewSample.BLOCKS,
                PreviewSample.ENTITIES,
                PreviewSample.BOSSES,
                PreviewSample.TAB_LIST,
                PreviewSample.FLUID_TARGET,
                PreviewSample.NARRATION),
        CONTENT(
                "content",
                PreviewSample.INVENTORY,
                PreviewSample.INVENTORY_LIMIT,
                PreviewSample.INVENTORY_DETAILS,
                PreviewSample.INVENTORY_COLUMNS,
                PreviewSample.INVENTORY_NAMES,
                PreviewSample.INVENTORY_SORT,
                PreviewSample.HARVEST,
                PreviewSample.HARVEST_STYLE,
                PreviewSample.HARVEST_EFFECTIVE,
                PreviewSample.HARVEST_CREATIVE,
                PreviewSample.HARVEST_UNBREAKABLE,
                PreviewSample.HARVEST_NEW_LINE,
                PreviewSample.HEALTH,
                PreviewSample.BREAKING,
                PreviewSample.HEALTH_LIMIT,
                PreviewSample.HEALTH_COLUMNS,
                PreviewSample.HEALTH_FRACTION,
                PreviewSample.ARMOR,
                PreviewSample.ARMOR_LIMIT,
                PreviewSample.GROWTH,
                PreviewSample.FURNACE,
                PreviewSample.TANK,
                PreviewSample.TANK_BAR,
                PreviewSample.BREWING,
                PreviewSample.SPAWNER,
                PreviewSample.HOPPER,
                PreviewSample.NOTE_BLOCK,
                PreviewSample.JUKEBOX,
                PreviewSample.ENCHANTING,
                PreviewSample.ANIMAL,
                PreviewSample.HORSE,
                PreviewSample.OWNER,
                PreviewSample.VILLAGER,
                PreviewSample.EFFECTS,
                PreviewSample.EFFECT_LIMIT,
                PreviewSample.ITEM_FRAME,
                PreviewSample.DROPPED_ITEM);

        final String key;
        final PreviewSample[] options;

        Category(String key, PreviewSample... options) {
            this.key = key;
            this.options = options;
        }

        String title() {
            return translate("iaspis.settings.category." + key);
        }
    }

    private final Overlay overlay;
    private final IntBuffer scissor = BufferUtils.createIntBuffer(16);
    private final List<GuiButton> options = new ArrayList<GuiButton>();
    private final int[] sectionOffsets = new int[Category.values().length];
    private int settingsWidth;
    private int listBottom;
    private int contentHeight;
    private int scroll;
    private int dragOffset = -1;
    private int selectedCategory;
    private PreviewSample sample = PreviewSample.DEFAULT;
    private long sampleSince;

    HudSettingsScreen(Overlay overlay) {
        this.overlay = overlay;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();
        options.clear();
        dragOffset = -1;
        settingsWidth = Math.min(260, (width - 2 * MARGIN - GAP) * 56 / 100);
        listBottom = height - 37;
        int optionWidth = settingsWidth - 2 * PADDING - TRACK_WIDTH - 4;
        int tabWidth = tabWidth();
        int contentTop = PADDING;
        for (Category category : Category.values()) {
            int index = category.ordinal();
            int tabX = MARGIN + index * (tabWidth + 4);
            buttonList.add(
                    new CategoryButton(
                            CATEGORY_BUTTON + index,
                            tabX,
                            29,
                            tabWidth,
                            BUTTON_HEIGHT,
                            category.title()));
            sectionOffsets[index] = contentTop;
            contentTop += HEADER_HEIGHT;
            for (PreviewSample sample : category.options) {
                int x = MARGIN + PADDING;
                int id = sample.buttonId();
                NumberOption number = NumberOption.forKey(sample.key);
                GuiButton option =
                        number != null && number.isSlider()
                                ? new Slider(id, x, 0, optionWidth, number)
                                : new GuiButton(
                                        id, x, 0, optionWidth, BUTTON_HEIGHT, buttonLabel(sample));
                options.add(option);
                buttonList.add(option);
                contentTop += ROW_HEIGHT;
            }
            contentTop += PADDING;
        }
        contentHeight = contentTop;
        int footerWidth = Math.min(120, settingsWidth);
        if (!WailaBridge.INSTANCE.getModuleNames().isEmpty()
                || !HudProviderRegistry.settings().isEmpty()) {
            footerWidth = Math.min(footerWidth, (width - 2 * MARGIN - 2 * GAP) / 3);
            buttonList.add(
                    new GuiButton(
                            WAILA,
                            (width - footerWidth) / 2,
                            height - 29,
                            footerWidth,
                            BUTTON_HEIGHT,
                            translate("iaspis.extensions.settings")));
        }
        buttonList.add(
                new GuiButton(
                        RESET,
                        MARGIN,
                        height - 29,
                        footerWidth,
                        BUTTON_HEIGHT,
                        translate("iaspis.settings.reset")));
        buttonList.add(
                new GuiButton(
                        DONE,
                        width - MARGIN - footerWidth,
                        height - 29,
                        footerWidth,
                        BUTTON_HEIGHT,
                        translate("gui.done")));
        setScroll(scroll);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        RenderScope screen = new RenderScope();
        try {
            RenderScope.hud();
            drawDefaultBackground();
            drawCenteredString(
                    fontRenderer, translate("iaspis.settings.title"), width / 2, 10, 0xffffff);
            int previewLeft = MARGIN + settingsWidth + GAP;
            int previewWidth = width - MARGIN - previewLeft;
            drawRect(MARGIN, LIST_TOP, MARGIN + settingsWidth, listBottom, PANEL);
            drawRect(previewLeft, 29, width - MARGIN, listBottom, PANEL);
            drawRect(previewLeft, 29, width - MARGIN, 30, EDGE);
            drawCenteredString(
                    fontRenderer,
                    translate("iaspis.settings.preview"),
                    previewLeft + previewWidth / 2,
                    37,
                    ACCENT);
            drawRect(
                    previewLeft + PADDING,
                    LIST_TOP - 2,
                    width - MARGIN - PADDING,
                    LIST_TOP - 1,
                    EDGE);
            ScaledResolution resolution =
                    new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight);
            drawPreview(resolution, previewLeft, previewWidth, mouseX, mouseY);
            drawSettingsList(mouseX, mouseY, resolution.getScaleFactor());
            for (Object entry : buttonList) {
                GuiButton button = (GuiButton) entry;
                if (button.id >= DONE) {
                    if (button instanceof CategoryButton) {
                        ((CategoryButton) button).pressed =
                                button.id == CATEGORY_BUTTON + selectedCategory;
                    }
                    button.drawButton(mc, mouseX, mouseY);
                }
            }
            drawScrollbar();
        } finally {
            screen.close();
        }
    }

    private void drawPreview(
            ScaledResolution resolution, int left, int previewWidth, int mouseX, int mouseY) {
        PreviewSample hovered = hoveredSample(mouseX, mouseY);
        long now = System.nanoTime();
        if (hovered != sample || sampleSince == 0) {
            sample = hovered;
            sampleSince = now;
        }
        long millis = (now - sampleSince) / 1000000;
        int center = left + previewWidth / 2;
        int innerWidth = previewWidth - 2 * PADDING;
        String descriptionKey =
                sample == PreviewSample.DEFAULT
                        ? "iaspis.settings.example.default"
                        : "iaspis.settings.example." + sample.key;
        if (sample == PreviewSample.MODE) descriptionKey += "." + NumberOption.MODE.get();
        if (sample == PreviewSample.VISIBILITY)
            descriptionKey += sample.visible(millis) ? ".on" : ".off";
        if (sample == PreviewSample.FURNACE && !Iaspis.config().getBoolean(sample.key))
            descriptionKey += ".off";
        List<?> lines =
                fontRenderer.listFormattedStringToWidth(translate(descriptionKey), innerWidth);
        int descriptionY = listBottom - PADDING - lines.size() * 10;
        if (sample != PreviewSample.DEFAULT) {
            drawCenteredString(
                    fontRenderer,
                    translate("iaspis.settings." + sample.key),
                    center,
                    descriptionY - 13,
                    ACCENT);
        }
        for (int i = 0; i < lines.size(); i++) {
            drawCenteredString(
                    fontRenderer, (String) lines.get(i), center, descriptionY + i * 10, 0xffc5c4ce);
        }
        int previewHeight = descriptionY - LIST_TOP - (sample == PreviewSample.DEFAULT ? 5 : 17);
        if (previewHeight > 0) {
            overlay.renderPreview(
                    resolution,
                    left + PADDING,
                    LIST_TOP,
                    innerWidth,
                    previewHeight,
                    sample,
                    millis);
        }
    }

    private PreviewSample hoveredSample(int x, int y) {
        for (GuiButton option : options) {
            if (option instanceof Slider && ((Slider) option).dragging)
                return PreviewSample.forButton(option.id);
            if (option.drawButton
                    && overList(x, y)
                    && x >= option.xPosition
                    && x < trackLeft() - 4
                    && y >= option.yPosition
                    && y < option.yPosition + BUTTON_HEIGHT) {
                return PreviewSample.forButton(option.id);
            }
        }
        return PreviewSample.DEFAULT;
    }

    private void drawSettingsList(int mouseX, int mouseY, int scale) {
        RenderScope list = new RenderScope();
        try {
            RenderScope.clip(
                    MARGIN * scale,
                    mc.displayHeight - listBottom * scale,
                    settingsWidth * scale,
                    (listBottom - LIST_TOP) * scale,
                    scissor);
            for (Category category : Category.values()) {
                int y = LIST_TOP + sectionOffsets[category.ordinal()] - scroll;
                drawString(fontRenderer, category.title(), MARGIN + PADDING, y + 2, ACCENT);
                drawRect(MARGIN + PADDING, y + 13, trackLeft() - 4, y + 14, EDGE);
            }
            int hoverY = overList(mouseX, mouseY) ? mouseY : -1;
            for (GuiButton option : options) option.drawButton(mc, mouseX, hoverY);
        } finally {
            list.close();
        }
    }

    private void drawScrollbar() {
        int left = trackLeft();
        int thumbTop = thumbTop();
        drawRect(left, LIST_TOP + 2, left + TRACK_WIDTH, listBottom - 2, 0xff252631);
        drawRect(
                left + 1,
                thumbTop,
                left + TRACK_WIDTH - 1,
                thumbTop + thumbHeight(),
                maxScroll() == 0 ? EDGE : ACCENT);
    }

    private int trackLeft() {
        return MARGIN + settingsWidth - PADDING - TRACK_WIDTH;
    }

    private int tabWidth() {
        return (settingsWidth - 8) / Category.values().length;
    }

    private int activeCategory() {
        if (maxScroll() > 0 && scroll == maxScroll()) return sectionOffsets.length - 1;
        for (int i = sectionOffsets.length - 1; i > 0; i--) {
            if (scroll >= sectionOffsets[i] - PADDING) return i;
        }
        return 0;
    }

    private int trackHeight() {
        return Math.max(1, listBottom - LIST_TOP - 4);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (listBottom - LIST_TOP));
    }

    private int thumbHeight() {
        return Math.min(
                trackHeight(),
                Math.max(14, trackHeight() * (listBottom - LIST_TOP) / contentHeight));
    }

    private int thumbTop() {
        return LIST_TOP
                + 2
                + (maxScroll() == 0 ? 0 : (trackHeight() - thumbHeight()) * scroll / maxScroll());
    }

    private boolean overList(int x, int y) {
        return x >= MARGIN && x < MARGIN + settingsWidth && y >= LIST_TOP && y < listBottom;
    }

    private void setScroll(int value) {
        scroll = Math.max(0, Math.min(maxScroll(), value));
        int index = 0;
        for (Category category : Category.values()) {
            int y = LIST_TOP + sectionOffsets[category.ordinal()] - scroll + HEADER_HEIGHT;
            for (PreviewSample ignored : category.options) {
                GuiButton option = options.get(index++);
                option.yPosition = y;
                option.drawButton = y < listBottom && y + BUTTON_HEIGHT > LIST_TOP;
                y += ROW_HEIGHT;
            }
        }
    }

    @Override
    public void handleMouseInput() {
        int wheel = Mouse.getEventDWheel();
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (wheel != 0 && overList(x, y)) {
            setScroll(scroll - Integer.signum(wheel) * ROW_HEIGHT * 2);
            selectedCategory = activeCategory();
        }
        super.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (button == 0 && overList(x, y) && x >= trackLeft() && maxScroll() > 0) {
            int top = thumbTop();
            dragOffset = y >= top && y < top + thumbHeight() ? y - top : thumbHeight() / 2;
            dragScrollbar(y);
            return;
        }
        if (overList(x, y)) {
            super.mouseClicked(x, y, button);
            return;
        }
        // A row clipped by the viewport must not intercept a tab or footer click.
        for (GuiButton option : options) option.drawButton = false;
        try {
            super.mouseClicked(x, y, button);
        } finally {
            setScroll(scroll);
        }
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (dragOffset >= 0) dragScrollbar(y);
        else super.mouseClickMove(x, y, button, elapsed);
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int button) {
        if (button == 0) dragOffset = -1;
        super.mouseMovedOrUp(x, y, button);
    }

    private void dragScrollbar(int y) {
        int travel = Math.max(1, trackHeight() - thumbHeight());
        setScroll(Math.round((y - LIST_TOP - 2 - dragOffset) * maxScroll() / (float) travel));
        selectedCategory = activeCategory();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int id = button.id;
        if (id == DONE) {
            mc.displayGuiScreen(null);
        } else if (id == WAILA) {
            mc.displayGuiScreen(new WailaSettingsScreen(this));
        } else if (id == RESET) {
            for (NumberOption number : NumberOption.values()) number.set(number.fallback);
            for (PreviewSample setting : PreviewSample.values()) {
                if (setting != PreviewSample.DEFAULT && NumberOption.forKey(setting.key) == null) {
                    Iaspis.config().setBoolean(setting.key, setting.defaultEnabled);
                }
            }
            initGui();
        } else if (id >= CATEGORY_BUTTON) {
            selectedCategory = id - CATEGORY_BUTTON;
            setScroll(sectionOffsets[selectedCategory] - PADDING);
        } else {
            PreviewSample setting = PreviewSample.forButton(id);
            NumberOption option = NumberOption.forKey(setting.key);
            if (option != null) {
                if (option.isSlider()) return;
                option.set(option.get() == option.max ? option.min : option.get() + 1);
            } else {
                Iaspis.config().setBoolean(setting.key, !setting.enabled());
            }
            button.displayString = buttonLabel(setting);
        }
    }

    private static String buttonLabel(PreviewSample sample) {
        NumberOption option = NumberOption.forKey(sample.key);
        String key = "iaspis.settings." + sample.key;
        if (option != null) {
            return translate(key) + ": " + translate(key + "." + option.get());
        }
        return translate(key) + ": " + translate(sample.enabled() ? "options.on" : "options.off");
    }

    @Override
    public void onGuiClosed() {
        Iaspis.config().save();
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static final class CategoryButton extends GuiButton {
        private boolean pressed;

        CategoryButton(int id, int x, int y, int width, int height, String text) {
            super(id, x, y, width, height, text);
        }

        @Override
        public void drawButton(Minecraft game, int mouseX, int mouseY) {
            if (!pressed) {
                super.drawButton(game, mouseX, mouseY);
                return;
            }
            int right = xPosition + width;
            int bottom = yPosition + height;
            drawRect(xPosition, yPosition, right, bottom, 0xff17151e);
            drawRect(xPosition + 1, yPosition + 2, right - 1, bottom - 1, 0xff4f465f);
            drawRect(xPosition + 1, bottom - 2, right - 1, bottom - 1, 0xff796c8c);
            drawCenteredString(
                    game.fontRenderer,
                    displayString,
                    xPosition + width / 2,
                    yPosition + (height - 8) / 2 + 1,
                    0xffeee7f6);
        }
    }

    private static final class Slider extends GuiButton {
        private static final int HANDLE_WIDTH = 8;
        private final NumberOption option;
        private boolean dragging;

        Slider(int id, int x, int y, int width, NumberOption option) {
            super(id, x, y, width, BUTTON_HEIGHT, "");
            this.option = option;
            updateLabel();
        }

        @Override
        public boolean mousePressed(Minecraft game, int mouseX, int mouseY) {
            if (!super.mousePressed(game, mouseX, mouseY)) return false;
            dragging = true;
            move(mouseX);
            return true;
        }

        @Override
        protected void mouseDragged(Minecraft game, int mouseX, int mouseY) {
            if (dragging) move(mouseX);
            float fraction = (option.get() - option.min) / (float) (option.max - option.min);
            int handle = xPosition + Math.round(fraction * (width - HANDLE_WIDTH));
            game.getTextureManager().bindTexture(buttonTextures);
            GL11.glColor4f(1, 1, 1, 1);
            drawTexturedModalRect(handle, yPosition, 0, 66, HANDLE_WIDTH / 2, height);
            drawTexturedModalRect(
                    handle + HANDLE_WIDTH / 2, yPosition, 196, 66, HANDLE_WIDTH / 2, height);
        }

        @Override
        protected int getHoverState(boolean hovered) {
            return 0;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            dragging = false;
        }

        private void move(int mouseX) {
            float fraction =
                    (mouseX - xPosition - HANDLE_WIDTH / 2) / (float) (width - HANDLE_WIDTH);
            int value =
                    Math.max(
                            option.min,
                            Math.min(
                                    option.max,
                                    option.min + Math.round(fraction * (option.max - option.min))));
            if (value != option.get()) {
                option.set(value);
                updateLabel();
            }
        }

        private void updateLabel() {
            displayString =
                    translate("iaspis.settings." + option.key) + ": " + option.get() + option.unit;
        }
    }
}
