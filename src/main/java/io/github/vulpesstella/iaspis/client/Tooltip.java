package io.github.vulpesstella.iaspis.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import io.github.vulpesstella.iaspis.compat.Target;
import io.github.vulpesstella.iaspis.compat.InventoryPreview;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import io.github.vulpesstella.iaspis.compat.WailaBridge.Section;
import mcp.mobius.waila.api.IWailaBlock;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaEntityProvider;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.StatCollector;

final class Tooltip {
    private static final WailaBridge BRIDGE = WailaBridge.INSTANCE;

    final List<String> lines = new ArrayList<String>();
    ItemStack icon;
    HarvestHint harvest;
    List<ItemStack> inventory = Collections.emptyList();
    List<ItemStack> furnaceSlots = Collections.emptyList();
    int cookTime = -1;
    float health;
    float maxHealth;
    float absorption;
    int armor;

    Tooltip(Target target, boolean details, String detailKey) {
        if (target.getBlock() != null) buildBlock(target);
        else if (target.getEntity() != null) buildEntity(target);

        InventoryPreview preview = target.getInventoryPreview();
        if (target.getTileEntity() instanceof TileEntityFurnace) {
            furnaceSlots = preview.furnaceSlots;
            if (!furnaceSlots.isEmpty() && cookTime < 0) cookTime = 0;
            if (!furnaceSlots.isEmpty()) return;
        }
        int limit = details ? InventoryPreview.DETAIL_LIMIT : InventoryPreview.COMPACT_LIMIT;
        inventory = preview.stacks.subList(0, Math.min(limit, preview.stacks.size()));
        if (!details && (preview.stacks.size() > limit || preview.truncated)) {
            lines.add("\u00a77" + StatCollector.translateToLocalFormatted("iaspis.details.hold", detailKey));
        } else if (details && preview.truncated) {
            lines.add("\u00a77" + StatCollector.translateToLocal("iaspis.inventory.truncated"));
        }
    }

    private void buildBlock(Target target) {
        Block block = target.getBlock();
        IWailaBlock direct = block instanceof IWailaBlock ? (IWailaBlock) block : null;
        icon = pickStack(target, direct);
        List<String> head = section(icon == null ? block.getLocalizedName() : icon.getDisplayName());
        List<String> body = new ArrayList<String>();
        addBlockDetails(target, body);
        if (BRIDGE.getConfig("harvest.enabled")) harvest = HarvestHint.create(target, body);
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(block);
        List<String> tail = source(id == null ? null : Loader.instance().getIndexedModList().get(id.modId),
                block.getClass());
        lines.addAll(blockSection(Section.HEAD, target, direct, head));
        lines.addAll(blockSection(Section.BODY, target, direct, body));
        lines.addAll(blockSection(Section.TAIL, target, direct, tail));
    }

    private ItemStack pickStack(Target target, IWailaBlock direct) {
        if (direct != null) {
            try {
                ItemStack stack = direct.getWailaStack(target, BRIDGE);
                if (stack != null) return stack;
            } catch (RuntimeException e) {
                BRIDGE.report(direct.getClass().getName(), e);
            } catch (LinkageError e) {
                BRIDGE.report(direct.getClass().getName(), e);
            }
        }
        for (IWailaDataProvider provider : BRIDGE.blocks(Section.OVERRIDE, target)) {
            try {
                ItemStack stack = provider.getWailaStack(target, BRIDGE);
                if (stack != null) return stack;
            } catch (RuntimeException e) {
                BRIDGE.report(provider.getClass().getName(), e);
            } catch (LinkageError e) {
                BRIDGE.report(provider.getClass().getName(), e);
            }
        }
        return target.getStack();
    }

