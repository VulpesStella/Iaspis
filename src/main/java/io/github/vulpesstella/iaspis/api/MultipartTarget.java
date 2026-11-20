package io.github.vulpesstella.iaspis.api;

/** Optional target interface, available since extension API 1. */
public interface MultipartTarget extends HudTarget {
    /** Returns the uniquely hit part, or null for an unsupported or invalid selection. */
    MultipartPart getMultipartPart();
}
