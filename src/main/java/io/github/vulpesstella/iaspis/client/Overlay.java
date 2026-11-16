package io.github.vulpesstella.iaspis.client;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.compat.MultipartAccessor;
import io.github.vulpesstella.iaspis.compat.Target;
import io.github.vulpesstella.iaspis.compat.TankPreview;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import io.github.vulpesstella.iaspis.compat.HudProviderRegistry;
import mcp.mobius.waila.api.IWailaBlockDecorator;
import mcp.mobius.waila.api.IWailaFMPDecorator;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumMovingObjectType;
import net.minecraft.util.Icon;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import io.github.vulpesstella.iaspis.client.HudSettings.NumberOption;

public final class Overlay extends Gui {
    private static final int SLOT_SIZE = 18;
    private static final int ICON_STEP = 9;
    private static final int VITAL_ROW_HEIGHT = 11;
    private static final int EFFECT_ROW_HEIGHT = 20;
    private static final ResourceLocation EFFECT_TEXTURE = new ResourceLocation("textures/gui/container/inventory.png");
    private static final int METER_HEIGHT = 13;
    private static final int PROGRESS_ARROW_WIDTH = 24;
    private static final ResourceLocation ASCII_FONT = new ResourceLocation("textures/font/ascii.png");
    private static final ResourceLocation UNICODE_DIGITS = new ResourceLocation("textures/font/unicode_page_00.png");
    private final Minecraft game = Minecraft.getMinecraft();
    private final RenderItem itemRenderer = new RenderItem();
    private final RemoteTargetData remote;
    private final KeyBinding showKey;
    private final KeyBinding detailsKey;
    private final TargetNarrator narrator = new TargetNarrator();
    private final OverlayTransition transition = new OverlayTransition();
    private final OverlayTransition previewTransition = new OverlayTransition();
    private final OverlayFade fade = new OverlayFade();
    private final IntBuffer scissor = BufferUtils.createIntBuffer(16);
    private World lastWorld;
    private Tooltip lastTooltip;
    private PreviewSample lastPreview;

    Overlay(RemoteTargetData remote, KeyBinding showKey, KeyBinding detailsKey) {
        this.remote = remote;
        this.showKey = showKey;
        this.detailsKey = detailsKey;
    }

    @ForgeSubscribe
    public void render(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (lastWorld != game.theWorld) {
            resetTransition();
            lastWorld = game.theWorld;
        }
        if (!available()) {
            resetTransition();
            return;
        }
        Target target;
        BlockBreakingProgress breaking = null;
        boolean animate = Iaspis.config().getBoolean("overlay.animation");
        if (game.objectMouseOver == null && NumberOption.FLUID_TARGET.get() == FluidTarget.OFF && !showsDroppedItems()) {
            target = null;
        } else {
            // Legacy providers and modded pick-block hooks run before the HUD draws.
            RenderScope providers = new RenderScope();
            try {
                target = target(event.partialTicks, true, true);
                if (target != null) {
                    boolean details = detailsKey.keyCode != 0 && GameSettings.isKeyDown(detailsKey);
                    boolean lite = NumberOption.MODE.get() == HudSettings.LITE && !details;
                    HudProviderRegistry.Changes changes = HudProviderRegistry.collect(target, details);
                    if (changes.hidden) {
                        resetTransition();
                        return;
                    }
                    lastTooltip = new Tooltip(target, details, detailsKeyName(), lite, changes);
                    breaking = BlockBreakingProgress.read(game.playerController, target, event.partialTicks);
                }
            } finally {
                providers.close();
            }
        }
        updateNarration(target);
        if (lastTooltip == null) return;
        float opacity = fade.update(System.nanoTime(), target != null, NumberOption.LINGER.get(), animate);
        if (opacity > 0) {
            renderTooltip(lastTooltip, event.resolution, 0, 0,
                    event.resolution.getScaledWidth(), event.resolution.getScaledHeight(), null, opacity, breaking);
        } else if (target == null) {
            resetTransition();
        }
    }

    private void resetTransition() {
        narrator.update(null);
        transition.reset();
        fade.reset();
        lastTooltip = null;
    }

    private void updateNarration(Target target) {
        if (!PreviewSample.NARRATION.enabled()) {
            narrator.update(null);
            return;
        }
        narrator.update(target == null || lastTooltip == null || lastTooltip.lines.isEmpty()
                ? null : lastTooltip.lines.get(0));
        if (narrator.takeWarning()) {
            game.thePlayer.addChatMessage(StatCollector.translateToLocal("iaspis.narration.unavailable"));
        }
    }

    void renderPreview(ScaledResolution resolution, int x, int y, int width, int height,
                       PreviewSample sample, long millis) {
        if (sample != lastPreview) {
            previewTransition.reset();
            lastPreview = sample;
        }
        if (sample == PreviewSample.BOSS_BAR) drawPreviewBossBar(x, y, width);
        if (sample == PreviewSample.TAB_LIST && millis % 2800 >= 1400) {
            drawPreviewPlayerList(x, y, width);
        }
        if (sample.visible(millis)) {
            BlockBreakingProgress progress = sample == PreviewSample.BREAKING && sample.enabled()
                    ? BlockBreakingProgress.preview(millis) : null;
            renderTooltip(Tooltip.preview(sample, millis, detailsKeyName()), resolution, x, y, width, height, sample, 1, progress);
        }
    }

