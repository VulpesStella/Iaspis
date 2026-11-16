package io.github.vulpesstella.iaspis.client;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.compat.Target;
import io.github.vulpesstella.iaspis.compat.TankPreview;
import io.github.vulpesstella.iaspis.compat.InventoryPreview;
import io.github.vulpesstella.iaspis.compat.MachinePreview;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import io.github.vulpesstella.iaspis.compat.WailaBridge.Section;
import io.github.vulpesstella.iaspis.client.HudSettings.NumberOption;
import mcp.mobius.waila.api.IWailaBlock;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaEntityProvider;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

final class Tooltip {
    private static final WailaBridge BRIDGE = WailaBridge.INSTANCE;

    final List<String> lines = new ArrayList<String>();
    ItemStack icon;
    Fluid fluid;
    List<TankPreview> tanks = Collections.emptyList();
    MachinePreview energy = MachinePreview.EMPTY;
    float processing;
    HarvestHint harvest;
    List<ItemStack> inventory = Collections.emptyList();
    List<ItemStack> furnaceSlots = Collections.emptyList();
    int cookTime = -1;
    float health;
    float maxHealth;
    float absorption;
    int armor;

    Tooltip(Target target, boolean details, String detailKey) {
        this(target, details, detailKey, false);
    }

    Tooltip(Target target, boolean details, String detailKey, boolean lite) {
        if (target.getBlock() != null) buildBlock(target, lite);
        else if (target.getEntity() != null) buildEntity(target, lite);

        if (lite) return;
        List<TankPreview> tankPreview = target.getTankPreview();
        if (!PreviewSample.TANK_DETAILS.enabled() || details) tanks = tankPreview;
        MachinePreview machine = target.getMachinePreview();
        boolean showMachine = !PreviewSample.MACHINE_DETAILS.enabled() || details;
        boolean energyAvailable = machine.unit != null && PreviewSample.ENERGY.enabled();
        boolean processingAvailable = machine.progress > 0 && PreviewSample.PROCESSING.enabled();
        if (showMachine && energyAvailable) energy = machine;
        if (showMachine && processingAvailable) processing = machine.progress;
        InventoryPreview preview = target.getInventoryPreview();
        boolean showInventory = true;
        if (target.getTileEntity() instanceof TileEntityFurnace && Iaspis.config().getBoolean("furnace.enabled")) {
            int progress = target.getNBTInteger(target.getNBTData(), "CookTime");
            showInventory = !applyFurnaceProgress(preview.furnaceSlots, progress);
        }
        if (showInventory) applyInventory(preview.stacks, preview.truncated, details, detailKey);
        if ((tanks.isEmpty() && !tankPreview.isEmpty()) || (!showMachine && (energyAvailable || processingAvailable))) {
            String hint = "\u00a77" + StatCollector.translateToLocalFormatted("iaspis.details.hold", detailKey);
            if (!lines.contains(hint)) lines.add(hint);
        }
    }

    private void applyInventory(List<ItemStack> stacks, boolean truncated, boolean details, String detailKey) {
        int limit = (details ? NumberOption.INVENTORY_DETAILS : NumberOption.INVENTORY_LIMIT).get();
        if (!stacks.isEmpty() && PreviewSample.INVENTORY_SORT.enabled()) {
            stacks = new ArrayList<ItemStack>(stacks);
            Collections.sort(stacks, new Comparator<ItemStack>() {
                @Override
                public int compare(ItemStack a, ItemStack b) {
                    return Integer.compare(b.stackSize, a.stackSize);
                }
            });
        }
        inventory = stacks.subList(0, Math.min(limit, stacks.size()));
        if (!details && NumberOption.INVENTORY_DETAILS.get() > limit && (stacks.size() > limit || truncated)) {
            lines.add("\u00a77" + StatCollector.translateToLocalFormatted("iaspis.details.hold", detailKey));
        } else if (truncated || stacks.size() > limit) {
            lines.add("\u00a77" + StatCollector.translateToLocal("iaspis.inventory.truncated"));
        }
        int names = NumberOption.INVENTORY_NAMES.get();
        if (!inventory.isEmpty() && stacks.size() <= names && !truncated) {
            for (ItemStack stack : inventory) lines.add(stack.getDisplayName());
        }
    }

    private Tooltip() {}

