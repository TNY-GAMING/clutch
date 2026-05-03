package com.clutchmod.mixins;

import com.clutchmod.ModState;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3rd Person Head Rendering for Silent Aim — 1.8.9
 *
 * When thirdPersonAimView is enabled and Silent Aim has an override, the player's
 * head in 3rd person view is rotated to face serverYaw instead of visual yaw.
 * This makes the 3rd person model look toward the actual aim target.
 *
 * INJECTION STRATEGY:
 *   RenderPlayer.doRender calls super.doRender (RenderLivingBase.doRender) after
 *   setting up the model. The super.doRender method internally calls renderModel,
 *   which uses entity.rotationYawHead to orient the head.
 *
 *   We inject BEFORE and AFTER the super.doRender call in RenderPlayer.doRender.
 *   Before: override entity.rotationYawHead with serverYaw.
 *   After: restore the original rotationYawHead.
 *
 *   This avoids having to target renderModel inside RenderLivingBase (which would
 *   affect ALL living entities, not just the local player).
 *
 * SRG NAMES (1.8.9):
 *   RenderPlayer.doRender = func_76986_a
 *   RenderLivingBase.doRender = func_76986_a (inherited)
 *   EntityLivingBase.rotationYawHead = field_70759_as (public, no reflection needed)
 *
 *   RenderPlayer.doRender descriptor: (Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V
 *   RenderLivingBase.doRender descriptor: (Lnet/minecraft/entity/EntityLivingBase;DDDFF)V
 */
@Mixin(RenderPlayer.class)
public abstract class MixinRenderPlayer {

    /**
     * Inject BEFORE super.doRender in RenderPlayer.doRender.
     * If conditions are met, override rotationYawHead to serverYaw so the
     * 3rd person model head points toward the aim target during rendering.
     */
    @Inject(
        method = "func_76986_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderLivingBase;func_76986_a(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
            remap = false
        ),
        remap = false
    )
    private void clutchmod$onPreSuperRender(AbstractClientPlayer entity,
                                               double x, double y, double z,
                                               float entityYaw, float partialTicks,
                                               CallbackInfo ci) {
        // Only affect the local player in 3rd person
        if (entity != net.minecraft.client.Minecraft.getMinecraft().thePlayer) return;
        if (ModState.SILENT_AIM == null) return;
        if (!ModState.SILENT_AIM.isEnabled()) return;
        if (!ModState.SILENT_AIM.isThirdPersonAimView()) return;
        if (!ModState.SILENT_AIM.hasOverride()) return;

        // Only apply in 3rd person (not 1st person)
        if (net.minecraft.client.Minecraft.getMinecraft().gameSettings.thirdPersonView == 0) return;

        // Save original and override with server yaw
        float savedYawHead = entity.rotationYawHead;
        float serverYaw = ModState.SILENT_AIM.getServerYaw();
        entity.rotationYawHead = serverYaw;

        // Store for restoration — ThreadLocal because doRender is render-thread only
        clutchmod$lastYawHead.set(savedYawHead);
    }

    /**
     * Inject AFTER super.doRender returns. Restore the original rotationYawHead
     * so we don't permanently mutate the entity state.
     */
    @Inject(
        method = "func_76986_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderLivingBase;func_76986_a(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
            shift = At.Shift.AFTER,
            remap = false
        ),
        remap = false
    )
    private void clutchmod$onPostSuperRender(AbstractClientPlayer entity,
                                                double x, double y, double z,
                                                float entityYaw, float partialTicks,
                                                CallbackInfo ci) {
        if (entity != net.minecraft.client.Minecraft.getMinecraft().thePlayer) return;
        Float saved = clutchmod$lastYawHead.get();
        if (saved != null) {
            entity.rotationYawHead = saved;
            clutchmod$lastYawHead.remove();
        }
    }

    /** Thread-local storage for the original rotationYawHead value. */
    private static final ThreadLocal<Float> clutchmod$lastYawHead = new ThreadLocal<Float>();
}