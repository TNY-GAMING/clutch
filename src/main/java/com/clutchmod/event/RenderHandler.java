package com.clutchmod.event;

import com.clutchmod.ClutchMod;
import com.clutchmod.modules.movement.ClutchModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class RenderHandler {

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;

        ClutchModule clutch = ClutchMod.CLUTCH;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        ScaledResolution sr    = new ScaledResolution(mc);
        FontRenderer      fr   = mc.fontRendererObj;
        int               sw   = sr.getScaledWidth();
        int               sh   = sr.getScaledHeight();

        if (clutch.isEnabled() && clutch.isShowBlockCount()) {
            int available = clutch.availableBlocks();
            String text   = "Blocks: " + available;

            // Draw just right of the crosshair
            int x = sw / 2 + 10;
            int y = sh / 2 + 2;

            int color = clutch.isClutching() ? 0xFF5555 : 0xAAAAAA;
            fr.drawStringWithShadow(text, x, y, color);
        }

        if (ClutchMod.PERSPECTIVE != null) ClutchMod.PERSPECTIVE.renderOverlay(mc, sr, fr);
        if (ClutchMod.PLAYER_ESP != null) ClutchMod.PLAYER_ESP.renderOverlay(mc, sr, fr);
    }
}
