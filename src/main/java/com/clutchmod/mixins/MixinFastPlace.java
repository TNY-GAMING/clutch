package com.clutchmod.mixins;

import com.clutchmod.ClutchMod;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Records the timestamp of each C08 placement packet sent to the server.
 * This is used by FastPlaceModule to enforce the configurable placement delay.
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class MixinFastPlace {

    @ModifyArg(
        method = "func_147297_a",   // addToSendQueue
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkManager;func_179290_a(Lnet/minecraft/network/Packet;)V",
            remap = false
        ),
        index = 0,
        remap = false
    )
    private net.minecraft.network.Packet<?> clutchmod$onSendBlockPlacement(
            net.minecraft.network.Packet<?> packet) {
        if (ClutchMod.FAST_PLACE != null && packet instanceof C08PacketPlayerBlockPlacement) {
            ClutchMod.FAST_PLACE.recordPlace();
        }
        return packet;
    }
}
