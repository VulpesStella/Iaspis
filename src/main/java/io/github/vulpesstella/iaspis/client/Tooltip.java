package io.github.vulpesstella.iaspis.client;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.IaspisConfig.DisplayMode;
import io.github.vulpesstella.iaspis.api.HudComponent;
import io.github.vulpesstella.iaspis.client.HudSettings.NumberOption;
import io.github.vulpesstella.iaspis.compat.HudProviderRegistry.Changes;
import io.github.vulpesstella.iaspis.compat.InventoryPreview;
import io.github.vulpesstella.iaspis.compat.MachinePreview;
import io.github.vulpesstella.iaspis.compat.MultipartAccessor;
import io.github.vulpesstella.iaspis.compat.TankPreview;
import io.github.vulpesstella.iaspis.compat.Target;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import io.github.vulpesstella.iaspis.compat.WailaBridge.Section;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import mcp.mobius.waila.api.IWailaBlock;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaEntityProvider;
import mcp.mobius.waila.api.IWailaFMPProvider;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

final class Tooltip {
    private static final WailaBridge BRIDGE = WailaBridge.INSTANCE;

    final List<String> lines = new ArrayList<String>();
    List<HudComponent> components = Collections.emptyList();
    private boolean hasHead;
    private int bodyEnd;
    ItemStack icon;
    Fluid fluid;
    List<TankPreview> tanks = Collections.emptyList();
    MachinePreview energy = MachinePreview.EMPTY;
    float processing;
    float brewProgress = -1;
    ItemStack detailItem;
    String detailLabel;
    List<VanillaEntityDetails.EffectLine> effects = Collections.emptyList();
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
        this(target, details, detailKey, lite, null);
    }

    Tooltip(Target target, boolean details, String detailKey, boolean lite, Changes changes) {
        if (target.getBlock() != null) buildBlock(target, lite);
        else if (target.getEntity() != null) buildEntity(target, lite);

        if (changes != null) {
            if (!lite) {
                lines.addAll(bodyEnd, changes.lines);
                components = changes.components;
            }
            if (changes.name != null) {
                if (hasHead) lines.set(0, changes.name);
                else lines.add(0, changes.name);
            }
            if (changes.icon != null) {
                icon = changes.icon;
                fluid = null;
            }
        }

        if (lite) return;
        List<TankPreview> tankPreview = target.getTankPreview();
        DisplayMode tankDisplay = Iaspis.config().getTankDisplay();
        if (tankDisplay.shows(details)) tanks = tankPreview;
        MachinePreview machine = target.getMachinePreview();
        if (machine.unit != null) energy = machine;
        processing = machine.progress;
        if (energy.unit != null) {
            if (energy.maxInput >= 0)
                lines.add(bodyEnd++, energyLimit("inputLimit", energy.maxInput, energy.unit));
            if (energy.maxOutput >= 0)
                lines.add(bodyEnd++, energyLimit("outputLimit", energy.maxOutput, energy.unit));
        }
        InventoryPreview preview = target.getInventoryPreview();
        boolean showInventory = true;
        if (target.getTileEntity() instanceof TileEntityFurnace
                && Iaspis.config().getBoolean("furnace.enabled")) {
            int progress = target.getNBTInteger(target.getNBTData(), "CookTime");
            showInventory = !applyFurnaceProgress(preview.furnaceSlots, progress);
        }
        if (showInventory) applyInventory(preview.stacks, preview.truncated, details, detailKey);
        if (detailKey != null
                && tankDisplay == DisplayMode.DETAILS
                && !details
                && !tankPreview.isEmpty()) {
            String hint =
                    "\u00a77"
                            + StatCollector.translateToLocalFormatted(
                                    "iaspis.details.hold", detailKey);
            if (!lines.contains(hint)) lines.add(hint);
        }
    }

    private void applyInventory(
            List<ItemStack> stacks, boolean truncated, boolean details, String detailKey) {
        int limit = (details ? NumberOption.INVENTORY_DETAILS : NumberOption.INVENTORY_LIMIT).get();
        if (!stacks.isEmpty() && PreviewSample.INVENTORY_SORT.enabled()) {
            stacks = new ArrayList<ItemStack>(stacks);
            Collections.sort(
                    stacks,
                    new Comparator<ItemStack>() {
                        @Override
                        public int compare(ItemStack a, ItemStack b) {
                            return Integer.compare(b.stackSize, a.stackSize);
                        }
                    });
        }
        inventory = stacks.subList(0, Math.min(limit, stacks.size()));
        if (detailKey != null
                && !details
                && NumberOption.INVENTORY_DETAILS.get() > limit
                && (stacks.size() > limit || truncated)) {
            lines.add(
                    "\u00a77"
                            + StatCollector.translateToLocalFormatted(
                                    "iaspis.details.hold", detailKey));
        } else if (truncated || stacks.size() > limit) {
            lines.add("\u00a77" + StatCollector.translateToLocal("iaspis.inventory.truncated"));
        }
        int names = NumberOption.INVENTORY_NAMES.get();
        if (!inventory.isEmpty() && stacks.size() <= names && !truncated) {
            for (ItemStack stack : inventory) lines.add(stack.getDisplayName());
        }
    }

    private Tooltip() {}

    static Tooltip preview(PreviewSample sample, long millis, String detailKey) {
        Tooltip tooltip = new Tooltip();
        String entityName = null;
        String detail = null;
        List<ItemStack> exampleInventory = Collections.emptyList();
        List<String> entityDetails = new ArrayList<String>();
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
                if (PreviewSample.ARMOR.enabled() || sample == PreviewSample.ARMOR_LIMIT)
                    tooltip.armor = 18;
                break;
            case HARVEST:
            case HARVEST_STYLE:
            case HARVEST_CREATIVE:
            case HARVEST_NEW_LINE:
                boolean shearPreview =
                        sample == PreviewSample.HARVEST_STYLE
                                && NumberOption.HARVEST_STYLE.get()
                                        == HudSettings.HARVESTABILITY_STYLE
                                && millis % 4800 >= 2400;
                Block harvestBlock = shearPreview ? Block.leaves : Block.oreDiamond;
                tooltip.icon = new ItemStack(harvestBlock);
                if (sample == PreviewSample.HARVEST_STYLE
                        || sample == PreviewSample.HARVEST_NEW_LINE
                        || sample.enabled()) {
                    tooltip.applyHarvest(
                            HarvestHint.preview(harvestBlock, millis % 2400 >= 1200),
                            entityDetails,
                            null);
                }
                break;
            case HARVEST_EFFECTIVE:
                tooltip.icon = new ItemStack(Block.wood);
                if (sample.enabled())
                    tooltip.applyHarvest(
                            HarvestHint.preview(Block.wood, true), entityDetails, null);
                break;
            case HARVEST_UNBREAKABLE:
                tooltip.icon = new ItemStack(Block.bedrock);
                if (sample.enabled())
                    detail = StatCollector.translateToLocal("iaspis.harvest.unbreakable");
                break;
            case GROWTH:
                tooltip.icon = new ItemStack(Item.wheat);
                if (Iaspis.config().getBoolean(sample.key))
                    detail = cropProgress(millis % 2400 < 1500 ? 4 : 7, 7);
                break;
            case FURNACE:
                tooltip.icon = new ItemStack(Block.furnaceIdle);
                List<ItemStack> items =
                        Iaspis.config().getBoolean("inventory.preview")
                                ? Arrays.asList(
                                        new ItemStack(Block.oreIron, 8),
                                        new ItemStack(Item.coal, 5),
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
                entityName =
                        StatCollector.translateToLocal(
                                millis % 3200 < 1600
                                        ? "iaspis.fluid.source"
                                        : "iaspis.fluid.flowing");
                break;
            case TANK:
            case TANK_BAR:
                tooltip.icon = new ItemStack(Item.cauldron);
                entityName = StatCollector.translateToLocal("iaspis.tank.example");
                DisplayMode tankSample = Iaspis.config().getTankDisplay();
                boolean showTankDetails = millis % 3200 >= 1600;
                if (tankSample.shows(showTankDetails)) {
                    tooltip.tanks =
                            Arrays.asList(
                                    new TankPreview(FluidRegistry.WATER, 6000, 8000),
                                    new TankPreview(null, 0, 4000));
                } else if (tankSample == DisplayMode.DETAILS && detailKey != null) {
                    detail =
                            "\u00a77"
                                    + StatCollector.translateToLocalFormatted(
                                            "iaspis.details.hold", detailKey);
                }
                break;
            case BREWING:
                tooltip.icon = new ItemStack(Item.brewingStand);
                if (sample.enabled()) tooltip.brewProgress = 0.2F + (millis % 2200) / 3000.0F;
                if (PreviewSample.INVENTORY.enabled()) {
                    exampleInventory =
                            Arrays.asList(
                                    new ItemStack(Item.potion),
                                    new ItemStack(Item.netherStalkSeeds, 3));
                }
                break;
            case SPAWNER:
                tooltip.icon = new ItemStack(Block.mobSpawner);
                if (sample.enabled()) detail = VanillaBlockDetails.spawns("Skeleton");
                break;
            case HOPPER:
                tooltip.icon = new ItemStack(Block.hopperBlock);
                if (sample.enabled()) detail = VanillaBlockDetails.hopper(millis % 3000 < 1500);
                if (PreviewSample.INVENTORY.enabled())
                    exampleInventory = Collections.singletonList(new ItemStack(Item.coal, 8));
                break;
            case NOTE_BLOCK:
                tooltip.icon = new ItemStack(Block.music);
                if (sample.enabled()) detail = VanillaBlockDetails.note((int) (millis / 700 % 25));
                break;
            case JUKEBOX:
                tooltip.icon = new ItemStack(Block.jukebox);
                if (sample.enabled())
                    VanillaBlockDetails.setRecord(tooltip, new ItemStack(Item.recordCat));
                break;
            case ENCHANTING:
                tooltip.icon = new ItemStack(Block.enchantmentTable);
                if (sample.enabled())
                    detail = VanillaBlockDetails.enchanting(millis % 2800 < 1400 ? 8 : 15);
                break;
            case ANIMAL:
                entityName = StatCollector.translateToLocal("entity.Cow.name");
                if (sample.enabled())
                    detail =
                            VanillaEntityDetails.timed(
                                    millis % 3400 < 1700
                                            ? "iaspis.entity.growth"
                                            : "iaspis.entity.cooldown",
                                    1800 - millis % 1700 / 50);
                break;
            case HORSE:
                entityName = StatCollector.translateToLocal("entity.EntityHorse.name");
                if (sample.enabled()) VanillaEntityDetails.appendHorse(entityDetails, .225, .7);
                break;
            case OWNER:
                entityName = StatCollector.translateToLocal("entity.Wolf.name");
                if (sample.enabled()) VanillaEntityDetails.appendOwner(entityDetails, "Alex");
                break;
            case VILLAGER:
                entityName = StatCollector.translateToLocal("entity.Villager.name");
                if (sample.enabled()) detail = VanillaEntityDetails.profession(1);
                break;
            case EFFECTS:
            case EFFECT_LIMIT:
                entityName = StatCollector.translateToLocal("entity.Witch.name");
                if (PreviewSample.EFFECTS.enabled()) {
                    tooltip.effects =
                            VanillaEntityDetails.effectLines(
                                    Arrays.asList(
                                            new PotionEffect(Potion.moveSpeed.id, 800, 1),
                                            new PotionEffect(Potion.damageBoost.id, 950),
                                            new PotionEffect(Potion.regeneration.id, 360),
                                            new PotionEffect(Potion.fireResistance.id, 1800),
                                            new PotionEffect(Potion.waterBreathing.id, 620),
                                            new PotionEffect(Potion.invisibility.id, 2400),
                                            new PotionEffect(Potion.nightVision.id, 900),
                                            new PotionEffect(Potion.jump.id, 340),
                                            new PotionEffect(Potion.resistance.id, 650),
                                            new PotionEffect(Potion.digSpeed.id, 400),
                                            new PotionEffect(Potion.wither.id, 200),
                                            new PotionEffect(Potion.weakness.id, 580)));
                }
                break;
            case ITEM_FRAME:
                tooltip.icon = new ItemStack(Item.itemFrame);
                if (sample.enabled())
                    VanillaEntityDetails.setDisplayedItem(tooltip, new ItemStack(Item.appleRed));
                break;
            case DROPPED_ITEM:
                entityName = StatCollector.translateToLocal("entity.Item.name");
                if (sample.enabled()) {
                    tooltip.icon = new ItemStack(Item.diamond, 4);
                    entityName = tooltip.icon.getDisplayName();
                    entityDetails.add(
                            StatCollector.translateToLocalFormatted("iaspis.entity.count", 4));
                    entityDetails.add(
                            VanillaEntityDetails.timed(
                                    "iaspis.entity.despawn", 3500 - millis % 3400 / 50));
                }
                break;
            case NARRATION:
                tooltip.icon = new ItemStack(Block.chest);
                if (sample.enabled()) {
                    detail =
                            StatCollector.translateToLocalFormatted(
                                    "iaspis.narration.example", tooltip.icon.getDisplayName());
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
                showMoreItems =
                        sample == PreviewSample.INVENTORY_DETAILS
                                || sample == PreviewSample.INVENTORY_COLUMNS
                                || sample == PreviewSample.AUTO_SCALE;
                exampleInventory = exampleInventory();
                break;
            default:
                boolean alternate = sample == PreviewSample.ANIMATION && millis % 2300 >= 1150;
                tooltip.icon = new ItemStack(alternate ? Block.stone : Block.chest);
                boolean compact =
                        sample == PreviewSample.MODE
                                && HudSettings.NumberOption.MODE.get() == HudSettings.LITE
                                && millis % 2400 < 1400;
                if (!alternate
                        && !compact
                        && (sample != PreviewSample.INVENTORY
                                || Iaspis.config().getBoolean(sample.key))) {
                    exampleInventory =
                            Arrays.asList(
                                    new ItemStack(Item.appleRed, 3),
                                    new ItemStack(Block.planks, 12),
                                    new ItemStack(Item.ingotIron, 2));
                }
                break;
        }
        tooltip.lines.add(entityName == null ? tooltip.icon.getDisplayName() : entityName);
        if (detail != null) tooltip.lines.add(detail);
        tooltip.lines.addAll(entityDetails);
        if (PreviewSample.MOD_NAME.enabled()) tooltip.lines.add("\u00a79\u00a7oMinecraft");
        if (!exampleInventory.isEmpty()) {
            tooltip.applyInventory(exampleInventory, false, showMoreItems, detailKey);
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
        String name =
                tank.fluid == null
                        ? StatCollector.translateToLocal(
                                tank.amount == 0 ? "iaspis.tank.empty" : "iaspis.tank.unknown")
                        : fluidName(tank.fluid);
        return StatCollector.translateToLocalFormatted(
                "iaspis.tank.amount", name, tank.amount, tank.capacity);
    }

    static String energyLabel(MachinePreview energy) {
        NumberFormat quantities = NumberFormat.getNumberInstance();
        quantities.setMaximumFractionDigits(2);
        return StatCollector.translateToLocalFormatted(
                "iaspis.machine.energyAmount",
                quantities.format(energy.stored),
                quantities.format(energy.capacity),
                energy.unit.name());
    }

    private static String energyLimit(String key, double value, MachinePreview.EnergyUnit unit) {
        NumberFormat quantities = NumberFormat.getNumberInstance();
        quantities.setMaximumFractionDigits(2);
        return StatCollector.translateToLocalFormatted(
                "iaspis.machine." + key, quantities.format(value), unit.name());
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
        List<String> head =
                section(
                        fluid != null
                                ? fluidName(fluid)
                                : VanillaBlockDetails.blockName(block, icon));
        List<String> body = new ArrayList<String>();
        if (!lite) {
            addBlockDetails(target, body);
            VanillaBlockDetails.append(target, this, body);
            if (FluidTarget.fluid(block) == null && PreviewSample.HARVEST.enabled()) {
                applyHarvest(HarvestHint.create(target, body), body, target);
            }
        }
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(block);
        List<String> tail =
                source(
                        id == null ? null : Loader.instance().getIndexedModList().get(id.modId),
                        block.getClass());
        List<MultipartAccessor> parts = BRIDGE.multipart(target);
        lines.addAll(
                multipartSection(
                        Section.HEAD, parts, blockSection(Section.HEAD, target, direct, head)));
        hasHead = !lines.isEmpty();
        if (!lite)
            lines.addAll(
                    multipartSection(
                            Section.BODY, parts, blockSection(Section.BODY, target, direct, body)));
        bodyEnd = lines.size();
        lines.addAll(
                multipartSection(
                        Section.TAIL, parts, blockSection(Section.TAIL, target, direct, tail)));
    }

    private void applyHarvest(HarvestHint hint, List<String> lines, Target target) {
        int style = NumberOption.HARVEST_STYLE.get();
        if (style == HudSettings.HARVESTABILITY_STYLE) {
            if (target != null) {
                HarvestabilityStyle.append(target, hint, lines);
            } else if (hint != null) {
                String color = HarvestabilityStyle.color(hint.harvestable);
                String silk = icon.itemID == Block.oreDiamond.blockID ? color + "\u2712" : "";
                String shears = icon.itemID == Block.leaves.blockID ? color + "\u2702" : "";
                HarvestabilityStyle.append(hint, silk, shears, lines);
            }
        } else if (style == HudSettings.HARVEST_TEXT) {
            if (hint != null) hint.appendText(lines);
        } else {
            harvest = hint;
        }
    }

    private List<String> multipartSection(
            Section section, List<MultipartAccessor> parts, List<String> lines) {
        for (MultipartAccessor part : parts) {
            for (IWailaFMPProvider provider : BRIDGE.multipartProviders(section, part.getID())) {
                try {
                    List<String> updated;
                    switch (section) {
                        case HEAD:
                            updated = provider.getWailaHead(icon, lines, part, BRIDGE);
                            break;
                        case BODY:
                            updated = provider.getWailaBody(icon, lines, part, BRIDGE);
                            break;
                        case TAIL:
                            updated = provider.getWailaTail(icon, lines, part, BRIDGE);
                            break;
                        default:
                            throw new AssertionError(section);
                    }
                    lines = keep(updated, lines);
                } catch (RuntimeException e) {
                    BRIDGE.report(provider.getClass().getName(), e);
                } catch (LinkageError e) {
                    BRIDGE.report(provider.getClass().getName(), e);
                }
            }
        }
        return lines;
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

    private List<String> blockSection(
            Section part, Target target, IWailaBlock direct, List<String> lines) {
        if (direct != null) {
            try {
                switch (part) {
                    case HEAD:
                        return keep(direct.getWailaHead(icon, lines, target, BRIDGE), lines);
                    case BODY:
                        return keep(direct.getWailaBody(icon, lines, target, BRIDGE), lines);
                    case TAIL:
                        return keep(direct.getWailaTail(icon, lines, target, BRIDGE), lines);
                    default:
                        throw new AssertionError(part);
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
                    case HEAD:
                        result = provider.getWailaHead(icon, lines, target, BRIDGE);
                        break;
                    case BODY:
                        result = provider.getWailaBody(icon, lines, target, BRIDGE);
                        break;
                    case TAIL:
                        result = provider.getWailaTail(icon, lines, target, BRIDGE);
                        break;
                    default:
                        throw new AssertionError(part);
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
        return age >= stages
                ? "\u00a7a" + StatCollector.translateToLocal("iaspis.growth.mature")
                : StatCollector.translateToLocalFormatted(
                        "iaspis.growth", Math.round(age * 100.0F / stages));
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
        EntityRegistry.EntityRegistration registration =
                EntityRegistry.instance().lookupModSpawn(entity.getClass(), true);
        if (!lite) VanillaEntityDetails.append(target, entity, this, body);
        String name = VanillaEntityDetails.name(entity, icon);
        lines.addAll(entitySection(Section.HEAD, target, entity, section(name)));
        hasHead = !lines.isEmpty();
        if (!lite) lines.addAll(entitySection(Section.BODY, target, entity, body));
        bodyEnd = lines.size();
        lines.addAll(
                entitySection(
                        Section.TAIL,
                        target,
                        entity,
                        source(
                                registration == null ? null : registration.getContainer(),
                                entity.getClass())));
    }

    private List<String> entitySection(
            Section part, Target target, Entity entity, List<String> lines) {
        for (IWailaEntityProvider provider : BRIDGE.entities(part, target)) {
            try {
                List<String> result;
                switch (part) {
                    case HEAD:
                        result = provider.getWailaHead(entity, lines, target, BRIDGE);
                        break;
                    case BODY:
                        result = provider.getWailaBody(entity, lines, target, BRIDGE);
                        break;
                    case TAIL:
                        result = provider.getWailaTail(entity, lines, target, BRIDGE);
                        break;
                    default:
                        throw new AssertionError(part);
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
