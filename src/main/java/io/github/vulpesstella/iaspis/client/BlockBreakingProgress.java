package io.github.vulpesstella.iaspis.client;

import cpw.mods.fml.relauncher.ReflectionHelper;
import io.github.vulpesstella.iaspis.compat.Target;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import java.lang.reflect.Field;
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.MovingObjectPosition;

final class BlockBreakingProgress {
    final float fraction;
    final boolean canHarvest;

    private BlockBreakingProgress(float fraction, boolean canHarvest) {
        this.fraction = fraction;
        this.canHarvest = canHarvest;
    }

    static BlockBreakingProgress read(
            PlayerControllerMP controller, Target target, float partialTicks) {
        if (controller == null
                || target == null
                || target.getBlock() == null
                || controller.isInCreativeMode()
                || !PreviewSample.BREAKING.enabled()) return null;
        MiningFields fields = FieldLookup.FIELDS;
        if (fields == null) return null;
        MovingObjectPosition hit = target.getPosition();
        try {
            if (!fields.active.getBoolean(controller)
                    || fields.x.getInt(controller) != hit.blockX
                    || fields.y.getInt(controller) != hit.blockY
                    || fields.z.getInt(controller) != hit.blockZ) return null;
            float damage = fields.damage.getFloat(controller);
            if (!(damage > 0 && damage < 1)) return null;

            Block block = target.getBlock();
            float perTick =
                    block.getPlayerRelativeBlockHardness(
                            target.getPlayer(),
                            target.getWorld(),
                            hit.blockX,
                            hit.blockY,
                            hit.blockZ);
            if (perTick > 0 && perTick < 1) {
                damage += perTick * Math.max(0, Math.min(1, partialTicks));
            }
            return new BlockBreakingProgress(Math.min(1, damage), HarvestHint.canHarvest(target));
        } catch (IllegalAccessException e) {
            WailaBridge.INSTANCE.report("Could not read block breaking progress", e);
            return null;
        } catch (RuntimeException e) {
            WailaBridge.INSTANCE.report("Could not calculate block breaking progress", e);
            return null;
        }
    }

    static BlockBreakingProgress preview(long millis) {
        long cycle = millis % 4400;
        if (cycle % 2200 >= 1550) return null;
        return new BlockBreakingProgress(Math.min(1, (cycle % 2200) / 1450.0F), cycle < 2200);
    }

    private static final class MiningFields {
        final Field active = field("isHittingBlock", "field_78778_j");
        final Field x = field("currentBlockX", "field_78775_c");
        final Field y = field("currentBlockY", "field_78772_d");
        final Field z = field("currentblockZ", "field_78773_e");
        final Field damage = field("curBlockDamageMP", "field_78770_f");

        private static Field field(String mcp, String srg) {
            return ReflectionHelper.findField(PlayerControllerMP.class, mcp, srg);
        }
    }

    private static final class FieldLookup {
        static final MiningFields FIELDS = load();

        private static MiningFields load() {
            try {
                return new MiningFields();
            } catch (RuntimeException e) {
                WailaBridge.INSTANCE.report("Block breaking progress unavailable", e);
                return null;
            }
        }
    }
}
