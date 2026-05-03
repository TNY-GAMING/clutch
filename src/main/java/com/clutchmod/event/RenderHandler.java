package com.clutchmod.event;

import com.clutchmod.ModState;
import com.clutchmod.modules.movement.ClutchModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Overlay renderer for ClutchMod.
 *
 * Handles:
 *   • Block-count HUD (Clutch module)
 *   • Silent Aim indicator (2D line + dot showing server aim direction)
 *   • Delegated overlays for Perspective and PlayerESP modules
 *
 * THREAD SAFETY:
 *   All rendering runs on the render thread. SilentAimModule.getServerYaw/Pitch
 *   reads AtomicReference — safe without synchronization.
 */
public class RenderHandler {

    // Aim indicator colours (ARGB)
    private static final int AIM_LINE_COLOUR   = 0xFF4A90D9; // blue
    private static final int AIM_DOT_COLOUR      = 0xFFFFFFFF; // white
    private static final float AIM_LINE_WIDTH    = 2.0f;
    private static final float AIM_DOT_RADIUS   = 3.0f;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        // ─── Null guard on ModState.CLUTCH ─────────────────────────────────────
        // If ModState.initModules() failed (e.g. exception in constructor),
        // CLUTCH can be null. The original code called clutch.isEnabled()
        // unconditionally, causing NPE and crash-to-desktop.
        ClutchModule clutch = ModState.CLUTCH;
        if (clutch != null) {
            renderBlockCount(mc, clutch);
        }

        // ─── Silent Aim Indicator ────────────────────────────────────────────
        if (ModState.SILENT_AIM != null
            && ModState.SILENT_AIM.isEnabled()
            && ModState.SILENT_AIM.isAimIndicator()
            && ModState.SILENT_AIM.hasOverride()) {
            renderAimIndicator(mc);
        }

