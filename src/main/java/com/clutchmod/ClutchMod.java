package com.clutchmod;

import com.clutchmod.event.KeyInputHandler;
import com.clutchmod.event.RenderHandler;
import com.clutchmod.event.TickHandler;
import com.clutchmod.modules.combat.SilentAimModule;
import com.clutchmod.modules.movement.ClutchModule;
import com.clutchmod.modules.placement.FastPlaceModule;
import com.clutchmod.modules.visual.PerspectiveModule;
import com.clutchmod.modules.visual.PlayerESPModule;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

// BUG FIX: @Mod annotation must use string literals, NOT constant field references.
// ForgeGradle 2.x / FML's annotation scanner reads the raw bytecode; it does not
// evaluate field references, so modid = ClutchMod.MODID was being read as the
// literal string "ClutchMod.MODID" and the mod was never registered. This is why
// clutchmod never appeared in the FML mod table and init() never ran.
@Mod(modid = "clutchmod", version = "1.0.0", clientSideOnly = true)
public class ClutchMod {

    public static final String MODID   = "clutchmod";
    public static final String VERSION = "1.0.0";

    @Mod.Instance("clutchmod")
    public static ClutchMod instance;

    public static final String KEYBIND_CATEGORY = "ClutchMod";

    public static ClutchModule      CLUTCH;
    public static SilentAimModule   SILENT_AIM;
    public static FastPlaceModule   FAST_PLACE;
    public static PerspectiveModule PERSPECTIVE;
    public static PlayerESPModule   PLAYER_ESP;

    public static KeyBinding KEY_OPEN_MENU;
    public static KeyBinding KEY_TOGGLE_CLUTCH;
    public static KeyBinding KEY_TOGGLE_SILENT_AIM;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Initialise modules in preInit so they exist before any mixin fires
        CLUTCH      = new ClutchModule();
        SILENT_AIM  = new SilentAimModule();
        FAST_PLACE  = new FastPlaceModule();
        PERSPECTIVE = new PerspectiveModule();
        PLAYER_ESP  = new PlayerESPModule();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        KEY_OPEN_MENU = new KeyBinding(
                "key.clutchmod.open_menu",
                Keyboard.KEY_G,
                KEYBIND_CATEGORY
        );
        KEY_TOGGLE_CLUTCH = new KeyBinding(
                "key.clutchmod.toggle_clutch",
                Keyboard.KEY_NONE,
                KEYBIND_CATEGORY
        );
        KEY_TOGGLE_SILENT_AIM = new KeyBinding(
                "key.clutchmod.toggle_silent_aim",
                Keyboard.KEY_NONE,
                KEYBIND_CATEGORY
        );

        ClientRegistry.registerKeyBinding(KEY_OPEN_MENU);
        ClientRegistry.registerKeyBinding(KEY_TOGGLE_CLUTCH);
        ClientRegistry.registerKeyBinding(KEY_TOGGLE_SILENT_AIM);

        MinecraftForge.EVENT_BUS.register(new RenderHandler());
        MinecraftForge.EVENT_BUS.register(new TickHandler());
        MinecraftForge.EVENT_BUS.register(new KeyInputHandler());
    }
}
