package io.github.vulpesstella.iaspis.api;

import io.github.vulpesstella.iaspis.compat.MachinePreview;

/** Optional API 1 target with client-projected machine data. */
public interface MachineTarget extends HudTarget {
    MachinePreview getMachinePreview();

    String getMachineProviderId();
}
