package com.clutchmod.mixins;

import com.clutchmod.ModState;
import com.clutchmod.gui.ModMenuScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    private boolean wasGDown = false;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void clutchmod$onRunTick(CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;

        if (Keyboard.isKeyDown(Keyboard.KEY_G) && ModState.KEY_OPEN_MENU != null && !wasGDown) {
            mc.displayGuiScreen(new ModMenuScreen());
        }
        wasGDown = Keyboard.isKeyDown(Keyboard.KEY_G);
    }
}