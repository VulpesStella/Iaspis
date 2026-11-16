package io.github.vulpesstella.iaspis.compat;

import io.github.vulpesstella.iaspis.Iaspis;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;

public final class EntityHudData {
    public static final String BREEDING = "IaspisBreeding";
    public static final String DESPAWN = "IaspisDespawn";
    public static final String EFFECTS = "IaspisEffects";
    private static final int MAX_EFFECTS = 32;

    public static boolean needsSync(Entity entity) {
        return breedingEnabled(entity) || despawnEnabled(entity) || effectsEnabled(entity);
    }

    static void append(Entity entity, NBTTagCompound data) {
        if (breedingEnabled(entity)) data.setBoolean(BREEDING, ((EntityAnimal) entity).isInLove());
        if (despawnEnabled(entity)) {
            EntityItem item = (EntityItem) entity;
            long remaining = (long) item.lifespan - item.age;
            data.setInteger(DESPAWN, (int) Math.max(0, Math.min(Integer.MAX_VALUE, remaining)));
        }
        if (effectsEnabled(entity)) {
            NBTTagList effects = new NBTTagList();
            for (Object entry : ((EntityLivingBase) entity).getActivePotionEffects()) {
                if (effects.tagCount() == MAX_EFFECTS) break;
                effects.appendTag(((PotionEffect) entry).writeCustomPotionEffectToNBT(new NBTTagCompound()));
            }
            data.setTag(EFFECTS, effects);
        }
    }

    private static boolean breedingEnabled(Entity entity) {
        return entity instanceof EntityAnimal && Iaspis.config().getBoolean("vanilla.animal");
    }

    private static boolean despawnEnabled(Entity entity) {
        return entity instanceof EntityItem && Iaspis.config().getBoolean("vanilla.dropped");
    }

    private static boolean effectsEnabled(Entity entity) {
        return entity instanceof EntityLivingBase && Iaspis.config().getBoolean("vanilla.effects");
    }

    private EntityHudData() {}
}
