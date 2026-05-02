package com.clutchmod.mixins;

import com.clutchmod.ClutchMod;
import com.clutchmod.gui.ModMenuScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives all per-tick logic (module ticks + key input) via Mixin.
 *
 * WHY MIXIN NOT FORGE EVENTS:
 *   TickEvent.ClientTickEvent fires on FMLCommonHandler.instance().bus(),
 *   NOT MinecraftForge.EVENT_BUS. On Feather/modified launchers that bus may
 *   not route at all — EVENT_BUS registration silently does nothing.
 *   Injecting into Minecraft.runTick is unconditional and always works.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    private boolean cm$lastG = false;
    private boolean cm$lastC = false;
    private boolean cm$lastV = false;

    @Inject(
        method = "func_71407_l",
        at = @At("HEAD"),
        remap = false
    )
    private void clutchmod$onRunTick(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;

        if (mc.thePlayer != null && mc.theWorld != null) {
            handleKeys(mc);
        }

        if (ClutchMod.CLUTCH      != null) ClutchMod.CLUTCH.onTick();
        if (ClutchMod.SILENT_AIM  != null) ClutchMod.SILENT_AIM.onTick();
        if (ClutchMod.FAST_PLACE  != null) ClutchMod.FAST_PLACE.onTick();
        if (ClutchMod.PERSPECTIVE != null) ClutchMod.PERSPECTIVE.onTick();
        if (ClutchMod.PLAYER_ESP  != null) ClutchMod.PLAYER_ESP.onTick();
    }

    private void handleKeys(Minecraft mc) {
        boolean gDown = Keyboard.isKeyDown(Keyboard.KEY_G);
        if (gDown && !cm$lastG) {
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(new ModMenuScreen());
            } else if (mc.currentScreen instanceof ModMenuScreen) {
                mc.displayGuiScreen(null);
            }
        }
        cm$lastG = gDown;

        boolean cDown = Keyboard.isKeyDown(Keyboard.KEY_C);
        if (cDown && !cm$lastC && ClutchMod.CLUTCH != null) {
            ClutchMod.CLUTCH.toggle();
        }
        cm$lastC = cDown;

        boolean vDown = Keyboard.isKeyDown(Keyboard.KEY_V);
        if (vDown && !cm$lastV && ClutchMod.SILENT_AIM != null) {
            ClutchMod.SILENT_AIM.toggle();
        }
        cm$lastV = vDown;
    }
}