    private List<String> blockSection(Section part, Target target, IWailaBlock direct, List<String> lines) {
        if (direct != null) {
            try {
                switch (part) {
                    case HEAD: return keep(direct.getWailaHead(icon, lines, target, BRIDGE), lines);
                    case BODY: return keep(direct.getWailaBody(icon, lines, target, BRIDGE), lines);
                    case TAIL: return keep(direct.getWailaTail(icon, lines, target, BRIDGE), lines);
                    default: throw new AssertionError(part);
                }
            } catch (RuntimeException e) {
                BRIDGE.report(direct.getClass().getName(), e);
            } catch (LinkageError e) {
                BRIDGE.report(direct.getClass().getName(), e);
            }
        }
        for (IWailaDataProvider provider : BRIDGE.blocks(part, target)) {
            try {
                List<String> result;
                switch (part) {
                    case HEAD: result = provider.getWailaHead(icon, lines, target, BRIDGE); break;
                    case BODY: result = provider.getWailaBody(icon, lines, target, BRIDGE); break;
                    case TAIL: result = provider.getWailaTail(icon, lines, target, BRIDGE); break;
                    default: throw new AssertionError(part);
                }
                lines = keep(result, lines);
            } catch (RuntimeException e) {
                BRIDGE.report(provider.getClass().getName(), e);
            } catch (LinkageError e) {
                BRIDGE.report(provider.getClass().getName(), e);
            }
        }
        return lines;
    }

    private void addBlockDetails(Target target, List<String> lines) {
        Block block = target.getBlock();
        int stages = 0;
        if (block == Block.crops || block == Block.carrot || block == Block.potato) stages = 7;
        else if (block == Block.netherStalk) stages = 3;
        if (stages > 0) {
            int growth = target.getMetadata();
            lines.add(growth >= stages ? "\u00a7a" + StatCollector.translateToLocal("iaspis.growth.mature")
                    : StatCollector.translateToLocalFormatted("iaspis.growth", Math.round(growth * 100.0F / stages)));
        }
        if (target.getTileEntity() instanceof TileEntityFurnace) {
            NBTTagCompound data = target.getNBTData();
            int progress = target.getNBTInteger(data, "CookTime");
            if (progress > 0) cookTime = Math.min(200, progress);
        }
    }

    private void buildEntity(Target target) {
        Entity entity = target.getEntity();
        for (IWailaEntityProvider provider : BRIDGE.entities(Section.OVERRIDE, target)) {
            try {
                Entity override = provider.getWailaOverride(target, BRIDGE);
                if (override != null) {
                    entity = override;
                    break;
                }
            } catch (RuntimeException e) {
                BRIDGE.report(provider.getClass().getName(), e);
            } catch (LinkageError e) {
                BRIDGE.report(provider.getClass().getName(), e);
            }
        }
        List<String> body = new ArrayList<String>();
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            health = living.getHealth();
            maxHealth = living.getMaxHealth();
            absorption = living.getAbsorptionAmount();
            armor = living.getTotalArmorValue();
        }
        EntityRegistry.EntityRegistration registration = EntityRegistry.instance().lookupModSpawn(entity.getClass(), true);
        lines.addAll(entitySection(Section.HEAD, target, entity, section(entity.getTranslatedEntityName())));
        lines.addAll(entitySection(Section.BODY, target, entity, body));
        lines.addAll(entitySection(Section.TAIL, target, entity,
                source(registration == null ? null : registration.getContainer(), entity.getClass())));
    }

    private List<String> entitySection(Section part, Target target, Entity entity, List<String> lines) {
        for (IWailaEntityProvider provider : BRIDGE.entities(part, target)) {
            try {
                List<String> result;
                switch (part) {
                    case HEAD: result = provider.getWailaHead(entity, lines, target, BRIDGE); break;
                    case BODY: result = provider.getWailaBody(entity, lines, target, BRIDGE); break;
                    case TAIL: result = provider.getWailaTail(entity, lines, target, BRIDGE); break;
                    default: throw new AssertionError(part);
                }
                lines = keep(result, lines);
            } catch (RuntimeException e) {
                BRIDGE.report(provider.getClass().getName(), e);
            } catch (LinkageError e) {
                BRIDGE.report(provider.getClass().getName(), e);
            }
        }
        return lines;
    }

    private static List<String> source(ModContainer mod, Class<?> type) {
        if (mod != null) return section("\u00a79\u00a7o" + mod.getName());
        if (type.getName().startsWith("net.minecraft.")) return section("\u00a79\u00a7oMinecraft");
        return new ArrayList<String>();
    }

    private static List<String> section(String line) {
        return new ArrayList<String>(Collections.singletonList(line));
    }

    private static List<String> keep(List<String> result, List<String> fallback) {
        return result == null ? fallback : result;
    }
}
