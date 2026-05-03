package com.clutchmod.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;

import java.util.concurrent.atomic.AtomicReference;

/**
 * SilentAimModule — 1.8.9
 *
 * Overrides the yaw/pitch injected into C03PacketPlayer (movement packets)
 * without touching the client's visual render angles.
 *
 * The actual packet injection is handled by MixinNetHandlerPlayClient.
 * PROPER movement correction is handled by MixinEntityPlayerSP.
 *
 * THREAD SAFETY:
 *   serverYaw/serverPitch/hasOverride are read from THREE threads:
 *   1. Client thread  — MixinEntityPlayerSP.onLivingUpdate (PROPER rotation)
 *   2. Network thread — MixinNetHandlerPlayClient.addToSendQueue (packet patch)
 *   3. Render thread  — RenderHandler / MixinRenderPlayer (indicator + head)
 *   AtomicReference<float[]> guarantees atomic read/write of the yaw/pitch pair.
 *   hasOverride is volatile for single-field visibility.
 */
public class SilentAimModule {

    // ─── Enable ──────────────────────────────────────────────────────────────
    private volatile boolean enabled = false;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) {
        enabled = v;
        if (!v) clearOverride();
    }

    /**
     * BUG FIX: original toggle() didn't clear the override when disabling.
     * If the module was toggled off mid-aim the packet mixin would keep
     * injecting the stale serverYaw/Pitch until the next aimAt() call.
     */
    public void toggle() {
        enabled = !enabled;
        if (!enabled) clearOverride();
    }

    // ─── Movement mode ────────────────────────────────────────────────────────
    public enum MovementMode { PROPER, NONE, SLOW }
    private MovementMode movementMode = MovementMode.PROPER;
    public MovementMode getMovementMode() { return movementMode; }
    public void setMovementMode(MovementMode m) { movementMode = m; }

    // ─── Features ────────────────────────────────────────────────────────────
    private boolean thirdPersonAimView = false;
    private boolean aimIndicator = false;
    private boolean useReach = false;
    private boolean useHitboxes = false;

    public boolean isThirdPersonAimView() { return thirdPersonAimView; }
    public void setThirdPersonAimView(boolean v) { thirdPersonAimView = v; }
    public boolean isAimIndicator() { return aimIndicator; }
    public void setAimIndicator(boolean v) { aimIndicator = v; }
    public boolean isUseReach() { return useReach; }
    public void setUseReach(boolean v) { useReach = v; }
    public boolean isUseHitboxes() { return useHitboxes; }
    public void setUseHitboxes(boolean v) { useHitboxes = v; }

    // ─── Runtime override (THREAD-SAFE) ────────────────────────────────────────
    /**
     * AtomicReference holds float[2] = {serverYaw, serverPitch}.
     * Atomic read/write of the pair prevents torn reads across threads.
     * null = no override active.
     */
    private final AtomicReference<float[]> serverAngles = new AtomicReference<float[]>(null);

    /**
     * Volatile flag for fast path check. Always true when serverAngles is non-null.
     * Set atomically with the reference update in aimAt/clearOverride.
     */
    private volatile boolean hasOverride = false;

    public float getServerYaw() {
        float[] angles = serverAngles.get();
        return angles != null ? angles[0] : 0f;
    }

    public float getServerPitch() {
        float[] angles = serverAngles.get();
        return angles != null ? angles[1] : 0f;
    }

    public boolean hasOverride() {
        // Volatile read — safe from any thread without synchronization
        return hasOverride && enabled;
    }

    /**
     * Compute server-side angles to face the given world position.
     * Called by ClutchModule (or any other module) on the client thread.
     * Writes are atomic — network/render threads see consistent pair.
     */
    public void aimAt(Vec3 target) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        double dx = target.xCoord - mc.thePlayer.posX;
        double dy = target.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = target.zCoord - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        // BUG FIX: dy sign was negated twice (atan2 negation + -toDegrees).
        // Correct: pitch is negative when looking down (Minecraft convention),
        // atan2(dy, dist) gives a positive angle for targets below eye level
        // when dy < 0, so we negate once.
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        pitch = Math.max(-90f, Math.min(90f, pitch)); // clamp to valid range

        // Atomic update: set reference first, then volatile flag
        serverAngles.set(new float[] { yaw, pitch });
        hasOverride = true;
    }

    /** Per-tick processing — handles SLOW mode visual lerp. Client thread only. */
    public void onTick() {
        if (!enabled || !hasOverride) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        if (movementMode == MovementMode.SLOW) {
            // BUG FIX: naive lerp between yaw angles wraps incorrectly when
            // crossing the ±180° boundary (e.g. lerp(170°, -170°) goes the
            // long way round). Use shortest-arc interpolation.
            float yawDelta = wrapDegrees(getServerYaw() - mc.thePlayer.rotationYaw);
            mc.thePlayer.rotationYaw += yawDelta * 0.2f;
            mc.thePlayer.rotationPitch = lerp(mc.thePlayer.rotationPitch, getServerPitch(), 0.2f);
        }
    }

    public void clearOverride() {
        // Atomic clear: null the reference, then clear the flag
        serverAngles.set(null);
        hasOverride = false;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Wraps an angle delta into [-180, 180]. */
    private static float wrapDegrees(float angle) {
        angle %= 360f;
        if (angle >= 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }
}