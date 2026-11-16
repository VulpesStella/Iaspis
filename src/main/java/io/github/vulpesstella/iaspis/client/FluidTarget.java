package io.github.vulpesstella.iaspis.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFluid;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidBlock;

final class FluidTarget {
    static final int OFF = 0;
    static final int SOURCE = 1;
    static final int ALL = 2;

    static Fluid fluid(Block block) {
        if (block == Block.waterMoving || block == Block.waterStill) return FluidRegistry.WATER;
        if (block == Block.lavaMoving || block == Block.lavaStill) return FluidRegistry.LAVA;
        if (block instanceof IFluidBlock) return ((IFluidBlock) block).getFluid();
        return block == null ? null : FluidRegistry.lookupFluidForBlock(block);
    }

    // Trace voxel boundaries up to the normal mouse hit so fluids cannot hide nearer solid targets.
    static MovingObjectPosition trace(World world, Vec3 start, Vec3 look, double limit, int mode) {
        int x = MathHelper.floor_double(start.xCoord);
        int y = MathHelper.floor_double(start.yCoord);
        int z = MathHelper.floor_double(start.zCoord);
        int stepX = look.xCoord < 0 ? -1 : 1;
        int stepY = look.yCoord < 0 ? -1 : 1;
        int stepZ = look.zCoord < 0 ? -1 : 1;
        double dx = Math.abs(1 / look.xCoord);
        double dy = Math.abs(1 / look.yCoord);
        double dz = Math.abs(1 / look.zCoord);
        double tx = boundary(start.xCoord, x, look.xCoord);
        double ty = boundary(start.yCoord, y, look.yCoord);
        double tz = boundary(start.zCoord, z, look.zCoord);
        Vec3 end = start.addVector(look.xCoord * limit, look.yCoord * limit, look.zCoord * limit);
        while (true) {
            double distance;
            if (tx <= ty && tx <= tz) {
                distance = tx;
                tx += dx;
                x += stepX;
            } else if (ty <= tz) {
                distance = ty;
                ty += dy;
                y += stepY;
            } else {
                distance = tz;
                tz += dz;
                z += stepZ;
            }
            if (distance >= limit) return null;
            if (y < 0 || y >= world.getHeight()) continue;
            if (!world.blockExists(x, y, z)) return null;
            Block block = Block.blocksList[world.getBlockId(x, y, z)];
            Fluid fluid = fluid(block);
            if (fluid == null) continue;
            if (mode == SOURCE && !isSource(world, block, x, y, z)) continue;
            double fill = filledHeight(world, block, fluid, x, y, z);
            double height = Math.min(1, Math.abs(fill));
            if (!(height > 0)) continue;
            AxisAlignedBB volume = AxisAlignedBB.getBoundingBox(x, fill < 0 ? y + 1 - height : y, z,
                    x + 1, fill < 0 ? y + 1 : y + height, z + 1);
            MovingObjectPosition hit = volume.calculateIntercept(start, end);
            if (hit != null && start.distanceTo(hit.hitVec) < limit) {
                return new MovingObjectPosition(x, y, z, hit.sideHit, hit.hitVec);
            }
        }
    }

    private static double boundary(double start, int cell, double direction) {
        if (direction == 0) return Double.POSITIVE_INFINITY;
        return (cell + (direction > 0 ? 1 : 0) - start) / direction;
    }

    private static boolean isSource(World world, Block block, int x, int y, int z) {
        if (block instanceof IFluidBlock) return ((IFluidBlock) block).canDrain(world, x, y, z);
        return world.getBlockMetadata(x, y, z) == 0;
    }

    private static double filledHeight(World world, Block block, Fluid fluid, int x, int y, int z) {
        double fill = 1;
        if (block instanceof IFluidBlock) fill = ((IFluidBlock) block).getFilledPercentage(world, x, y, z);
        else if (block instanceof BlockFluid) {
            int level = world.getBlockMetadata(x, y, z);
            fill = level >= 8 ? 1 : 1 - BlockFluid.getFluidHeightPercent(level);
        }
        if (fill != 0 && Math.abs(fill) < 1) {
            Block adjacent = Block.blocksList[world.getBlockId(x, y + (fill > 0 ? 1 : -1), z)];
            if (fluid(adjacent) == fluid) return Math.copySign(1, fill);
        }
        return fill;
    }

    private FluidTarget() {}
}
