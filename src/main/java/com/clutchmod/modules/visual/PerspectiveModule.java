package com.clutchmod.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

/**
 * Perspective Module — 1.8.9
 * 
 * Alters the player's camera perspective for better view angles.
 */
public class PerspectiveModule {

    private boolean enabled = false;
    public boolean isEnabled()           { return enabled; }
    public void    setEnabled(boolean v) { enabled = v; }
    public void    toggle()              { enabled = !enabled; }

    // Camera distance (how far behind the player)
    private float cameraDistance = 4.0f;
    public float getCameraDistance()       { return cameraDistance; }
    public void  setCameraDistance(float v) { cameraDistance = Math.max(0.1f, Math.min(10.0f, v)); }

    // Camera height offset
    private float heightOffset = 0.0f;
    public float getHeightOffset()       { return heightOffset; }
    public void  setHeightOffset(float v) { heightOffset = Math.max(-3.0f, Math.min(3.0f, v)); }

    // Enable/disable perspective smoothing
    private boolean smoothCamera = true;
    public boolean isSmoothCamera()           { return smoothCamera; }
    public void    setSmoothCamera(boolean v) { smoothCamera = v; }

    // Third person side offset
    private float sideOffset = 0.0f;
    public float getSideOffset()       { return sideOffset; }
    public void  setSideOffset(float v) { sideOffset = Math.max(-5.0f, Math.min(5.0f, v)); }

    public void onTick() {
        if (!enabled) return;
        // Camera modifications happen via render event
    }

    public void renderOverlay(Minecraft mc, ScaledResolution sr, FontRenderer fr) {
        if (!enabled) return;

        int x = 5;
        int y = sr.getScaledHeight() - 40;
        fr.drawStringWithShadow("Perspective enabled", x, y, 0xFF55AAFF);
        fr.drawStringWithShadow("Distance: " + cameraDistance + "  Offset: " + heightOffset, x, y + 10, 0xFF55AAFF);
        fr.drawStringWithShadow("Smooth: " + smoothCamera + "  Side: " + sideOffset, x, y + 20, 0xFF55AAFF);
    }
}
