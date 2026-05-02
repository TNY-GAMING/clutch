package com.clutchmod.mixins;

import com.clutchmod.ClutchMod;
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
        method = "func_178890_a",  // onPlayerRightClick
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
            CallbackInfoReturnable<Boolean> cir) {

        // BUG FIX: original code called canPlace() even when FastPlace was
        // disabled, and cancelled the event when canPlace returned false.
        // canPlace() already returns true when disabled, so this is safe now,
        // but the guard below is explicit for clarity.
        if (ClutchMod.FAST_PLACE == null || !ClutchMod.FAST_PLACE.isEnabled()) return;

        // Only gate block placements (not entity interactions, food, etc.)
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return;

        if (!ClutchMod.FAST_PLACE.canPlace()) {
            cir.setReturnValue(false); // Suppress this placement attempt
        }
    }
}
