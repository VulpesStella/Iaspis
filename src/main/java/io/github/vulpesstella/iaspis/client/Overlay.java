package io.github.vulpesstella.iaspis.client;

import java.util.ArrayList;
import java.util.List;

import io.github.vulpesstella.iaspis.compat.Target;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import mcp.mobius.waila.api.IWailaBlockDecorator;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumMovingObjectType;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.ForgeSubscribe;
import org.lwjgl.opengl.GL11;

public final class Overlay extends Gui {
    private static final int SLOT_SIZE = 18;
    private static final int ICON_STEP = 9;
    private static final int ICONS_PER_ROW = 10;
    private static final int VITAL_ROW_HEIGHT = 11;
    private final Minecraft game = Minecraft.getMinecraft();
    private final RenderItem itemRenderer = new RenderItem();
    private final RemoteTargetData remote;
    private final OverlayTransition transition = new OverlayTransition();
    private World lastWorld;

    Overlay(RemoteTargetData remote) {
        this.remote = remote;
    }

    @ForgeSubscribe
    public void render(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Target target = target(event.partialTicks, true);
        if (target == null) {
            transition.reset();
            lastWorld = null;
            return;
        }
        if (lastWorld != target.getWorld()) {
            transition.reset();
            lastWorld = target.getWorld();
        }

        boolean details = GameSettings.isKeyDown(game.gameSettings.keyBindSneak);
        String detailKey = GameSettings.getKeyDisplayString(game.gameSettings.keyBindSneak.keyCode);
        Tooltip tooltip = new Tooltip(target, details, detailKey);
        int textWidth = 0;
        int wrapWidth = Math.max(40, event.resolution.getScaledWidth() - 36);
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
        boolean showIcon = tooltip.icon != null && tooltip.icon.getItem() != null;
        int iconWidth = showIcon ? 21 : 0;
        int columns = Math.min(Math.min(9, tooltip.inventory.size()),
                Math.max(1, (event.resolution.getScaledWidth() - 12) / SLOT_SIZE));
        int rows = columns == 0 ? 0 : (tooltip.inventory.size() + columns - 1) / columns;
        int headerHeight = Math.max(showIcon ? 16 : 0, lines.size() * 10);
        int harvestWidth = 0;
        if (tooltip.harvest != null) {
            harvestWidth = tooltip.harvest.showStatus ? 12 : 0;
            for (HarvestHint.ToolIcon icon : tooltip.harvest.icons) harvestWidth += toolWidth(icon);
        }
        int harvestHeight = tooltip.harvest == null ? 0 : SLOT_SIZE;
        int hearts = countIcons(tooltip.maxHealth);
        int extraHearts = countIcons(tooltip.absorption);
        int shields = countIcons(tooltip.armor);
        boolean compactHealth = hearts > 20 || extraHearts > 20 - hearts;
        boolean compactArmor = shields > 10;
        String healthText = compactHealth ? StatCollector.translateToLocalFormatted(
                "iaspis.health", tooltip.health, tooltip.maxHealth) : "";
        String armorText = compactArmor ? Integer.toString(tooltip.armor) : "";
        int healthRows = compactHealth ? 1 : (hearts + extraHearts + ICONS_PER_ROW - 1) / ICONS_PER_ROW;
        int healthWidth = compactHealth ? ICON_STEP + game.fontRenderer.getStringWidth(healthText)
                : Math.min(ICONS_PER_ROW, hearts + extraHearts) * ICON_STEP;
        int armorWidth = compactArmor ? ICON_STEP + game.fontRenderer.getStringWidth(armorText) : shields * ICON_STEP;
        int furnaceWidth = tooltip.cookTime < 0 ? 0 : tooltip.furnaceSlots.isEmpty() ? 24 : 3 * SLOT_SIZE + 28;
        int width = Math.max(Math.max(textWidth + iconWidth, columns * SLOT_SIZE),
                Math.max(Math.max(harvestWidth, furnaceWidth), Math.max(healthWidth, armorWidth))) + 12;
        int height = headerHeight + harvestHeight + rows * SLOT_SIZE + 10
                + (healthRows + (shields > 0 ? 1 : 0)) * VITAL_ROW_HEIGHT
                + (furnaceWidth > 0 ? SLOT_SIZE : 0);
        OverlayTransition.Bounds expected = new OverlayTransition.Bounds(
                (event.resolution.getScaledWidth() - width) / 2, width, height);
        OverlayTransition.Bounds bounds = transition.update(expected, System.nanoTime(),
                WailaBridge.INSTANCE.getConfig("overlay.animation"));
        int left = bounds.left;
        int top = 8;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            drawRect(left, top, left + bounds.width, top + bounds.height, 0xdd12121c);
            drawRect(left, top, left + bounds.width, top + 1, 0xff8c78b8);
            if (!bounds.sameSizeAndPosition(expected)) {
                int scale = event.resolution.getScaleFactor();
                GL11.glScissor(left * scale, game.displayHeight - (top + bounds.height) * scale,
                        bounds.width * scale, bounds.height * scale);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
            if (showIcon) {
                drawItem(tooltip.icon, left + 4, top + 5 + (headerHeight - 16) / 2, false);
            }
            for (int i = 0; i < lines.size(); i++) {
                game.fontRenderer.drawStringWithShadow(lines.get(i), left + 6 + iconWidth, top + 5 + i * 10, 0xffffff);
            }
            int detailTop = top + 5 + headerHeight;
            if (tooltip.harvest != null) drawHarvest(tooltip.harvest, left + 6, detailTop);
            detailTop += harvestHeight;
            if (healthRows > 0) {
                if (compactHealth) drawVitalWithText(52, 0, healthText, left + 6, detailTop);
                else drawHearts(tooltip, hearts, extraHearts, left + 6, detailTop);
                detailTop += healthRows * VITAL_ROW_HEIGHT;
            }
            if (shields > 0) {
                if (compactArmor) drawVitalWithText(34, 9, armorText, left + 6, detailTop);
                else drawArmor(tooltip.armor, shields, left + 6, detailTop);
                detailTop += VITAL_ROW_HEIGHT;
            }
            if (furnaceWidth > 0) {
                drawFurnace(tooltip, left + 6, detailTop);
                detailTop += SLOT_SIZE;
            }
            if (columns > 0) drawInventory(tooltip.inventory, columns, left + 6, detailTop);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static int countIcons(float points) {
        return Math.max(0, Math.min(21, (int) Math.ceil(points / 2.0F)));
    }

    private void drawHearts(Tooltip tooltip, int hearts, int extraHearts, int left, int top) {
        bindTexture(Gui.icons);
        for (int i = 0; i < hearts + extraHearts; i++) {
            int x = left + (i % ICONS_PER_ROW) * ICON_STEP;
            int y = top + (i / ICONS_PER_ROW) * VITAL_ROW_HEIGHT;
            boolean extra = i >= hearts;
            float points = extra ? tooltip.absorption - (i - hearts) * 2 : tooltip.health - i * 2;
            drawTexturedModalRect(x, y, 16, 0, 9, 9);
            if (points > 0) {
                int full = extra ? 160 : 52;
                drawTexturedModalRect(x, y, points > 1 ? full : full + 9, 0, 9, 9);
            }
        }
    }

    private void drawArmor(int armor, int shields, int left, int top) {
        bindTexture(Gui.icons);
        for (int i = 0; i < shields; i++) {
            drawTexturedModalRect(left + i * ICON_STEP, top, armor - i * 2 >= 2 ? 34 : 25, 9, 9, 9);
        }
    }

    private void drawVitalWithText(int u, int v, String text, int left, int top) {
        bindTexture(Gui.icons);
        drawTexturedModalRect(left, top, u, v, 9, 9);
        game.fontRenderer.drawStringWithShadow(text, left + ICON_STEP, top, 0xffffff);
    }

    private void drawFurnace(Tooltip tooltip, int left, int top) {
        int arrowX = left;
        if (!tooltip.furnaceSlots.isEmpty()) {
            drawFurnaceSlot(tooltip.furnaceSlots.get(0), left, top);
            drawFurnaceSlot(tooltip.furnaceSlots.get(1), left + SLOT_SIZE, top);
            arrowX += 2 * SLOT_SIZE + 2;
            drawFurnaceSlot(tooltip.furnaceSlots.get(2), arrowX + 26, top);
        }
        drawProgressArrow(arrowX, top + 1, tooltip.cookTime * 24 / 200);
    }

    private void drawProgressArrow(int x, int y, int filled) {
        for (int row = 0; row < 16; row++) {
            int start = row >= 5 && row < 11 ? 0 : 15;
            int end = 16 + Math.min(row, 15 - row);
            drawRect(x + start, y + row, x + end, y + row + 1, 0xff777985);
            if (filled > start) {
                drawRect(x + start, y + row, x + Math.min(filled, end), y + row + 1, 0xfff2f2f5);
            }
        }
    }

    private void drawFurnaceSlot(ItemStack stack, int x, int y) {
        if (stack != null) drawItem(stack, x + 1, y + 1, true);
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
            drawItem(icon.stack, x, top, false);
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

    private void drawItem(ItemStack stack, int x, int y, boolean count) {
        // GUI block models need depth writes to occlude their own back faces.
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        RenderHelper.enableGUIStandardItemLighting();
        itemRenderer.renderItemAndEffectIntoGUI(game.fontRenderer, game.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        if (count) {
            itemRenderer.renderItemOverlayIntoGUI(game.fontRenderer, game.getTextureManager(), stack, x, y);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
    }

    private void drawInventory(List<ItemStack> stacks, int columns, int left, int top) {
        for (int i = 0; i < stacks.size(); i++) {
            int x = left + 1 + (i % columns) * SLOT_SIZE;
            int y = top + 1 + (i / columns) * SLOT_SIZE;
            drawItem(stacks.get(i), x, y, true);
        }
    }

    @ForgeSubscribe
    public void decorate(RenderWorldLastEvent event) {
        Target target = target(event.partialTicks, false);
        if (target == null || target.getBlock() == null) return;
        WailaBridge bridge = WailaBridge.INSTANCE;
        for (IWailaBlockDecorator decorator : bridge.decorators(target)) {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushMatrix();
            try {
                decorator.decorateBlock(target.getStack(), target, bridge);
            } catch (RuntimeException e) {
                bridge.report(decorator.getClass().getName(), e);
            } catch (LinkageError e) {
                bridge.report(decorator.getClass().getName(), e);
            } finally {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        }
    }

    private Target target(float partialTicks, boolean request) {
        MovingObjectPosition hit = game.objectMouseOver;
        if (hit == null || game.theWorld == null || game.thePlayer == null || game.renderViewEntity == null
                || game.currentScreen != null || game.gameSettings.hideGUI
                || !WailaBridge.INSTANCE.getConfig("overlay.enabled")) return null;
        ItemStack stack = null;
        if (hit.typeOfHit == EnumMovingObjectType.TILE) {
            int id = game.theWorld.getBlockId(hit.blockX, hit.blockY, hit.blockZ);
            Block block = Block.blocksList[id];
            if (block == null) return null;
            stack = block.getPickBlock(hit, game.theWorld, hit.blockX, hit.blockY, hit.blockZ);
            if (stack == null) stack = new ItemStack(id, 1,
                    game.theWorld.getBlockMetadata(hit.blockX, hit.blockY, hit.blockZ));
            if (stack.getItem() == null) stack = null;
        }
        Target target = new Target(game.theWorld, game.thePlayer, game.renderViewEntity, hit, partialTicks, stack);
        remote.apply(target, request);
        return target;
    }
}