    private void drawPreviewPlayerList(int x, int y, int width) {
        String title = StatCollector.translateToLocal("iaspis.settings.example.tabList");
        int boxWidth = Math.min(width, game.fontRenderer.getStringWidth(title) + 16);
        int left = x + (width - boxWidth) / 2;
        drawRect(left, y, left + boxWidth, y + 22, 0xff404048);
        game.fontRenderer.drawStringWithShadow(title, left + 8, y + 7, 0xffffff);
    }

    private void drawPreviewBossBar(int x, int y, int width) {
        int barWidth = Math.min(182, Math.max(2, width - 12));
        int left = x + (width - barWidth) / 2;
        game.fontRenderer.drawStringWithShadow(StatCollector.translateToLocal("entity.WitherBoss.name"),
                x + (width - game.fontRenderer.getStringWidth(StatCollector.translateToLocal("entity.WitherBoss.name"))) / 2,
                y + 2, 0xffd6a7ee);
        drawRect(left, y + 13, left + barWidth, y + 18, 0xff452750);
        drawRect(left + 1, y + 14, left + barWidth * 3 / 4, y + 17, 0xffb665ca);
    }

    private void renderTooltip(Tooltip tooltip, ScaledResolution resolution, int originX, int originY,
                               int areaWidth, int areaHeight, PreviewSample sample, float opacity,
                               BlockBreakingProgress breaking) {
        boolean preview = sample != null;
        float scale = NumberOption.SCALE.get() / 100.0F;
        int layoutWidth = (int) (areaWidth / scale);
        int layoutHeight = (int) (areaHeight / scale);
        int textWidth = 0;
        int wrapWidth = Math.max(40, layoutWidth - 36);
        List<String> lines = new ArrayList<String>();
        for (String line : tooltip.lines) {
            if (line == null) continue;
            for (Object wrapped : game.fontRenderer.listFormattedStringToWidth(line, wrapWidth)) {
                String text = (String) wrapped;
                lines.add(text);
                textWidth = Math.max(textWidth, game.fontRenderer.getStringWidth(text));
            }
        }
        if (lines.isEmpty()) {
            transition.reset();
            return;
        }
        boolean showIcon = NumberOption.ICON.get() != HudSettings.ICON_HIDDEN
                && (tooltip.fluid != null || (tooltip.icon != null && tooltip.icon.getItem() != null));
        boolean inlineIcon = showIcon && NumberOption.ICON.get() == HudSettings.ICON_INLINE;
        int iconWidth = showIcon ? 21 : 0;
        int columns = Math.min(Math.min(NumberOption.INVENTORY_COLUMNS.get(), tooltip.inventory.size()),
                Math.max(1, (layoutWidth - 12) / SLOT_SIZE));
        int rows = columns == 0 ? 0 : (tooltip.inventory.size() + columns - 1) / columns;
        int headerWidth = inlineIcon ? Math.max(textWidth, game.fontRenderer.getStringWidth(lines.get(0)) + iconWidth)
                : textWidth + iconWidth;
        int firstLineHeight = inlineIcon ? SLOT_SIZE : 10;
        int harvestWidth = 0;
        if (tooltip.harvest != null) {
            harvestWidth = tooltip.harvest.showStatus ? 12 : 0;
            for (HarvestHint.ToolIcon icon : tooltip.harvest.icons) harvestWidth += toolWidth(icon);
        }
        int firstLineWidth = game.fontRenderer.getStringWidth(lines.get(0)) + iconWidth;
        boolean inlineHarvest = tooltip.harvest != null && !PreviewSample.HARVEST_NEW_LINE.enabled()
                && firstLineWidth + harvestWidth + 17 <= layoutWidth;
        int harvestHeight = tooltip.harvest == null || inlineHarvest ? 0 : SLOT_SIZE;
        if (inlineHarvest) {
            firstLineHeight = SLOT_SIZE;
            headerWidth = Math.max(headerWidth, firstLineWidth + harvestWidth + 5);
        }
        int headerHeight = Math.max(showIcon ? 16 : 0, firstLineHeight + (lines.size() - 1) * 10);
        int hearts = countIcons(tooltip.maxHealth);
        int extraHearts = countIcons(tooltip.absorption);
        int shields = countIcons(tooltip.armor);
        boolean compactHealth = hearts > 0 && tooltip.maxHealth + tooltip.absorption > NumberOption.HEALTH_LIMIT.get();
        boolean compactArmor = tooltip.armor > NumberOption.ARMOR_LIMIT.get();
        boolean healthFraction = compactHealth || (hearts > 0 && PreviewSample.HEALTH_FRACTION.enabled());
        String healthText = healthFraction ? StatCollector.translateToLocalFormatted(
                "iaspis.health", tooltip.health, tooltip.maxHealth) : "";
        String armorText = compactArmor ? Integer.toString(tooltip.armor) : "";
        int iconColumns = Math.max(1, Math.min(NumberOption.HEALTH_COLUMNS.get(), (layoutWidth - 12) / ICON_STEP));
        int healthRows = compactHealth ? 1 : (hearts + extraHearts + iconColumns - 1) / iconColumns;
        int healthWidth = compactHealth ? ICON_STEP + game.fontRenderer.getStringWidth(healthText)
                : Math.max(Math.min(iconColumns, hearts + extraHearts) * ICON_STEP, game.fontRenderer.getStringWidth(healthText));
        int armorColumns = Math.max(1, Math.min(10, (layoutWidth - 12) / ICON_STEP));
        int armorRows = compactArmor ? 1 : (shields + armorColumns - 1) / armorColumns;
        int armorWidth = compactArmor ? ICON_STEP + game.fontRenderer.getStringWidth(armorText)
                : Math.min(armorColumns, shields) * ICON_STEP;
        int extraHealthRows = healthFraction && !compactHealth ? 1 : 0;
        int furnaceWidth = tooltip.cookTime < 0 ? 0 : tooltip.furnaceSlots.isEmpty() ? 24 : 3 * SLOT_SIZE + 28;
        boolean tankBar = PreviewSample.TANK_BAR.enabled();
        int tankWidth = 0;
        List<String> tankLabels = new ArrayList<String>(tooltip.tanks.size());
        for (TankPreview tank : tooltip.tanks) {
            String label = Tooltip.tankLabel(tank);
            tankLabels.add(label);
            tankWidth = Math.max(tankWidth, game.fontRenderer.getStringWidth(label) + (tankBar ? 6 : 0));
        }
        int tankHeight = meterRowHeight(tankBar);
        String energyLabel = tooltip.energy.unit == null ? null : Tooltip.energyLabel(tooltip.energy);
        int energyWidth = energyLabel == null ? 0 : game.fontRenderer.getStringWidth(energyLabel) + 6;
        float progress = tooltip.brewProgress >= 0 ? tooltip.brewProgress : tooltip.processing;
        boolean showProgress = tooltip.brewProgress >= 0 || progress > 0;
        String progressLabel = showProgress ? StatCollector.translateToLocalFormatted(
                "iaspis.machine.progressAmount", (int) (progress * 100)) : null;
        int progressWidth = progressLabel == null ? 0 : PROGRESS_ARROW_WIDTH + 5 + game.fontRenderer.getStringWidth(progressLabel);
        int itemDetailWidth = tooltip.detailItem == null ? 0 : SLOT_SIZE + 2 + game.fontRenderer.getStringWidth(tooltip.detailLabel);
        int machineWidth = Math.max(Math.max(energyWidth, progressWidth), itemDetailWidth);
        int effectsWidth = 0;
        for (VanillaEntityDetails.EffectLine effect : tooltip.effects) {
            effectsWidth = Math.max(effectsWidth, SLOT_SIZE + 4 + game.fontRenderer.getStringWidth(effect.label));
        }
        HudComponentLayout components = HudComponentLayout.measure(tooltip.components, game.fontRenderer);
        int baseWidth = Math.max(Math.max(headerWidth, columns * SLOT_SIZE),
                Math.max(Math.max(harvestWidth, furnaceWidth),
                        Math.max(Math.max(healthWidth, armorWidth),
                                Math.max(Math.max(tankWidth, machineWidth), effectsWidth))));
        int width = Math.max(baseWidth, components.width) + 12;
        int height = headerHeight + harvestHeight + rows * SLOT_SIZE + 10
                + (healthRows + extraHealthRows + armorRows) * VITAL_ROW_HEIGHT
                + (furnaceWidth > 0 ? SLOT_SIZE : 0) + tankLabels.size() * tankHeight
                + (energyLabel == null ? 0 : meterRowHeight(true)) + (progressLabel == null ? 0 : SLOT_SIZE)
                + (itemDetailWidth == 0 ? 0 : SLOT_SIZE) + tooltip.effects.size() * EFFECT_ROW_HEIGHT
                + components.height;
        if (sample == PreviewSample.ANCHOR_Y) {
            scale = Math.min(scale, areaHeight / (2.0F * (height + 8)));
            layoutWidth = (int) (areaWidth / scale);
            layoutHeight = (int) (areaHeight / scale);
        } else if (sample == PreviewSample.ANCHOR_X) {
            scale = Math.min(scale, areaWidth / (2.0F * width));
            layoutWidth = (int) (areaWidth / scale);
            layoutHeight = (int) (areaHeight / scale);
        }
        float heightLimit = areaHeight * (preview && sample != PreviewSample.AUTO_SCALE
                ? 1 : NumberOption.AUTO_SCALE.get() / 100.0F);
        if ((height + 8) * scale > heightLimit && areaHeight > 0) {
            float fit = heightLimit / (height + 8);
            scale = preview ? fit : Math.max(scale * 0.5F, fit);
            layoutWidth = (int) (areaWidth / scale);
            layoutHeight = (int) (areaHeight / scale);
        }
        // Demonstrate each anchor away from the clamped screen edges.
        int xPercent = sample == PreviewSample.ANCHOR_X ? 50 : NumberOption.X.get();
        int yPercent = sample == PreviewSample.ANCHOR_Y ? 50 : NumberOption.Y.get();
        int expectedLeft = anchoredPosition(layoutWidth, width, xPercent, NumberOption.ANCHOR_X.get());
        int expectedTop = 8 + anchoredPosition(layoutHeight - 8, height, yPercent, NumberOption.ANCHOR_Y.get());
        boolean bossVisible = preview ? sample == PreviewSample.BOSS_BAR : BossStatus.statusBarLength > 0;
        int bossHalfWidth = preview ? Math.min(91, Math.max(1, (areaWidth - 12) / 2)) : 91;
        if (bossVisible
                && expectedTop * scale < 26 && expectedLeft * scale < areaWidth / 2 + bossHalfWidth
                && (expectedLeft + width) * scale > areaWidth / 2 - bossHalfWidth) {
            int mode = NumberOption.BOSS_MODE.get();
            if (mode == HudSettings.BOSS_HIDE) return;
            if (mode == HudSettings.BOSS_MOVE) expectedTop = (int) Math.ceil(26 / scale);
        }
        OverlayTransition.Bounds expected = new OverlayTransition.Bounds(expectedLeft, expectedTop, width, height);
        boolean animate = Iaspis.config().getBoolean("overlay.animation");
        OverlayTransition.Bounds bounds = preview
                ? previewTransition.update(expected, System.nanoTime(), animate && sample == PreviewSample.ANIMATION)
                : transition.update(expected, System.nanoTime(), animate);
        int left = bounds.left;
        int top = bounds.top;
        int pixelScale = resolution.getScaleFactor();
        int clipLeft = Math.max(originX, (int) Math.floor(originX + left * scale));
        int clipTop = Math.max(originY, (int) Math.floor(originY + top * scale));
        int clipRight = Math.min(originX + areaWidth, (int) Math.ceil(originX + (left + bounds.width) * scale));
        int clipBottom = Math.min(originY + areaHeight, (int) Math.ceil(originY + (top + bounds.height) * scale));

        RenderScope hud = new RenderScope();
        try {
            RenderScope.hud();
            if (sample == PreviewSample.ANCHOR_Y) {
                int guideY = originY + Math.round((8 + (layoutHeight - 8) / 2) * scale);
                drawRect(originX, guideY, originX + areaWidth, guideY + 1, 0x7fbcade8);
            } else if (sample == PreviewSample.ANCHOR_X) {
                int guideX = originX + Math.round(layoutWidth / 2 * scale);
                drawRect(guideX, originY, guideX + 1, originY + areaHeight, 0x7fbcade8);
            }
            RenderScope.clip(clipLeft * pixelScale, game.displayHeight - clipBottom * pixelScale,
                    Math.max(0, clipRight - clipLeft) * pixelScale,
                    Math.max(0, clipBottom - clipTop) * pixelScale, scissor);
            boolean captured = opacity < 1 && fade.capture(clipLeft * pixelScale, clipTop * pixelScale,
                    clipRight * pixelScale, clipBottom * pixelScale, game.displayWidth, game.displayHeight);
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(originX, originY, 0);
                GL11.glScalef(scale, scale, 1);
                drawRect(left, top, left + bounds.width, top + bounds.height, HudSettings.background());
                drawRect(left, top, left + bounds.width, top + 1, HudSettings.accent());
                if (showIcon) {
                    int iconTop = top + 5 + (inlineIcon ? 0 : (headerHeight - 16) / 2);
                    if (tooltip.fluid == null) drawItem(tooltip.icon, left + 4, iconTop);
                    else drawFluid(tooltip.fluid, 0, left + 4, iconTop, 16, 16);
                }
                for (int i = 0; i < lines.size(); i++) {
                    int x = left + 6 + (inlineIcon && i > 0 ? 0 : iconWidth);
                    int y = top + 5 + (i == 0 ? (firstLineHeight - 10) / 2 : firstLineHeight + (i - 1) * 10);
                    game.fontRenderer.drawStringWithShadow(lines.get(i), x, y, 0xffffff);
                }
                int detailTop = top + 5 + headerHeight;
                if (tooltip.harvest != null) {
                    if (inlineHarvest) drawHarvest(tooltip.harvest, left + 11 + firstLineWidth, top + 5);
                    else drawHarvest(tooltip.harvest, left + 6, detailTop);
                }
                detailTop += harvestHeight;
                if (healthRows > 0) {
                    if (compactHealth) drawVitalWithText(52, 0, healthText, left + 6, detailTop);
                    else drawHearts(tooltip, hearts, extraHearts, iconColumns, left + 6, detailTop);
                    detailTop += healthRows * VITAL_ROW_HEIGHT;
                    if (extraHealthRows > 0) {
                        game.fontRenderer.drawStringWithShadow(healthText, left + 6, detailTop, 0xffffff);
                        detailTop += VITAL_ROW_HEIGHT;
                    }
                }
                if (shields > 0) {
                    if (compactArmor) drawVitalWithText(34, 9, armorText, left + 6, detailTop);
                    else drawArmor(tooltip.armor, shields, armorColumns, left + 6, detailTop);
                    detailTop += armorRows * VITAL_ROW_HEIGHT;
                }
                if (furnaceWidth > 0) {
                    drawFurnace(tooltip, left + 6, detailTop, scale != 1);
                    detailTop += SLOT_SIZE;
                }
                for (int i = 0; i < tankLabels.size(); i++) {
                    drawTank(tooltip.tanks.get(i), tankLabels.get(i), left + 6, detailTop, bounds.width - 12, tankBar);
                    detailTop += tankHeight;
                }
                if (energyLabel != null) {
                    int meterLeft = left + 6;
                    int fill = drawMeter(meterLeft, detailTop, bounds.width - 12,
                            tooltip.energy.stored / tooltip.energy.capacity);
                    if (fill > 0) {
                        drawRect(meterLeft + 1, detailTop + 1, meterLeft + fill + 1,
                                detailTop + METER_HEIGHT - 1, 0xffaa5924);
                    }
                    drawMeterLabel(energyLabel, meterLeft, detailTop, true);
                    detailTop += meterRowHeight(true);
                }
                if (progressLabel != null) {
                    drawProgressArrow(left + 6, detailTop + 1, progress);
                    game.fontRenderer.drawStringWithShadow(progressLabel,
                            left + 11 + PROGRESS_ARROW_WIDTH, detailTop + 5, 0xffffff);
                    detailTop += SLOT_SIZE;
                }
                if (itemDetailWidth > 0) {
                    drawItem(tooltip.detailItem, left + 6, detailTop + 1);
                    game.fontRenderer.drawStringWithShadow(tooltip.detailLabel,
                            left + 8 + SLOT_SIZE, detailTop + 5, 0xffffff);
                    detailTop += SLOT_SIZE;
                }
                for (VanillaEntityDetails.EffectLine effect : tooltip.effects) {
                    if (effect.icon >= 0) {
                        bindTexture(EFFECT_TEXTURE);
                        drawTexturedModalRect(left + 6, detailTop + 1,
                                effect.icon % 8 * 18, 198 + effect.icon / 8 * 18, 18, 18);
                    }
                    game.fontRenderer.drawStringWithShadow(effect.label, left + 10 + SLOT_SIZE, detailTop + 6, 0xffffff);
                    detailTop += EFFECT_ROW_HEIGHT;
                }
                components.render(game.fontRenderer, left + 6, detailTop);
                detailTop += components.height;
                if (columns > 0) drawInventory(tooltip.inventory, columns, left + 6, detailTop, scale != 1);
                if (breaking != null) drawBreakingProgress(breaking, bounds);
            } finally {
                GL11.glPopMatrix();
            }
            if (captured) fade.composite(opacity, pixelScale, game.displayHeight);
        } finally {
            hud.close();
        }
    }

    private void drawTank(TankPreview tank, String label, int left, int top, int width, boolean bar) {
        if (bar) {
            int fill = drawMeter(left, top, width, (double) tank.amount / tank.capacity);
            if (tank.fluid != null && fill > 0) {
                drawFluid(tank.fluid, tank.amount, left + 1, top + 1, fill, METER_HEIGHT - 2);
            }
        }
        drawMeterLabel(label, left, top, bar);
    }

    private static int meterRowHeight(boolean bar) {
        return bar ? METER_HEIGHT + 2 : 10;
    }

    private int drawMeter(int left, int top, int width, double fraction) {
        if (width <= 2) return 0;
        drawRect(left, top, left + width, top + METER_HEIGHT, 0xff424454);
        drawRect(left + 1, top + 1, left + width - 1, top + METER_HEIGHT - 1, 0xff191a22);
        return (int) ((width - 2) * Math.min(1, fraction));
    }

    private void drawMeterLabel(String label, int left, int top, boolean bar) {
        game.fontRenderer.drawStringWithShadow(label, left + (bar ? 3 : 0), top + (bar ? 2 : 0), 0xffffff);
    }

    private void drawFluid(Fluid fluid, int amount, int left, int top, int width, int height) {
        FluidStack stack = amount == 0 ? null : new FluidStack(fluid, amount);
        Icon texture = stack == null ? fluid.getIcon() : fluid.getIcon(stack);
        if (texture == null && fluid == FluidRegistry.WATER) texture = Block.waterStill.getIcon(1, 0);
        if (texture == null && fluid == FluidRegistry.LAVA) texture = Block.lavaStill.getIcon(1, 0);
        int color = (stack == null ? fluid.getColor() : fluid.getColor(stack)) & 0xffffff;
        if (texture == null) {
            drawRect(left, top, left + width, top + height, 0xff000000 | color);
            return;
        }
        RenderScope scope = new RenderScope();
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1, 1, 1, 1);
            game.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.setColorOpaque_I(color);
            for (int offset = 0; offset < width; offset += 16) {
                int tileWidth = Math.min(16, width - offset);
                int x = left + offset;
                float u = texture.getInterpolatedU(tileWidth);
                float v = texture.getInterpolatedV(height);
                tess.addVertexWithUV(x, top + height, zLevel, texture.getMinU(), v);
                tess.addVertexWithUV(x + tileWidth, top + height, zLevel, u, v);
                tess.addVertexWithUV(x + tileWidth, top, zLevel, u, texture.getMinV());
                tess.addVertexWithUV(x, top, zLevel, texture.getMinU(), texture.getMinV());
            }
            tess.draw();
        } finally {
            scope.close();
        }
    }

    private void drawBreakingProgress(BlockBreakingProgress breaking, OverlayTransition.Bounds bounds) {
        int fill = Math.round(bounds.width * breaking.fraction);
        if (fill == 0) return;
        int bottom = bounds.top + bounds.height;
        int color = breaking.canHarvest ? 0xffc3b4f1 : 0xffef6464;
        drawRect(bounds.left, bottom - 2, bounds.left + fill, bottom, color);
    }

    private static int anchoredPosition(int area, int size, int percent, int anchor) {
        int free = Math.max(0, area - size);
        if (anchor == 0) return free * percent / 100;
        int offset = area * percent / 100 - size * (anchor - 1) / 2;
        return Math.max(0, Math.min(free, offset));
    }

    private static int countIcons(float points) {
        return Math.max(0, Math.min(100, (int) Math.ceil(points / 2.0F)));
    }

    private void drawHearts(Tooltip tooltip, int hearts, int extraHearts, int columns, int left, int top) {
        bindTexture(Gui.icons);
        for (int i = 0; i < hearts + extraHearts; i++) {
            int x = left + (i % columns) * ICON_STEP;
            int y = top + (i / columns) * VITAL_ROW_HEIGHT;
            boolean extra = i >= hearts;
            float points = extra ? tooltip.absorption - (i - hearts) * 2 : tooltip.health - i * 2;
            drawTexturedModalRect(x, y, 16, 0, 9, 9);
            if (points > 0) {
                int full = extra ? 160 : 52;
                drawTexturedModalRect(x, y, points > 1 ? full : full + 9, 0, 9, 9);
            }
        }
    }

    private void drawArmor(int armor, int shields, int columns, int left, int top) {
        bindTexture(Gui.icons);
        for (int i = 0; i < shields; i++) {
            drawTexturedModalRect(left + (i % columns) * ICON_STEP, top + (i / columns) * VITAL_ROW_HEIGHT,
                    armor - i * 2 >= 2 ? 34 : 25, 9, 9, 9);
        }
    }

    private void drawVitalWithText(int u, int v, String text, int left, int top) {
        bindTexture(Gui.icons);
        drawTexturedModalRect(left, top, u, v, 9, 9);
        game.fontRenderer.drawStringWithShadow(text, left + ICON_STEP, top, 0xffffff);
    }

    private void drawFurnace(Tooltip tooltip, int left, int top, boolean scaled) {
        int arrowX = left;
        if (!tooltip.furnaceSlots.isEmpty()) {
            drawStack(tooltip.furnaceSlots.get(0), left + 1, top + 1, scaled);
            drawStack(tooltip.furnaceSlots.get(1), left + SLOT_SIZE + 1, top + 1, scaled);
            arrowX += 2 * SLOT_SIZE + 2;
            drawStack(tooltip.furnaceSlots.get(2), arrowX + 27, top + 1, scaled);
        }
        drawProgressArrow(arrowX, top + 1, tooltip.cookTime / 200.0F);
    }

    private void drawProgressArrow(int x, int y, float fraction) {
        int filled = (int) (fraction * PROGRESS_ARROW_WIDTH);
        for (int row = 0; row < 16; row++) {
            int start = row >= 5 && row < 11 ? 0 : 15;
            int end = 16 + Math.min(row, 15 - row);
            drawRect(x + start, y + row, x + end, y + row + 1, 0xff777985);
            if (filled > start) {
                drawRect(x + start, y + row, x + Math.min(filled, end), y + row + 1, 0xfff2f2f5);
            }
        }
    }

    private void bindTexture(ResourceLocation texture) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1, 1, 1, 1);
        game.getTextureManager().bindTexture(texture);
    }

    private void drawHarvest(HarvestHint harvest, int left, int top) {
        int x = left;
        for (HarvestHint.ToolIcon icon : harvest.icons) {
            drawItem(icon.stack, x, top);
            if (!icon.levelBadge.isEmpty()) {
                game.fontRenderer.drawStringWithShadow(icon.levelBadge, x + SLOT_SIZE, top + 5, 0xffffff);
            }
            x += toolWidth(icon);
        }
        if (!harvest.showStatus) return;
        int y = top + 4;
        // Pixel marks stay readable with Minecraft 1.6.4's default bitmap font.
        int color = harvest.harvestable ? 0xff55ff55 : 0xffff5555;
        if (harvest.harvestable) {
            for (int i = 0; i < 3; i++) drawRect(x + i, y + 4 + i, x + i + 2, y + 6 + i, color);
            for (int i = 0; i < 7; i++) drawRect(x + 2 + i, y + 6 - i, x + 4 + i, y + 8 - i, color);
        } else {
            for (int i = 0; i < 7; i++) {
                drawRect(x + i, y + i, x + i + 2, y + i + 2, color);
                drawRect(x + i, y + 6 - i, x + i + 2, y + 8 - i, color);
            }
        }
    }

    private int toolWidth(HarvestHint.ToolIcon icon) {
        return SLOT_SIZE + (icon.levelBadge.isEmpty() ? 0 : game.fontRenderer.getStringWidth(icon.levelBadge) + 3);
    }

    private void drawItem(ItemStack stack, int x, int y) {
        RenderScope icon = new RenderScope();
        try {
            // GUI block models need depth writes to occlude their own back faces.
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            itemRenderer.renderItemAndEffectIntoGUI(game.fontRenderer, game.getTextureManager(), stack, x, y);
        } catch (RuntimeException e) {
            WailaBridge.INSTANCE.report("Item renderer for " + stack.itemID, e);
        } catch (LinkageError e) {
            WailaBridge.INSTANCE.report("Item renderer for " + stack.itemID, e);
        } finally {
            icon.close();
        }
    }

    private void drawStack(ItemStack stack, int x, int y, boolean scaled) {
        if (stack == null) return;
        drawItem(stack, x, y);
        // Vanilla's count and durability overlays change lighting and depth on return.
        RenderScope label = new RenderScope();
        try {
            if (scaled && stack.stackSize > 1) drawFilteredStackOverlay(stack, x, y);
            else itemRenderer.renderItemOverlayIntoGUI(game.fontRenderer, game.getTextureManager(), stack, x, y);
        } catch (RuntimeException e) {
            WailaBridge.INSTANCE.report("Item overlay for " + stack.itemID, e);
        } catch (LinkageError e) {
            WailaBridge.INSTANCE.report("Item overlay for " + stack.itemID, e);
        } finally {
            label.close();
        }
    }

    private void drawFilteredStackOverlay(ItemStack stack, int x, int y) {
        ResourceLocation font = game.fontRenderer.getUnicodeFlag() ? UNICODE_DIGITS : ASCII_FONT;
        game.getTextureManager().bindTexture(font);
        int minFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        int magFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
        // At fractional scales a one-texel stroke can fall between two screen pixels.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        try {
            itemRenderer.renderItemOverlayIntoGUI(game.fontRenderer, game.getTextureManager(), stack, x, y);
        } finally {
            game.getTextureManager().bindTexture(font);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
        }
    }

    private void drawInventory(List<ItemStack> stacks, int columns, int left, int top, boolean scaled) {
        for (int i = 0; i < stacks.size(); i++) {
            int x = left + 1 + (i % columns) * SLOT_SIZE;
            int y = top + 1 + (i / columns) * SLOT_SIZE;
            drawStack(stacks.get(i), x, y, scaled);
        }
    }

    @ForgeSubscribe
    public void decorate(RenderWorldLastEvent event) {
        if (game.objectMouseOver == null || !available()) return;
        Target target;
        RenderScope pick = new RenderScope();
        try {
            target = target(event.partialTicks, false, false);
        } finally {
            pick.close();
        }
        if (target == null || target.getBlock() == null) return;
        WailaBridge bridge = WailaBridge.INSTANCE;
        for (IWailaBlockDecorator decorator : bridge.decorators(target)) {
            RenderScope decoration = new RenderScope();
            try {
                GL11.glDepthMask(false);
                decorator.decorateBlock(target.getStack(), target, bridge);
            } catch (RuntimeException e) {
                bridge.report(decorator.getClass().getName(), e);
            } catch (LinkageError e) {
                bridge.report(decorator.getClass().getName(), e);
            } finally {
                decoration.close();
            }
        }
        for (MultipartAccessor part : bridge.multipart(target)) {
            for (IWailaFMPDecorator decorator : bridge.multipartDecorators(part.getID())) {
                RenderScope decoration = new RenderScope();
                try {
                    GL11.glDepthMask(false);
                    decorator.decorateBlock(target.getStack(), part, bridge);
                } catch (RuntimeException e) {
                    bridge.report(decorator.getClass().getName(), e);
                } catch (LinkageError e) {
                    bridge.report(decorator.getClass().getName(), e);
                } finally {
                    decoration.close();
                }
            }
        }
    }

    private Target target(float partialTicks, boolean request, boolean hudAim) {
        MovingObjectPosition hit = game.objectMouseOver;
        if (!available()) return null;
        int fluidMode = NumberOption.FLUID_TARGET.get();
        if (hudAim && fluidMode != FluidTarget.OFF && (hit == null || hit.entityHit == null)
                && PreviewSample.BLOCKS.enabled()) {
            Vec3 eyes = game.renderViewEntity.getPosition(partialTicks);
            Vec3 look = game.renderViewEntity.getLook(partialTicks);
            double reach = game.playerController.getBlockReachDistance();
            if (hit != null && hit.hitVec != null) reach = Math.min(reach, eyes.distanceTo(hit.hitVec));
            MovingObjectPosition fluidHit = FluidTarget.trace(game.theWorld, eyes, look, reach, fluidMode);
            if (fluidHit != null) hit = fluidHit;
        }
        if (hudAim && showsDroppedItems() && (hit == null || hit.entityHit == null)) {
            MovingObjectPosition itemHit = droppedItemTarget(partialTicks, hit);
            if (itemHit != null) hit = itemHit;
        }
        if (hit == null) return null;
        if (hit.entityHit != null) {
            if (!(hit.entityHit instanceof IBossDisplayData ? PreviewSample.BOSSES : PreviewSample.ENTITIES).enabled()) return null;
        } else if (!PreviewSample.BLOCKS.enabled()) {
            return null;
        }
        ItemStack stack = null;
        if (hit.typeOfHit == EnumMovingObjectType.TILE) {
            int id = game.theWorld.getBlockId(hit.blockX, hit.blockY, hit.blockZ);
            Block block = Block.blocksList[id];
            if (block == null) return null;
            try {
                stack = block.getPickBlock(hit, game.theWorld, hit.blockX, hit.blockY, hit.blockZ);
            } catch (RuntimeException e) {
                WailaBridge.INSTANCE.report("Could not pick block " + block.getClass().getName(), e);
            } catch (LinkageError e) {
                WailaBridge.INSTANCE.report("Could not pick block " + block.getClass().getName(), e);
            }
            if (stack == null) stack = new ItemStack(id, 1,
                    game.theWorld.getBlockMetadata(hit.blockX, hit.blockY, hit.blockZ));
            if (stack.getItem() == null) stack = null;
        }
        Target target = new Target(game.theWorld, game.thePlayer, game.renderViewEntity, hit, partialTicks, stack);
        remote.apply(target, request);
        return target;
    }

    ItemStack lookupStack() {
        RenderScope scope = new RenderScope();
        try {
            Target target = target(1, false, true);
            if (target == null) return null;
            if (target.getEntity() instanceof EntityItem) return ((EntityItem) target.getEntity()).getEntityItem();
            if (target.getEntity() instanceof EntityItemFrame) return ((EntityItemFrame) target.getEntity()).getDisplayedItem();
            return target.getBlock() == null ? null : new Tooltip(target, false, null, true).icon;
        } finally {
            scope.close();
        }
    }

    private String detailsKeyName() {
        return detailsKey.keyCode == 0 ? null : GameSettings.getKeyDisplayString(detailsKey.keyCode);
    }

    private boolean showsDroppedItems() {
        return PreviewSample.ENTITIES.enabled() && PreviewSample.DROPPED_ITEM.enabled();
    }

    private MovingObjectPosition droppedItemTarget(float partialTicks, MovingObjectPosition blockHit) {
        Vec3 start = game.renderViewEntity.getPosition(partialTicks);
        Vec3 look = game.renderViewEntity.getLook(partialTicks);
        double reach = game.playerController.extendedReach() ? 6 : 3;
        if (blockHit != null && blockHit.hitVec != null) reach = Math.min(reach, start.distanceTo(blockHit.hitVec));
        if (reach <= 0) return null;
        double dx = look.xCoord * reach;
        double dy = look.yCoord * reach;
        double dz = look.zCoord * reach;
        Vec3 end = start.addVector(dx, dy, dz);
        AxisAlignedBB search = game.renderViewEntity.boundingBox.addCoord(dx, dy, dz).expand(1, 1, 1);
        EntityItem nearest = null;
        for (Object entry : game.theWorld.getEntitiesWithinAABB(EntityItem.class, search)) {
            EntityItem item = (EntityItem) entry;
            if (item.isDead) continue;
            MovingObjectPosition hit = item.boundingBox.calculateIntercept(start, end);
            double distance = item.boundingBox.isVecInside(start) ? 0
                    : hit == null ? Double.POSITIVE_INFINITY : start.distanceTo(hit.hitVec);
            if (distance < reach) {
                reach = distance;
                nearest = item;
            }
        }
        return nearest == null ? null : new MovingObjectPosition(nearest);
    }

    private boolean available() {
        return game.theWorld != null && game.thePlayer != null && game.renderViewEntity != null
                && game.currentScreen == null && !game.gameSettings.hideGUI && shouldShow();
    }

    private boolean shouldShow() {
        if (PreviewSample.TAB_LIST.enabled() && GameSettings.isKeyDown(game.gameSettings.keyBindPlayerList)) return false;
        if (NumberOption.MODE.get() == HudSettings.HOLD) return GameSettings.isKeyDown(showKey);
        return Iaspis.config().getBoolean("overlay.enabled");
    }
}
