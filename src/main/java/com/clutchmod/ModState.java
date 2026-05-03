package com.clutchmod;

import com.clutchmod.modules.combat.SilentAimModule;
import com.clutchmod.modules.movement.ClutchModule;
import com.clutchmod.modules.placement.FastPlaceModule;
import com.clutchmod.modules.visual.PerspectiveModule;
import com.clutchmod.modules.visual.PlayerESPModule;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * ModState — holds all runtime module instances and keybinds.
 *
 * WHY THIS EXISTS:
 *   In Forge 1.8.9 + Feather client, coremod/mixin classes are loaded by
 *   LaunchClassLoader BEFORE FML scans for @Mod annotations. If mixins
 *   reference ClutchMod (the @Mod class) directly, LaunchClassLoader loads
 *   ClutchMod first. FML's ModClassLoader then skips it (already loaded).
 *   preInit()/init() never run → all module fields stay null.
 *
 *   ModState is NOT a @Mod class. It is loaded by LaunchClassLoader (fine).
 *   ClutchMod is a pure @Mod class, loaded by ModClassLoader, with NO
 *   references from mixins/coremods. FML sees it, scans @Mod, calls preInit.
 *
 *   ClutchMod.preInit() calls ModState.initModules().
 *   ClutchMod.init() calls ModState.initKeybinds() and registers them.
 *   All mixins/event handlers reference ModState (not ClutchMod).
 */
public class ModState {

    // ─── Modules ─────────────────────────────────────────────────────────────
    public static ClutchModule CLUTCH;
    public static SilentAimModule SILENT_AIM;
    public static FastPlaceModule FAST_PLACE;
    public static PerspectiveModule PERSPECTIVE;
    public static PlayerESPModule PLAYER_ESP;

    // ─── Keybinds ────────────────────────────────────────────────────────────
    public static KeyBinding KEY_OPEN_MENU;
    public static KeyBinding KEY_TOGGLE_CLUTCH;
    public static KeyBinding KEY_TOGGLE_SILENT_AIM;

    // ─── Init ────────────────────────────────────────────────────────────────

    public static void initModules() {
        CLUTCH = new ClutchModule();
        SILENT_AIM = new SilentAimModule();
        FAST_PLACE = new FastPlaceModule();
        PERSPECTIVE = new PerspectiveModule();
        PLAYER_ESP = new PlayerESPModule();
    }

    public static void initKeybinds() {
        KEY_OPEN_MENU = new KeyBinding(
            "key.clutchmod.open_menu",
            Keyboard.KEY_G,
            "ClutchMod"
        );
        KEY_TOGGLE_CLUTCH = new KeyBinding(
            "key.clutchmod.toggle_clutch",
            Keyboard.KEY_NONE,
            "ClutchMod"
        );
        KEY_TOGGLE_SILENT_AIM = new KeyBinding(
            "key.clutchmod.toggle_silent_aim",
            Keyboard.KEY_NONE,
            "ClutchMod"
        );

        ClientRegistry.registerKeyBinding(KEY_OPEN_MENU);
        ClientRegistry.registerKeyBinding(KEY_TOGGLE_CLUTCH);
        ClientRegistry.registerKeyBinding(KEY_TOGGLE_SILENT_AIM);
    }

    public static void registerEventHandlers() {
        MinecraftForge.EVENT_BUS.register(new ModState.TickHandler());
        MinecraftForge.EVENT_BUS.register(new ModState.KeyHandler());
        MinecraftForge.EVENT_BUS.register(new com.clutchmod.event.RenderHandler());
    }

    // ─── Inline event handlers (avoid separate classes referencing ModState) ───

    public static class TickHandler {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (CLUTCH != null) CLUTCH.onTick();
            if (SILENT_AIM != null) SILENT_AIM.onTick();
            if (FAST_PLACE != null) FAST_PLACE.onTick();
            if (PERSPECTIVE != null) PERSPECTIVE.onTick();
            if (PLAYER_ESP != null) PLAYER_ESP.onTick();
        }
    }

    public static class KeyHandler {
        @SubscribeEvent
        public void onKeyInput(InputEvent.KeyInputEvent event) {
            if (KEY_TOGGLE_CLUTCH != null && KEY_TOGGLE_CLUTCH.isPressed()) {
                if (CLUTCH != null) CLUTCH.toggle();
            }
            if (KEY_TOGGLE_SILENT_AIM != null && KEY_TOGGLE_SILENT_AIM.isPressed()) {
                if (SILENT_AIM != null) SILENT_AIM.toggle();
            }
        }
    }
}
