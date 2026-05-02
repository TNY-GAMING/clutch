package com.clutchmod.mixins;

// MixinEntityPlayerSP was removed to prevent double-application of silent aim rotation.
// MixinNetHandlerPlayClient handles rotation modification at the lower NetworkManager layer,
// which intercepts all packets (not just those from onUpdateWalkingPlayer).
// Having both mixins active caused yaw/pitch to be applied twice per movement packet.

// This file is intentionally left as a no-op placeholder.
// If the mixin config references this class it must still exist and compile.

import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP {
    // Intentionally empty — rotation is handled solely by MixinNetHandlerPlayClient
}
