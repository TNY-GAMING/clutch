package com.clutchmod.event;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Stub — module ticking is handled by MixinMinecraft.clutchmod$onRunTick
 * which injects into Minecraft.runTick. That path is unconditional and works
 * on all launchers including Feather. This class exists only so ClutchMod.init
 * can register it without errors; if Forge events happen to fire it is a no-op
 * to avoid double-ticking.
 */
public class TickHandler {
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // Intentionally empty — see MixinMinecraft
    }
}
