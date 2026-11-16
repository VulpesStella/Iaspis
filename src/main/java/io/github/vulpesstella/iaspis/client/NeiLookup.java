package io.github.vulpesstella.iaspis.client;

import java.lang.reflect.Method;

import cpw.mods.fml.common.Loader;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

final class NeiLookup {
    static void open(Minecraft game, Overlay overlay, boolean uses) {
        if (game.thePlayer == null || game.theWorld == null) return;
        if (!Loader.isModLoaded("NotEnoughItems")) {
            message(game, "iaspis.nei.unavailable");
            return;
        }
        ItemStack stack = overlay.lookupStack();
        if (stack == null || stack.getItem() == null) return;
        try {
            String screen = uses ? "GuiUsageRecipe" : "GuiCraftingRecipe";
            Class<?> gui = Class.forName("codechicken.nei.recipe." + screen);
            Method open = gui.getMethod("openRecipeGui", String.class, Object[].class);
            // The 1.6.4 recipe views require an inventory screen to return to.
            game.displayGuiScreen(new GuiInventory(game.thePlayer));
            if (Boolean.TRUE.equals(open.invoke(null, "item", new Object[] { stack.copy() }))) return;
            game.displayGuiScreen(null);
            message(game, "iaspis.nei.noResults");
        } catch (ReflectiveOperationException e) {
            restore(game, e);
        } catch (RuntimeException e) {
            restore(game, e);
        } catch (LinkageError e) {
            restore(game, e);
        }
    }

    private static void restore(Minecraft game, Throwable failure) {
        if (game.currentScreen != null) game.displayGuiScreen(null);
        WailaBridge.INSTANCE.report("Could not open the NEI lookup", failure);
        message(game, "iaspis.nei.unavailable");
    }

    private static void message(Minecraft game, String key) {
        game.thePlayer.addChatMessage(StatCollector.translateToLocal(key));
    }

    private NeiLookup() {}
}
