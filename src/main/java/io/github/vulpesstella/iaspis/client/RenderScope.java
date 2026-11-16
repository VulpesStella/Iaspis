package io.github.vulpesstella.iaspis.client;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Saves the caller's fixed-function state and selects the model-view stack. */
final class RenderScope implements AutoCloseable {
    private static final int MATRIX_SIZE = 16;
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(MATRIX_SIZE);
    private final int activeTexture;
    private final int clientTexture;
    private final int matrixMode;
    private final float[] matrices = new float[MATRIX_SIZE * 3];

    RenderScope() {
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        clientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT | GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        saveMatrix(GL11.GL_PROJECTION_MATRIX, 0);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        saveMatrix(GL11.GL_TEXTURE_MATRIX, MATRIX_SIZE);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        saveMatrix(GL11.GL_TEXTURE_MATRIX, MATRIX_SIZE * 2);
        OpenGlHelper.setActiveTexture(activeTexture);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
    }

    static void hud() {
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glDepthMask(false);
        GL11.glColor4f(1, 1, 1, 1);
    }

    static void clip(int x, int y, int width, int height, IntBuffer scratch) {
        int right = x + width;
        int top = y + height;
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            scratch.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, scratch);
            x = Math.max(x, scratch.get(0));
            y = Math.max(y, scratch.get(1));
            right = Math.min(right, scratch.get(0) + scratch.get(2));
            top = Math.min(top, scratch.get(1) + scratch.get(3));
        }
        GL11.glScissor(x, y, Math.max(0, right - x), Math.max(0, top - y));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }

    private void saveMatrix(int matrix, int offset) {
        MATRIX.clear();
        GL11.glGetFloat(matrix, MATRIX);
        MATRIX.get(matrices, offset, MATRIX_SIZE);
    }

    private void restoreMatrix(int mode, int offset) {
        MATRIX.clear();
        MATRIX.put(matrices, offset, MATRIX_SIZE).flip();
        GL11.glMatrixMode(mode);
        GL11.glLoadMatrix(MATRIX);
    }

    @Override
    public void close() {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        restoreMatrix(GL11.GL_PROJECTION, 0);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        restoreMatrix(GL11.GL_TEXTURE, MATRIX_SIZE);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        restoreMatrix(GL11.GL_TEXTURE, MATRIX_SIZE * 2);
        GL11.glPopClientAttrib();
        GL11.glPopAttrib();
        OpenGlHelper.setActiveTexture(activeTexture);
        OpenGlHelper.setClientActiveTexture(clientTexture);
        GL11.glMatrixMode(matrixMode);
    }
}
