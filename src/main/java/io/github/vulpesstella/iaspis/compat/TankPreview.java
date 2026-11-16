package io.github.vulpesstella.iaspis.compat;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

public final class TankPreview {
    private static final int MARKER = -1;
    private static final int LIMIT = 8;

    public final Fluid fluid;
    public final int amount;
    public final int capacity;

    public TankPreview(Fluid fluid, int amount, int capacity) {
        this.fluid = fluid;
        this.amount = amount;
        this.capacity = capacity;
    }

    static List<TankPreview> collect(IFluidHandler handler) {
        List<TankPreview> tanks = new ArrayList<TankPreview>();
        add(tanks, handler.getTankInfo(ForgeDirection.UNKNOWN), 0);
        if (tanks.isEmpty()) {
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                add(tanks, handler.getTankInfo(side), tanks.size());
            }
        }
        return Collections.unmodifiableList(tanks);
    }

    private static void add(List<TankPreview> tanks, FluidTankInfo[] infos, int previous) {
        if (infos == null) return;
        // Keep identical tanks reported together, but collapse repeated views from other faces.
        for (FluidTankInfo info : infos) {
            if (tanks.size() == LIMIT) return;
            if (info == null || info.capacity <= 0) continue;
            int amount = info.fluid == null ? 0 : Math.max(0, info.fluid.amount);
            Fluid fluid = amount == 0 ? null : info.fluid.getFluid();
            int i = 0;
            for (; i < previous; i++) {
                TankPreview tank = tanks.get(i);
                if (tank.fluid == fluid && tank.amount == amount && tank.capacity == info.capacity) break;
            }
            if (i == previous) tanks.add(new TankPreview(fluid, amount, info.capacity));
        }
    }

    static void write(DataOutput out, List<TankPreview> tanks) throws IOException {
        if (tanks.isEmpty()) return;
        out.writeInt(MARKER);
        out.writeByte(tanks.size());
        for (TankPreview tank : tanks) {
            out.writeInt(tank.fluid == null ? 0 : tank.fluid.getID());
            out.writeInt(tank.amount);
            out.writeInt(tank.capacity);
        }
    }

    static List<TankPreview> read(DataInputStream in) throws IOException {
        if (in.available() == 0) return Collections.emptyList();
        in.mark(4);
        if (in.readInt() != MARKER) {
            in.reset();
            return Collections.emptyList();
        }
        int size = in.readUnsignedByte();
        if (size > LIMIT || in.available() < size * 12) throw new IOException("Invalid tank preview size");
        List<TankPreview> tanks = new ArrayList<TankPreview>(size);
        for (int i = 0; i < size; i++) {
            int id = in.readInt();
            int amount = in.readInt();
            int capacity = in.readInt();
            if (amount < 0 || capacity <= 0) throw new IOException("Invalid tank quantities");
            tanks.add(new TankPreview(id == 0 ? null : FluidRegistry.getFluid(id), amount, capacity));
        }
        return Collections.unmodifiableList(tanks);
    }
}
