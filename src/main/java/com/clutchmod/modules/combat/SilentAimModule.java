package com.clutchmod.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;

/**
 * SilentAimModule — 1.8.9
 *
 * Overrides the yaw/pitch injected into C03PacketPlayer (movement packets)
 * without touching the client's visual render angles.
 *
 * The actual packet injection is handled by MixinNetHandlerPlayClient.
 *
 * MovementMode:
 *   PROPER — movement direction matches server angles (AC-safe)
 *   NONE   — no movement correction (unsafe)
 *   SLOW   — gradual visual lerp toward server angles (cosmetic only, unsafe)
 */
public class SilentAimModule {

    // ─── Enable ──────────────────────────────────────────────────────────────
    private boolean enabled = false;
    public boolean isEnabled()           { return enabled; }
    public void    setEnabled(boolean v) { enabled = v; if (!v) clearOverride(); }

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
    public MovementMode getMovementMode()               { return movementMode; }
    public void         setMovementMode(MovementMode m) { movementMode = m; }

    // ─── Features ────────────────────────────────────────────────────────────
    private boolean thirdPersonAimView = false;
    private boolean aimIndicator       = false;
    private boolean useReach           = false;
    private boolean useHitboxes        = false;

    public boolean isThirdPersonAimView()           { return thirdPersonAimView; }
    public void    setThirdPersonAimView(boolean v) { thirdPersonAimView = v; }
    public boolean isAimIndicator()                 { return aimIndicator; }
    public void    setAimIndicator(boolean v)       { aimIndicator = v; }
    public boolean isUseReach()                     { return useReach; }
    public void    setUseReach(boolean v)           { useReach = v; }
    public boolean isUseHitboxes()                  { return useHitboxes; }
    public void    setUseHitboxes(boolean v)        { useHitboxes = v; }

    // ─── Runtime override ─────────────────────────────────────────────────────
    private float   serverYaw   = 0f;
    private float   serverPitch = 0f;
    private boolean hasOverride = false;

    public float   getServerYaw()   { return serverYaw; }
    public float   getServerPitch() { return serverPitch; }
    public boolean hasOverride()    { return hasOverride && enabled; }

    /**
     * Compute server-side angles to face the given world position.
     * Called by ClutchModule (or any other module).
     */
    public void aimAt(Vec3 target) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        double dx   = target.xCoord - mc.thePlayer.posX;
        double dy   = target.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz   = target.zCoord - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);

        serverYaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        // BUG FIX: dy sign was negated twice (atan2 negation + -toDegrees).
        // Correct: pitch is negative when looking down (Minecraft convention),
        // atan2(dy, dist) gives a positive angle for targets below eye level
        // when dy < 0, so we negate once.
        serverPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        serverPitch = Math.max(-90f, Math.min(90f, serverPitch)); // clamp to valid range
        hasOverride = true;
    }

    /** Per-tick processing — handles SLOW mode visual lerp. */
    public void onTick() {
        if (!enabled || !hasOverride) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        if (movementMode == MovementMode.SLOW) {
            // BUG FIX: naive lerp between yaw angles wraps incorrectly when
            // crossing the ±180° boundary (e.g. lerp(170°, -170°) goes the
            // long way round). Use shortest-arc interpolation.
            float yawDelta = wrapDegrees(serverYaw - mc.thePlayer.rotationYaw);
            mc.thePlayer.rotationYaw   += yawDelta * 0.2f;
            mc.thePlayer.rotationPitch = lerp(mc.thePlayer.rotationPitch, serverPitch, 0.2f);
        }
    }

    public void clearOverride() {
        hasOverride = false;
        // BUG FIX: reset stored angles so stale values can't leak through
        serverYaw   = 0f;
        serverPitch = 0f;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Wraps an angle delta into [-180, 180]. */
    private static float wrapDegrees(float angle) {
        angle %= 360f;
        if (angle >= 180f)  angle -= 360f;
        if (angle < -180f)  angle += 360f;
        return angle;
    }
}
