package com.clutchmod;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * ClutchMod — pure @Mod class. NO references from mixins/coremods.
 *
 * All functionality lives in ModState. This class only bootstraps it.
 */
@Mod(modid = "clutchmod", name = "ClutchMod", version = "1.0.0", acceptableRemoteVersions = "")
public class ClutchMod {

    public static final String MODID = "clutchmod";
    public static final String VERSION = "1.0.0";

    @Mod.Instance("clutchmod")
    public static ClutchMod instance;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModState.initModules();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        ModState.initKeybinds();
        ModState.registerEventHandlers();
    }
}