        // ─── Delegated module overlays ──────────────────────────────────────
        if (ModState.PERSPECTIVE != null) ModState.PERSPECTIVE.renderOverlay(mc, event.resolution, mc.fontRendererObj);
        if (ModState.PLAYER_ESP != null) ModState.PLAYER_ESP.renderOverlay(mc, event.resolution, mc.fontRendererObj);
    }

    private void renderBlockCount(Minecraft mc, ClutchModule clutch) {
        if (!clutch.isEnabled() || !clutch.isShowBlockCount()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        FontRenderer fr = mc.fontRendererObj;
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();

        int available = clutch.availableBlocks();
        String text = "Blocks: " + available;

        // Draw just right of the crosshair
        int x = sw / 2 + 10;
        int y = sh / 2 + 2;

        int color = clutch.isClutching() ? 0xFF5555 : 0xAAAAAA;
        fr.drawStringWithShadow(text, x, y, color);
    }

    /**
     * Renders a Silent Aim indicator as a 2D line + dot at the screen-space
     * projection of the server aim target.
     *
     * PROJECTION MATH (1.8.9 Minecraft convention):
     *   Minecraft yaw: 0° = south (+Z), increases clockwise.
     *   Minecraft pitch: 0° = horizontal, 90° = down (-Y), -90° = up (+Y).
     *
     *   We construct a LEFT-HANDED view space where:
     *     +X = camera right
     *     +Y = camera up
     *     +Z = camera forward (into the screen)
     *
     *   This matches Minecraft's internal coordinate system and avoids the
     *   sign confusion of trying to emulate OpenGL's right-handed convention.
     *
     *   Camera basis vectors (world space):
     *     forward = (-sin(yaw)*cos(pitch), -sin(pitch), cos(yaw)*cos(pitch))
     *     right   = forward × worldUp = (-forwardZ, 0, forwardX)
     *     up      = right × forward
     *
     *   View space transform:
     *     viewX = dot(world - cameraPos, right)
     *     viewY = dot(world - cameraPos, up)
     *     viewZ = dot(world - cameraPos, forward)  // positive = in front
     *
     *   Perspective projection (left-handed, +Z forward):
     *     screenX = viewX / viewZ * f + displayWidth/2
     *     screenY = -viewY / viewZ * f + displayHeight/2
     *     where f = (displayWidth/2) / tan(fov/2)
     *
     *   Points with viewZ <= 0 are behind the camera or at the camera plane.
     */
    private void renderAimIndicator(Minecraft mc) {
        float serverYaw   = ModState.SILENT_AIM.getServerYaw();
        float serverPitch = ModState.SILENT_AIM.getServerPitch();

        // Build aim target point 10 blocks along the aim vector
        double yawRad   = Math.toRadians(serverYaw);
        double pitchRad = Math.toRadians(serverPitch);

        // Minecraft forward vector from yaw/pitch
        double aimForwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double aimForwardY = -Math.sin(pitchRad);
        double aimForwardZ =  Math.cos(yawRad) * Math.cos(pitchRad);

        Vec3 cameraPos = new Vec3(
            mc.getRenderViewEntity().posX,
            mc.getRenderViewEntity().posY + mc.getRenderViewEntity().getEyeHeight(),
            mc.getRenderViewEntity().posZ
        );
        Vec3 targetWorld = cameraPos.addVector(aimForwardX * 10.0, aimForwardY * 10.0, aimForwardZ * 10.0);

        float[] screenPos = projectToScreen(targetWorld, mc);
        if (screenPos == null) return; // behind camera

        ScaledResolution sr = new ScaledResolution(mc);
        float screenX = screenPos[0];
        float screenY = screenPos[1];
        float centerX = sr.getScaledWidth() / 2.0f;
        float centerY = sr.getScaledHeight() / 2.0f;

        // Clamp to screen bounds
        float clampedX = Math.max(0, Math.min(sr.getScaledWidth(), screenX));
        float clampedY = Math.max(0, Math.min(sr.getScaledHeight(), screenY));

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.shadeModel(GL11.GL_FLAT);

        // Draw line from centre to projected point
        GL11.glLineWidth(AIM_LINE_WIDTH);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(centerX, centerY, 0.0).color(
            (AIM_LINE_COLOUR >> 16) & 0xFF,
            (AIM_LINE_COLOUR >> 8) & 0xFF,
            AIM_LINE_COLOUR & 0xFF,
            (AIM_LINE_COLOUR >> 24) & 0xFF
        ).endVertex();
        wr.pos(clampedX, clampedY, 0.0).color(
            (AIM_LINE_COLOUR >> 16) & 0xFF,
            (AIM_LINE_COLOUR >> 8) & 0xFF,
            AIM_LINE_COLOUR & 0xFF,
            (AIM_LINE_COLOUR >> 24) & 0xFF
        ).endVertex();
        tess.draw();

        // Draw dot at endpoint
        GL11.glPointSize(AIM_DOT_RADIUS * 2.0f);
        wr.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(clampedX, clampedY, 0.0).color(
            (AIM_DOT_COLOUR >> 16) & 0xFF,
            (AIM_DOT_COLOUR >> 8) & 0xFF,
            AIM_DOT_COLOUR & 0xFF,
            (AIM_DOT_COLOUR >> 24) & 0xFF
        ).endVertex();
        tess.draw();

        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    /**
     * Projects a world-space point into screen-space coordinates [0..width, 0..height].
     * Returns null if the point is behind the camera or at the camera plane.
     *
     * Uses a LEFT-HANDED view space (+Z forward) matching Minecraft's convention.
     */
    private float[] projectToScreen(Vec3 world, Minecraft mc) {
        Entity camera = mc.getRenderViewEntity();
        if (camera == null) camera = mc.thePlayer;
        if (camera == null) return null;

        // Use current position (no sub-tick interpolation needed for aim indicator)
        double camX = camera.posX;
        double camY = camera.posY + camera.getEyeHeight();
        double camZ = camera.posZ;

        double dx = world.xCoord - camX;
        double dy = world.yCoord - camY;
        double dz = world.zCoord - camZ;

        // Camera orientation
        double yawRad   = Math.toRadians(camera.rotationYaw);
        double pitchRad = Math.toRadians(camera.rotationPitch);

        // Camera forward vector (world space)
        double fwdX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fwdY = -Math.sin(pitchRad);
        double fwdZ =  Math.cos(yawRad) * Math.cos(pitchRad);

        // Camera right vector = forward × world_up(0,1,0)
        double rightX = -fwdZ;
        double rightY = 0.0;
        double rightZ = fwdX;

        // Camera up vector = right × forward
        double upX = rightY * fwdZ - rightZ * fwdY;
        double upY = rightZ * fwdX - rightX * fwdZ;
        double upZ = rightX * fwdY - rightY * fwdX;

        // Normalize up vector (should already be unit length, but guard against drift)
        double upLen = Math.sqrt(upX*upX + upY*upY + upZ*upZ);
        if (upLen > 0.001) {
            upX /= upLen; upY /= upLen; upZ /= upLen;
        }

        // Transform to LEFT-HANDED view space (+Z = forward)
        double viewX = dx * rightX + dy * rightY + dz * rightZ;
        double viewY = dx * upX    + dy * upY    + dz * upZ;
        double viewZ = dx * fwdX   + dy * fwdY   + dz * fwdZ;

        // Behind camera or at camera plane — point is not visible
        if (viewZ <= 0.001) return null;

        // Perspective projection (left-handed view space)
        double fovRad = Math.toRadians(mc.gameSettings.fovSetting);
        double focalLength = (mc.displayWidth / 2.0) / Math.tan(fovRad / 2.0);

        // Standard perspective divide with left-handed +Z forward
        float screenX = (float) (mc.displayWidth / 2.0 + (viewX / viewZ) * focalLength);
        float screenY = (float) (mc.displayHeight / 2.0 - (viewY / viewZ) * focalLength); // Y flipped for screen coords

        // Convert from display pixels to scaled GUI pixels
        ScaledResolution sr = new ScaledResolution(mc);
        screenX *= (float) sr.getScaledWidth() / mc.displayWidth;
        screenY *= (float) sr.getScaledHeight() / mc.displayHeight;

        return new float[] { screenX, screenY };
    }
}