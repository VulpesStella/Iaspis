package io.github.vulpesstella.iaspis.client;

import org.lwjgl.opengl.GL11;

final class OverlayFade {
    private static final long DURATION = 85000000L;
    private long updatedAt;
    private long lastTargetAt;
    private float opacity;
    private int texture;
    private int textureWidth;
    private int textureHeight;
    private int x;
    private int y;
    private int width;
    private int height;

    float update(long now, boolean hasTarget, int lingerMillis, boolean animate) {
        if (hasTarget) lastTargetAt = now;
        long elapsed = updatedAt == 0 ? 0 : now - updatedAt;
        updatedAt = now;
        long lingerEnd = lastTargetAt + lingerMillis * 1000000L;
        if (!animate) {
            opacity = hasTarget || now <= lingerEnd ? 1 : 0;
            return opacity;
        }
        if (now <= lingerEnd) {
            opacity = Math.min(1, opacity + elapsed / (float) DURATION);
        } else {
            opacity = Math.max(0, opacity - Math.min(elapsed, now - lingerEnd) / (float) DURATION);
        }
        return opacity;
    }

    void reset() {
        updatedAt = 0;
        lastTargetAt = 0;
        opacity = 0;
    }

    boolean capture(int left, int top, int right, int bottom, int displayWidth, int displayHeight) {
        x = Math.max(0, left);
        int upper = Math.max(0, top);
        width = Math.min(displayWidth, right) - x;
        height = Math.min(displayHeight, bottom) - upper;
        y = displayHeight - upper - height;
        if (width <= 0 || height <= 0) return false;

        if (texture == 0) {
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        } else {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }
        if (width > textureWidth || height > textureHeight) {
            textureWidth = Math.max(textureWidth, nextPowerOfTwo(width));
            textureHeight = Math.max(textureHeight, nextPowerOfTwo(height));
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, textureWidth, textureHeight, 0,
                    GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        }
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, width, height);
        return true;
    }

    void composite(float opacity, int pixelScale, int displayHeight) {
        float left = x / (float) pixelScale;
        float top = (displayHeight - y - height) / (float) pixelScale;
        float right = left + width / (float) pixelScale;
        float bottom = top + height / (float) pixelScale;
        float u = width / (float) textureWidth;
        float v = height / (float) textureHeight;
        // Blend the captured background over the result so item models fade with the rest of the HUD.
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1, 1, 1, 1 - opacity);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(left, bottom);
        GL11.glTexCoord2f(u, 0);
        GL11.glVertex2f(right, bottom);
        GL11.glTexCoord2f(u, v);
        GL11.glVertex2f(right, top);
        GL11.glTexCoord2f(0, v);
        GL11.glVertex2f(left, top);
        GL11.glEnd();
    }

    private static int nextPowerOfTwo(int value) {
        return value <= 1 ? 1 : Integer.highestOneBit(value - 1) << 1;
    }
}
