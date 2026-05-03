package com.clutchmod.mixins;

import com.clutchmod.ModState;
import com.clutchmod.modules.combat.SilentAimModule;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PROPER Movement Correction for Silent Aim — 1.8.9
 *
 * Injected into EntityPlayerSP.onLivingUpdate (func_70636_d) BEFORE the physics tick.
 * When Silent Aim is in PROPER mode and has an override, we rotate the player's
 * moveForward/moveStrafing inputs by the delta between visual rotationYaw and serverYaw.
 *
 * WHY THIS WORKS (Anti-Cheat Safety):
 *   The server receives C03 packets with serverYaw (via MixinNetHandlerPlayClient).
 *   The server then computes expected motion from that yaw + moveForward/moveStrafing.
 *   If the client sends serverYaw but moves using visualYaw, the server's predicted
 *   position diverges → NCP/Grim/Spartan flags "movement packet desync".
 *   By rotating the inputs to compensate for the yaw delta, the client's actual
 *   motion aligns with what the server computes from the packet yaw.
 *
 * MATHEMATICAL DERIVATION:
 *   Let V = visual yaw (what the player sees), S = server yaw (what the packet says).
 *   Let delta = S - V (how much the server yaw is rotated from visual).
 *   The server computes motion using S and the raw inputs (F, St).
 *   We want the server's computed motion to equal the motion the player INTENDS
 *   (which is computed using V and the original inputs).
 *
 *   Server motion = Rotate(S, newInputs)
 *   Intended motion = Rotate(V, oldInputs)
 *   We need: Rotate(S, newInputs) = Rotate(V, oldInputs)
 *   Therefore: newInputs = Rotate(V - S, oldInputs) = Rotate(-delta, oldInputs)
 *
 *   In 2D rotation matrix terms:
 *     newForward  = oldForward * cos(-delta) + oldStrafe * sin(-delta)
 *                 = oldForward * cos(delta)  - oldStrafe * sin(delta)
 *     newStrafe   = oldStrafe * cos(-delta) - oldForward * sin(-delta)
 *                 = oldStrafe * cos(delta)  + oldForward * sin(delta)
 *
 *   Note the sign swap compared to a positive rotation — this is critical.
 *   A positive delta (server yaw > visual yaw) means the server thinks we're
 *   looking further clockwise. To compensate, we must rotate our inputs
 *   counter-clockwise by the same amount.
 *
 * SRG NAMES (1.8.9):
 *   EntityLivingBase.onLivingUpdate = func_70636_d
 *   EntityLivingBase.moveForward    = field_70701_bs
 *   EntityLivingBase.moveStrafing   = field_70702_br
 *   Entity.rotationYaw              = field_70177_m  (public, no reflection needed)
 *
 * THREAD SAFETY:
 *   This mixin runs on the client thread (Minecraft.runTick → onLivingUpdate).
 *   SilentAimModule.getServerYaw() reads an AtomicReference — safe without sync.
 *   The network thread (packet mixin) may update the reference concurrently, but
 *   AtomicReference guarantees we see a consistent yaw/pitch pair (no torn reads).
 *
 * CONFLICT AVOIDANCE:
 *   Method prefix clutchmod$ — unique to this mod. Will not collide with FeatherOpt
 *   or Essential mixins on the same class.
 */
@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP {

    @Inject(
        method = "func_70636_d", // onLivingUpdate
        at = @At("HEAD"),
        remap = false
    )
    private void clutchmod$onLivingUpdatePre(CallbackInfo ci) {
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;

        // Fast path checks
        if (ModState.SILENT_AIM == null) return;
        if (!ModState.SILENT_AIM.isEnabled()) return;
        if (!ModState.SILENT_AIM.hasOverride()) return;
        if (ModState.SILENT_AIM.getMovementMode() != SilentAimModule.MovementMode.PROPER) return;

        // Only correct when the player is actually providing movement input
        if (player.moveForward == 0.0f && player.moveStrafing == 0.0f) return;

        float serverYaw = ModState.SILENT_AIM.getServerYaw();
        float visualYaw = player.rotationYaw;

        // Delta = how much the server yaw differs from visual yaw (S - V)
        float yawDelta = serverYaw - visualYaw;
        double yawRad = Math.toRadians(yawDelta);

        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);

        // CRITICAL: rotate by -yawDelta to compensate for the server yaw offset.
        // See mathematical derivation in class javadoc.
        float oldForward = player.moveForward;
        float oldStrafe  = player.moveStrafing;

        player.moveForward  = (float) ( oldForward * cos + oldStrafe * sin);
        player.moveStrafing = (float) (-oldForward * sin + oldStrafe * cos);

        // Normalize to prevent speed amplification from floating-point drift
        float max = Math.max(Math.abs(player.moveForward), Math.abs(player.moveStrafing));
        if (max > 1.0f) {
            player.moveForward  /= max;
            player.moveStrafing /= max;
        }
    }
}