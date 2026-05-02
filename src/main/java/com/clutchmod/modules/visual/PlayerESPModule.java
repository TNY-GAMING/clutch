package com.clutchmod.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import java.util.HashSet;
import java.util.Set;

/**
 * PlayerESP Module — 1.8.9
 * 
 * Renders other players through walls with customizable display options.
 * Similar to "Chams" feature in other clients.
 */
public class PlayerESPModule {

    public enum RenderMode {
        WIREFRAME_3D,    // 3D wireframe cube
        HITBOX,          // Adjusted hitbox
        BOUNDING_BOX,    // 2D bounding box
        SKELETON,        // Entity skeleton
        OUTLINE,         // Spectator-mode outline
        DISABLED
    }

    private boolean enabled = false;
    public boolean isEnabled()           { return enabled; }
    public void    setEnabled(boolean v) { enabled = v; }
    public void    toggle()              { enabled = !enabled; }

    // Rendering mode
    private RenderMode renderMode = RenderMode.WIREFRAME_3D;
    public RenderMode getRenderMode()              { return renderMode; }
    public void       setRenderMode(RenderMode m) { renderMode = m; }

    // Colors
    private int colorVisible = 0xFF00FF00;  // Green - visible players
    private int colorBehindWalls = 0xFFFF0000;  // Red - behind walls
    
    public int  getColorVisible()       { return colorVisible; }
    public void setColorVisible(int c)  { colorVisible = c; }
    public int  getColorBehindWalls()   { return colorBehindWalls; }
    public void setColorBehindWalls(int c) { colorBehindWalls = c; }

    // Rendering options
    private boolean showHealth = true;
    public boolean isShowHealth()           { return showHealth; }
    public void    setShowHealth(boolean v) { showHealth = v; }

    private boolean showName = true;
    public boolean isShowName()           { return showName; }
    public void    setShowName(boolean v) { showName = v; }

    private boolean showBackground = true;
    public boolean isShowBackground()           { return showBackground; }
    public void    setShowBackground(boolean v) { showBackground = v; }

    private boolean useFriendly = false;
    public boolean isUseFriendly()           { return useFriendly; }
    public void    setUseFriendly(boolean v) { useFriendly = v; }

    // Filtering
    private boolean hideBots = true;
    public boolean isHideBots()           { return hideBots; }
    public void    setHideBots(boolean v) { hideBots = v; }

    private boolean hideInvisibles = false;
    public boolean isHideInvisibles()           { return hideInvisibles; }
    public void    setHideInvisibles(boolean v) { hideInvisibles = v; }

    // Outline width
    private float outlineWidth = 2.0f;
    public float getOutlineWidth()        { return outlineWidth; }
    public void  setOutlineWidth(float v) { outlineWidth = Math.max(0.5f, Math.min(5.0f, v)); }

    // Friends list
    private final Set<String> friends = new HashSet<>();
    public Set<String> getFriends() { return friends; }

    /**
     * Check if a player should be rendered as an enemy.
     */
    public boolean shouldRender(Entity entity) {
        if (!enabled) return false;
        if (!(entity instanceof EntityPlayer)) return false;
        
        EntityPlayer player = (EntityPlayer) entity;
        
        // Hide bots (players with suspicious names or patterns)
        if (hideBots && isBot(player.getName())) return false;
        
        // Hide invisibles
        if (hideInvisibles && player.isInvisible()) return false;
        
        return true;
    }

    /**
     * Simple bot detection (name patterns).
     */
    private boolean isBot(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        // Common bot patterns
        return lower.contains("bot") || 
               lower.contains("anti") || 
               lower.contains("security") ||
               lower.contains("guard");
    }

    /**
     * Check if a player is in friend list.
     */
    public boolean isFriend(String playerName) {
        return friends.contains(playerName);
    }

    /**
     * Add player to friend list.
     */
    public void addFriend(String playerName) {
        if (playerName != null) friends.add(playerName);
    }

    /**
     * Remove player from friend list.
     */
    public void removeFriend(String playerName) {
        if (playerName != null) friends.remove(playerName);
    }

    public void onTick() {
        if (!enabled) return;
        // Rendering handled via render events
    }

    public void renderOverlay(Minecraft mc, ScaledResolution sr, FontRenderer fr) {
        if (!enabled || mc.theWorld == null) return;

        int count = 0;
        for (Object obj : mc.theWorld.playerEntities) {
            if (obj instanceof EntityPlayer && shouldRender((EntityPlayer) obj)) {
                count++;
            }
        }

        int x = 5;
        int y = sr.getScaledHeight() - 70;
        fr.drawStringWithShadow("PlayerESP enabled", x, y, 0xFFFFFF00);
        fr.drawStringWithShadow("Mode: " + renderMode.name(), x, y + 10, 0xFFFFFF00);
        fr.drawStringWithShadow("Players: " + count, x, y + 20, 0xFF00FF00);
    }
}
