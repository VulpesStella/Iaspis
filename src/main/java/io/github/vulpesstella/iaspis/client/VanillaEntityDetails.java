package io.github.vulpesstella.iaspis.client;

import io.github.vulpesstella.iaspis.compat.EntityHudData;
import io.github.vulpesstella.iaspis.compat.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.StatCollector;

final class VanillaEntityDetails {
    private static final String[] PROFESSIONS = {
        "farmer", "librarian", "priest", "smith", "butcher"
    };

    static void append(Target target, Entity entity, Tooltip tooltip, List<String> lines) {
        NBTTagCompound data = entity == target.getEntity() ? target.getRemoteData() : null;
        if (entity instanceof EntityAnimal && PreviewSample.ANIMAL.enabled()) {
            int age = ((EntityAnimal) entity).getGrowingAge();
            if (age < 0) lines.add(timed("iaspis.entity.growth", -(long) age));
            else if (age > 0) lines.add(timed("iaspis.entity.cooldown", age));
            else if (data != null && data.getBoolean(EntityHudData.BREEDING)) {
                lines.add(StatCollector.translateToLocal("iaspis.entity.breeding"));
            }
        }
        if (entity instanceof EntityHorse) {
            EntityHorse horse = (EntityHorse) entity;
            if (PreviewSample.HORSE.enabled()) {
                double speed =
                        horse.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
                                .getBaseValue();
                appendHorse(lines, speed, horse.getHorseJumpStrength());
            }
            if (PreviewSample.OWNER.enabled()) appendOwner(lines, horse.getOwnerName());
        } else if (entity instanceof EntityTameable && PreviewSample.OWNER.enabled()) {
            appendOwner(lines, ((EntityTameable) entity).getOwnerName());
        }
        if (entity instanceof EntityVillager && PreviewSample.VILLAGER.enabled()) {
            lines.add(profession(((EntityVillager) entity).getProfession()));
        }
        if (entity instanceof EntityLivingBase && PreviewSample.EFFECTS.enabled()) {
            tooltip.effects = effectLines(effects((EntityLivingBase) entity, data));
        }
        if (entity instanceof EntityItemFrame && PreviewSample.ITEM_FRAME.enabled()) {
            tooltip.icon = new ItemStack(Item.itemFrame);
            setDisplayedItem(tooltip, ((EntityItemFrame) entity).getDisplayedItem());
        }
        if (entity instanceof EntityItem && PreviewSample.DROPPED_ITEM.enabled()) {
            ItemStack stack = ((EntityItem) entity).getEntityItem();
            if (stack != null && stack.getItem() != null) {
                tooltip.icon = stack;
                lines.add(
                        StatCollector.translateToLocalFormatted(
                                "iaspis.entity.count", stack.stackSize));
                if (data != null && data.hasKey(EntityHudData.DESPAWN)) {
                    lines.add(
                            timed("iaspis.entity.despawn", data.getInteger(EntityHudData.DESPAWN)));
                }
            }
        }
    }

    static void setDisplayedItem(Tooltip tooltip, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return;
        tooltip.detailItem = stack;
        tooltip.detailLabel = stack.getDisplayName();
    }

    static String name(Entity entity, ItemStack icon) {
        if (entity instanceof EntityItem && icon != null) return icon.getDisplayName();
        String name = entity.getTranslatedEntityName();
        if (entity instanceof EntityItemFrame && "entity.ItemFrame.name".equals(name)) {
            return new ItemStack(Item.itemFrame).getDisplayName();
        }
        return name;
    }

    static void appendOwner(List<String> lines, String owner) {
        if (owner != null && !owner.isEmpty()) {
            lines.add(StatCollector.translateToLocalFormatted("iaspis.entity.owner", owner));
        }
    }

    static void appendHorse(List<String> lines, double speed, double jump) {
        // Estimate a sustained ride on a normal block from ground drag and forward input.
        double blocksPerSecond = speed * 0.98 * 20 / (1 - 0.6 * 0.91);
        double height = 0;
        for (int tick = 0; tick < 80 && jump > 0; tick++) {
            height += jump;
            jump = (jump - 0.08) * 0.98;
        }
        lines.add(
                StatCollector.translateToLocalFormatted(
                        "iaspis.entity.speed", String.format("%.2f", blocksPerSecond)));
        lines.add(
                StatCollector.translateToLocalFormatted(
                        "iaspis.entity.jump", String.format("%.2f", height)));
    }

    static String profession(int id) {
        String label =
                id >= 0 && id < PROFESSIONS.length
                        ? StatCollector.translateToLocal(
                                "iaspis.entity.profession." + PROFESSIONS[id])
                        : Integer.toString(id);
        return StatCollector.translateToLocalFormatted("iaspis.entity.profession", label);
    }

    static String timed(String key, long ticks) {
        long secs = (Math.max(0, ticks) + 19) / 20;
        return StatCollector.translateToLocalFormatted(
                key, String.format("%d:%02d", secs / 60, secs % 60));
    }

    private static List<PotionEffect> effects(EntityLivingBase entity, NBTTagCompound data) {
        List<PotionEffect> effects = new ArrayList<PotionEffect>();
        if (data == null || !data.hasKey(EntityHudData.EFFECTS)) {
            for (Object effect : entity.getActivePotionEffects())
                effects.add((PotionEffect) effect);
            return effects;
        }
        NBTTagList tags = data.getTagList(EntityHudData.EFFECTS);
        for (int i = 0; i < Math.min(tags.tagCount(), 32); i++) {
            NBTBase tag = tags.tagAt(i);
            if (tag instanceof NBTTagCompound) {
                PotionEffect effect =
                        PotionEffect.readCustomPotionEffectFromNBT((NBTTagCompound) tag);
                if (effect != null) effects.add(effect);
            }
        }
        return effects;
    }

    static List<EffectLine> effectLines(List<PotionEffect> effects) {
        List<EffectLine> lines = new ArrayList<EffectLine>();
        int limit = HudSettings.NumberOption.EFFECT_LIMIT.get();
        if (limit == 0) return lines;
        Collections.sort(
                effects,
                new Comparator<PotionEffect>() {
                    @Override
                    public int compare(PotionEffect a, PotionEffect b) {
                        return Integer.compare(a.getPotionID(), b.getPotionID());
                    }
                });
        for (PotionEffect effect : effects) {
            int id = effect.getPotionID();
            if (id < 0
                    || id >= Potion.potionTypes.length
                    || Potion.potionTypes[id] == null
                    || (effect.getDuration() <= 0 && !effect.getIsPotionDurationMax())) continue;
            Potion potion = Potion.potionTypes[id];
            String name = StatCollector.translateToLocal(potion.getName());
            if (effect.getAmplifier() > 0) {
                int level = effect.getAmplifier() + 1;
                String key = "enchantment.level." + level;
                name +=
                        " "
                                + (StatCollector.func_94522_b(key)
                                        ? StatCollector.translateToLocal(key)
                                        : level);
            }
            String label =
                    StatCollector.translateToLocalFormatted(
                            "iaspis.entity.effect", name, Potion.getDurationString(effect));
            lines.add(
                    new EffectLine(
                            potion.hasStatusIcon() ? potion.getStatusIconIndex() : -1, label));
            if (lines.size() == limit) break;
        }
        return lines;
    }

    static final class EffectLine {
        final int icon;
        final String label;

        EffectLine(int icon, String label) {
            this.icon = icon;
            this.label = label;
        }
    }

    private VanillaEntityDetails() {}
}
