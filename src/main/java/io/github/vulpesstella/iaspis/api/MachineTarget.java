package io.github.vulpesstella.iaspis.api;

import io.github.vulpesstella.iaspis.compat.MachinePreview;

/** Optional target with client-projected machine data. */
public interface MachineTarget extends HudTarget {
    MachinePreview getMachinePreview();

    String getMachineProviderId();
}