    static Tooltip preview(PreviewSample sample, long millis) {
        Tooltip tooltip = new Tooltip();
        String entityName = null;
        String detail = null;
        List<ItemStack> exampleInventory = Collections.emptyList();
        boolean showMoreItems = false;
        switch (sample) {
            case HEALTH:
            case HEALTH_FRACTION:
            case ENTITIES:
                entityName = StatCollector.translateToLocal("entity.Slime.name");
                if (PreviewSample.HEALTH.enabled() || sample != PreviewSample.HEALTH) {
                    tooltip.maxHealth = 4;
                    tooltip.health = millis % 2400 < 1600 ? 4 : 3;
                }
                break;
            case HEALTH_LIMIT:
            case HEALTH_COLUMNS:
                entityName = StatCollector.translateToLocal("entity.PigZombie.name");
                tooltip.maxHealth = 36;
                tooltip.health = 29;
                break;
            case ARMOR:
            case ARMOR_LIMIT:
                entityName = StatCollector.translateToLocal("entity.Zombie.name");
                if (PreviewSample.ARMOR.enabled() || sample == PreviewSample.ARMOR_LIMIT) tooltip.armor = 18;
                break;
            case HARVEST:
            case HARVEST_CREATIVE:
            case HARVEST_NEW_LINE:
                tooltip.icon = new ItemStack(Block.oreDiamond);
                if (sample == PreviewSample.HARVEST_NEW_LINE || sample.enabled()) {
                    tooltip.harvest = HarvestHint.preview(Block.oreDiamond, millis % 2400 >= 1200);
                }
                break;
            case HARVEST_EFFECTIVE:
                tooltip.icon = new ItemStack(Block.wood);
                if (sample.enabled()) tooltip.harvest = HarvestHint.preview(Block.wood, true);
                break;
            case HARVEST_UNBREAKABLE:
                tooltip.icon = new ItemStack(Block.bedrock);
                if (sample.enabled()) detail = StatCollector.translateToLocal("iaspis.harvest.unbreakable");
                break;
            case GROWTH:
                tooltip.icon = new ItemStack(Item.wheat);
                if (Iaspis.config().getBoolean(sample.key)) detail = cropProgress(millis % 2400 < 1500 ? 4 : 7, 7);
                break;
            case FURNACE:
                tooltip.icon = new ItemStack(Block.furnaceIdle);
                List<ItemStack> items = Iaspis.config().getBoolean("inventory.preview")
                        ? Arrays.asList(new ItemStack(Block.oreIron, 8), new ItemStack(Item.coal, 5),
                                new ItemStack(Item.ingotIron, 2))
                        : Collections.<ItemStack>emptyList();
                boolean showProgress = Iaspis.config().getBoolean(sample.key);
                int progress = 1 + (int) (millis % 2200) * 199 / 2200;
                if (!showProgress || !tooltip.applyFurnaceProgress(items, progress)) {
                    exampleInventory = items;
                }
                break;
            case FLUID_TARGET:
                tooltip.fluid = FluidRegistry.WATER;
                entityName = StatCollector.translateToLocal(millis % 3200 < 1600
                        ? "iaspis.fluid.source" : "iaspis.fluid.flowing");
                break;
            case TANK:
            case TANK_DETAILS:
            case TANK_BAR:
                tooltip.icon = new ItemStack(Item.cauldron);
                entityName = StatCollector.translateToLocal("iaspis.tank.example");
                if (PreviewSample.TANK.enabled()) {
                    if (PreviewSample.TANK_DETAILS.enabled() && millis % 3200 < 1600) {
                        detail = "\u00a77" + StatCollector.translateToLocalFormatted("iaspis.details.hold", "Shift");
                    } else {
                        tooltip.tanks = Arrays.asList(new TankPreview(FluidRegistry.WATER, 6000, 8000),
                                new TankPreview(null, 0, 4000));
                    }
                }
                break;
            case ENERGY:
            case ENERGY_BAR:
            case PROCESSING:
            case MACHINE_DETAILS:
                tooltip.icon = new ItemStack(Block.furnaceIdle);
                entityName = StatCollector.translateToLocal("iaspis.machine.example");
                boolean energySample = sample != PreviewSample.PROCESSING && PreviewSample.ENERGY.enabled();
                boolean progressSample = (sample == PreviewSample.PROCESSING || sample == PreviewSample.MACHINE_DETAILS)
                        && PreviewSample.PROCESSING.enabled();
                if ((energySample || progressSample) && PreviewSample.MACHINE_DETAILS.enabled()
                        && millis % 3200 < 1600) {
                    detail = "\u00a77" + StatCollector.translateToLocalFormatted("iaspis.details.hold", "Shift");
                } else {
                    if (energySample) tooltip.energy = new MachinePreview(MachinePreview.EnergyUnit.EU, 1800, 4000, 0);
                    if (progressSample) tooltip.processing = 0.25F + (millis % 2200) / 4400.0F;
                }
                break;
            case BOSS_BAR:
                tooltip.icon = new ItemStack(Block.obsidian);
                break;
            case BREAKING:
                tooltip.icon = new ItemStack(Block.stone);
                break;
            case BOSSES:
                entityName = StatCollector.translateToLocal("entity.WitherBoss.name");
                tooltip.maxHealth = 300;
                tooltip.health = 226;
                break;
            case AUTO_SCALE:
            case INVENTORY_LIMIT:
            case INVENTORY_DETAILS:
            case INVENTORY_COLUMNS:
            case INVENTORY_SORT:
                tooltip.icon = new ItemStack(Block.chest);
                showMoreItems = sample == PreviewSample.INVENTORY_DETAILS || sample == PreviewSample.INVENTORY_COLUMNS
                        || sample == PreviewSample.AUTO_SCALE;
                exampleInventory = exampleInventory();
                break;
            default:
                boolean alternate = sample == PreviewSample.ANIMATION && millis % 2300 >= 1150;
                tooltip.icon = new ItemStack(alternate ? Block.stone : Block.chest);
                boolean compact = sample == PreviewSample.MODE && HudSettings.NumberOption.MODE.get() == HudSettings.LITE
                        && millis % 2400 < 1400;
                if (!alternate && !compact && (sample != PreviewSample.INVENTORY || Iaspis.config().getBoolean(sample.key))) {
                    exampleInventory = Arrays.asList(new ItemStack(Item.appleRed, 3),
                            new ItemStack(Block.planks, 12), new ItemStack(Item.ingotIron, 2));
                }
                break;
        }
        tooltip.lines.add(entityName == null ? tooltip.icon.getDisplayName() : entityName);
        if (detail != null) tooltip.lines.add(detail);
        if (PreviewSample.MOD_NAME.enabled()) tooltip.lines.add("\u00a79\u00a7oMinecraft");
        if (!exampleInventory.isEmpty()) {
            tooltip.applyInventory(exampleInventory, false, showMoreItems, "Shift");
        }
        return tooltip;
    }

