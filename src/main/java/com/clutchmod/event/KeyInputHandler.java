package com.clutchmod.event;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Stub — key handling is done in MixinMinecraft.handleKeys via Keyboard.isKeyDown.
 * That path works on all launchers. This class is registered on EVENT_BUS only
 * for compatibility; it does nothing to avoid double-firing with the mixin.
 */
public class KeyInputHandler {
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // Intentionally empty — see MixinMinecraft
    }
}
