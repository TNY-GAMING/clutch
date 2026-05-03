// MixinFastPlace.java
package com.clutchmod.mixins;

import com.clutchmod.ModState;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NetHandlerPlayClient.class)
public abstract class MixinFastPlace {

    @ModifyArg(
        method = "func_147297_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkManager;func_179290_a(Lnet/minecraft/network/Packet;)V",
            remap = false
        ),
        index = 0,
        remap = false
    )
    private net.minecraft.network.Packet clutchmod$onSendBlockPlacement(
            net.minecraft.network.Packet packet) {
        if (ModState.FAST_PLACE != null && packet instanceof C08PacketPlayerBlockPlacement) {
            ModState.FAST_PLACE.recordPlace();
        }
        return packet;
    }
}