    private static List<ItemStack> exampleInventory() {
        List<ItemStack> items = new ArrayList<ItemStack>(InventoryPreview.DETAIL_LIMIT);
        for (int variant = 0; variant < InventoryPreview.DETAIL_LIMIT; variant++) {
            Block block = variant < 16 ? Block.cloth : Block.stainedClay;
            items.add(new ItemStack(block, 1 + (variant * 7) % 64, variant % 16));
        }
        return items;
    }

    private boolean applyFurnaceProgress(List<ItemStack> slots, int progress) {
        furnaceSlots = slots;
        if (progress > 0 || !slots.isEmpty()) cookTime = Math.max(0, Math.min(200, progress));
        return !slots.isEmpty();
    }

    static String tankLabel(TankPreview tank) {
        String name = tank.fluid == null ? StatCollector.translateToLocal(
                tank.amount == 0 ? "iaspis.tank.empty" : "iaspis.tank.unknown") : fluidName(tank.fluid);
        return StatCollector.translateToLocalFormatted("iaspis.tank.amount", name, tank.amount, tank.capacity);
    }

    static String energyLabel(MachinePreview energy) {
        NumberFormat quantities = NumberFormat.getNumberInstance();
        quantities.setMaximumFractionDigits(2);
        return StatCollector.translateToLocalFormatted("iaspis.machine.energyAmount",
                quantities.format(energy.stored), quantities.format(energy.capacity), energy.unit.name());
    }

    private static String fluidName(Fluid fluid) {
        if (fluid == FluidRegistry.WATER) return StatCollector.translateToLocal("tile.water.name");
        if (fluid == FluidRegistry.LAVA) return StatCollector.translateToLocal("tile.lava.name");
        return fluid.getLocalizedName();
    }

    private void buildBlock(Target target, boolean lite) {
        Block block = target.getBlock();
        IWailaBlock direct = block instanceof IWailaBlock ? (IWailaBlock) block : null;
        icon = pickStack(target, direct);
        if (icon == null) fluid = FluidTarget.fluid(block);
        List<String> head = section(fluid != null ? fluidName(fluid)
                : icon == null ? block.getLocalizedName() : icon.getDisplayName());
        List<String> body = new ArrayList<String>();
        if (!lite) {
            addBlockDetails(target, body);
            if (FluidTarget.fluid(block) == null && Iaspis.config().getBoolean("harvest.enabled")) harvest = HarvestHint.create(target, body);
        }
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(block);
        List<String> tail = source(id == null ? null : Loader.instance().getIndexedModList().get(id.modId),
                block.getClass());
        lines.addAll(blockSection(Section.HEAD, target, direct, head));
        if (!lite) lines.addAll(blockSection(Section.BODY, target, direct, body));
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
        if (stages > 0 && Iaspis.config().getBoolean("growth.enabled")) {
            lines.add(cropProgress(target.getMetadata(), stages));
        }
    }

    private static String cropProgress(int age, int stages) {
        return age >= stages ? "\u00a7a" + StatCollector.translateToLocal("iaspis.growth.mature")
                : StatCollector.translateToLocalFormatted("iaspis.growth", Math.round(age * 100.0F / stages));
    }

    private void buildEntity(Target target, boolean lite) {
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
        if (!lite && entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            if (Iaspis.config().getBoolean("health.enabled")) {
                health = living.getHealth();
                maxHealth = living.getMaxHealth();
                absorption = living.getAbsorptionAmount();
            }
            if (Iaspis.config().getBoolean("armor.enabled")) armor = living.getTotalArmorValue();
        }
        EntityRegistry.EntityRegistration registration = EntityRegistry.instance().lookupModSpawn(entity.getClass(), true);
        lines.addAll(entitySection(Section.HEAD, target, entity, section(entity.getTranslatedEntityName())));
        if (!lite) lines.addAll(entitySection(Section.BODY, target, entity, body));
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
        if (!PreviewSample.MOD_NAME.enabled()) return new ArrayList<String>();
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
