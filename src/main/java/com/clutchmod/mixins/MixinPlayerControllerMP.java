// MixinPlayerControllerMP.java
package com.clutchmod.mixins;

import com.clutchmod.ModState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerControllerMP.class)
public abstract class MixinPlayerControllerMP {

    @Inject(
        method = "func_178890_a",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void clutchmod$onPlayerRightClick(
            EntityPlayerSP player,
            WorldClient world,
            ItemStack stack,
            BlockPos pos,
            EnumFacing facing,
            Vec3 vec,
            CallbackInfoReturnable cir) {

        if (ModState.FAST_PLACE == null || !ModState.FAST_PLACE.isEnabled()) return;
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return;

        if (!ModState.FAST_PLACE.canPlace()) {
            cir.setReturnValue(false);
        }
    }
}