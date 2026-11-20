package io.github.vulpesstella.iaspis.client;

import io.github.vulpesstella.iaspis.compat.DetailRegistry;
import io.github.vulpesstella.iaspis.compat.DetailsChannel;
import io.github.vulpesstella.iaspis.compat.MachinePreview;
import io.github.vulpesstella.iaspis.compat.MachineProviders;
import io.github.vulpesstella.iaspis.compat.Target;
import io.github.vulpesstella.iaspis.compat.WailaBridge;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetworkManager;

/** Connection-bound API 1 snapshots. A late response never changes a newer selection. */
final class RemoteDetails {
    private INetworkManager connection;
    private Target context;
    private int sequence;
    private long nextRequest, dataTime;
    private long sentAt;
    private boolean pending;
    private boolean detailed;
    private List<String> ids = Collections.emptyList();
    private String machineId;
    private NBTTagCompound data;
    private MachinePreview machine = MachinePreview.EMPTY;

    synchronized void apply(Target target, boolean request, boolean details) {
        Minecraft game = Minecraft.getMinecraft();
        INetworkManager current =
                game.theWorld == null || game.thePlayer == null || game.getNetHandler() == null
                        ? null
                        : game.getNetHandler().getNetManager();
        if (connection != current) {
            connection = current;
            reset();
            nextRequest = 0;
        }
        if (current == null
                || target.getEntity() != null
                || target.getTileEntity() == null
                || !DetailsChannel.available(target.getPlayer())) {
            if (request) reset();
            return;
        }
        List<String> wanted = DetailRegistry.ids(target, details);
        if (wanted.size() > DetailsChannel.MAX_SOURCES)
            wanted = wanted.subList(0, DetailsChannel.MAX_SOURCES);
        MachineProviders.Source source = MachineProviders.source(target.getTileEntity());
        String wantedMachine =
                MachineProviders.visible(source, details, false)
                        ? source.id == null ? "" : source.id
                        : null;
        boolean same =
                RemoteTargetData.matches(target, context)
                        && detailed == details
                        && ids.equals(wanted)
                        && equal(machineId, wantedMachine);
        if (request && !same) {
            reset();
            context = target;
            detailed = details;
            ids = wanted;
            machineId = wantedMachine;
        }
        long now = System.nanoTime();
        if (same && data != null && now - dataTime < 1000000000L) {
            target.setSnapshots(data);
            if (wantedMachine != null)
                target.setMachinePreview(wantedMachine.isEmpty() ? null : wantedMachine, machine);
        }
        if (!request
                || (wanted.isEmpty() && wantedMachine == null)
                || now - nextRequest < 0
                || (pending && now - sentAt < 1000000000L)) return;
        nextRequest = now + 250000000L;
        pending = true;
        sentAt = now;
        DetailsChannel.request(target, ++sequence, details, wanted, wantedMachine);
    }

    synchronized void receive(INetworkManager manager, int response, NBTTagCompound tag) {
        Minecraft game = Minecraft.getMinecraft();
        if (manager != connection
                || context == null
                || game.theWorld != context.getWorld()
                || game.thePlayer != context.getPlayer()
                || game.getNetHandler() == null
                || game.getNetHandler().getNetManager() != manager
                || response != sequence
                || !pending) return;
        NBTTagCompound snapshots = new NBTTagCompound();
        NBTTagCompound received = tag.getCompoundTag("details");
        for (String id : ids)
            if (received.hasKey(id)) snapshots.setTag(id, received.getCompoundTag(id).copy());
        MachinePreview preview = MachinePreview.EMPTY;
        if (machineId != null && tag.hasKey("machine")) {
            NBTTagCompound value = tag.getCompoundTag("machine");
            if (machineId.equals(value.getString("id")))
                try {
                    preview = DetailsChannel.decodeMachine(value);
                } catch (IOException e) {
                    WailaBridge.INSTANCE.report("Invalid machine details", e);
                }
        }
        data = snapshots;
        machine = preview;
        dataTime = System.nanoTime();
        pending = false;
    }

    private void reset() {
        sequence++;
        context = null;
        data = null;
        machine = MachinePreview.EMPTY;
        ids = Collections.emptyList();
        machineId = null;
        pending = false;
    }

    private static boolean equal(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
