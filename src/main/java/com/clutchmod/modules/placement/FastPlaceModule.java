package com.clutchmod.modules.placement;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;

/**
 * FastPlaceModule — 1.8.9
 *
 * Speeds up block/item placement by enforcing a configurable minimum delay
 * between placements instead of the vanilla ~4-tick server cooldown.
 */
public class FastPlaceModule {

    public enum HeldItemMode {
        ALL,        // Activates regardless of held item
        BLOCKS,     // Activates only for blocks
        PROJECTILES // Activates only for projectiles
    }

    private boolean enabled = false;
    public boolean isEnabled()           { return enabled; }
    public void    setEnabled(boolean v) { enabled = v; }
    public void    toggle()              { enabled = !enabled; }

    // Held item filter
    private HeldItemMode heldItemMode = HeldItemMode.ALL;
    public HeldItemMode getHeldItemMode()               { return heldItemMode; }
    public void         setHeldItemMode(HeldItemMode m) { heldItemMode = m; }

    // ── Delay ────────────────────────────────────────────────────────────────
    // Store delay in ticks (1 tick = 50 ms).
    // 0 = fastest possible (one placement per tick).
    private int delayTicks = 0;

    public int  getDelayTicks()      { return delayTicks; }
    public void setDelayTicks(int v) { delayTicks = Math.max(0, Math.min(20, v)); }

    /**
     * BUG FIX: original setDelayMs used `(v + 25) / 50` which rounds 0 ms to
     * 0 ticks (OK) but rounds 1–49 ms to 1 tick instead of 0. More importantly,
     * the GUI passes the raw ms value the user typed; we want a simple
     * round-to-nearest conversion.
     */
    public int  getDelayMs()         { return delayTicks * 50; }
    public void setDelayMs(int v)    { setDelayTicks(Math.round(v / 50f)); }

    // ── Sub-feature toggles ───────────────────────────────────────────────────
    private boolean placeBlock = true;
    private boolean breakBlock = true;

    public boolean isPlaceBlock()             { return placeBlock; }
    public void    setPlaceBlock(boolean v)   { placeBlock = v; }
    public boolean isBreakBlock()             { return breakBlock; }
    public void    setBreakBlock(boolean v)   { breakBlock = v; }

    // ── Runtime state ─────────────────────────────────────────────────────────
    // BUG FIX: was storing lastPlaceTick as a long representing ticksExisted.
    // ticksExisted resets to 0 on world reload, so the first placement after
    // a reload always appeared to be on cooldown. Use System.currentTimeMillis()
    // instead for a monotonic clock independent of game ticks.
    private long lastPlaceMs = 0L;

    public void onTick() {
        // No per-tick state needed right now.
    }

    /**
     * Returns true if a new placement should be allowed right now.
     *
     * BUG FIX: original method returned false when !placeBlock (blocking the
     * placement even when the module was disabled for blocks), but
     * MixinPlayerControllerMP calls canPlace() for ALL right-clicks, not just
     * block placements. If placeBlock is false we should allow it through
     * (let vanilla handle it), not cancel it.
     */
    public boolean canPlace() {
        if (!enabled) return true; // module off → don't interfere

        if (!placeBlock) return true; // block sub-feature off → pass through

        if (!canPlaceWithHeldItem()) return true; // wrong item type → pass through

        long now = System.currentTimeMillis();
        long delayMs = delayTicks * 50L;
        return (now - lastPlaceMs) >= delayMs;
    }

    private boolean canPlaceWithHeldItem() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return false;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return heldItemMode == HeldItemMode.ALL;

        Item item = held.getItem();
        switch (heldItemMode) {
            case ALL:         return true;
            case BLOCKS:      return item instanceof ItemBlock;
            case PROJECTILES: return isProjectile(item);
            default:          return true;
        }
    }

    private boolean isProjectile(Item item) {
        return item instanceof ItemBow
            || item instanceof ItemSnowball
            || item instanceof ItemEgg
            || item instanceof ItemEnderPearl;
    }

    /**
     * Called by MixinFastPlace whenever a C08 placement packet is sent.
     *
     * BUG FIX: original used ticksExisted which has the reset problem above.
     */
    public void recordPlace() {
        lastPlaceMs = System.currentTimeMillis();
    }
}
