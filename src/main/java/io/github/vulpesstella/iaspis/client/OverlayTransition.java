package io.github.vulpesstella.iaspis.client;

final class OverlayTransition {
    private static final long DURATION_NANOS = 75000000L;

    private Bounds from;
    private Bounds to;
    private long startedAt;

    Bounds update(Bounds target, long now, boolean animate) {
        if (!animate || to == null) {
            from = target;
            to = target;
            return target;
        }
        if (!to.sameSizeAndPosition(target)) {
            from = current(now);
            to = target;
            startedAt = now;
        }
        return current(now);
    }

    void reset() {
        from = null;
        to = null;
    }

    private Bounds current(long now) {
        long elapsed = now - startedAt;
        if (from == to || elapsed >= DURATION_NANOS) return to;
        float progress = elapsed / (float) DURATION_NANOS;
        return new Bounds(
                interpolate(from.left, to.left, progress),
                interpolate(from.width, to.width, progress),
                interpolate(from.height, to.height, progress));
    }

    private static int interpolate(int start, int end, float progress) {
        return Math.round(start + (end - start) * progress);
    }

    static final class Bounds {
        final int left;
        final int width;
        final int height;

        Bounds(int left, int width, int height) {
            this.left = left;
            this.width = width;
            this.height = height;
        }

        boolean sameSizeAndPosition(Bounds other) {
            return left == other.left && width == other.width && height == other.height;
        }
    }
}
