package com.clutchmod.modules.movement;

import com.clutchmod.ClutchMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * ClutchModule — 1.8.9
 *
 * Automatically places blocks beneath the player to survive:
 *   • Void falls
 *   • Lethal fall damage
 *   • Falls beyond a configurable distance
 */
public class ClutchModule {

    // ─── Enable ──────────────────────────────────────────────────────────────
    private boolean enabled = false;
    public boolean isEnabled()           { return enabled; }
    public void    setEnabled(boolean v) { enabled = v; }
    public void    toggle()              { enabled = !enabled; }

    // ─── Activation conditions ────────────────────────────────────────────────
    private boolean activateOnVoid        = true;
    private boolean activateOnLethalFall  = true;
    private boolean activateOnMoreThanX   = false;
    private int     triggerBlocks         = 4;

    public boolean isActivateOnVoid()              { return activateOnVoid; }
    public void    setActivateOnVoid(boolean v)    { activateOnVoid = v; }
    public boolean isActivateOnLethalFall()        { return activateOnLethalFall; }
    public void    setActivateOnLethalFall(boolean v){ activateOnLethalFall = v; }
    public boolean isActivateOnMoreThanX()         { return activateOnMoreThanX; }
    public void    setActivateOnMoreThanX(boolean v){ activateOnMoreThanX = v; }
    public int     getTriggerBlocks()              { return triggerBlocks; }
    public void    setTriggerBlocks(int v)         { triggerBlocks = Math.max(1, v); }

    // ─── Feature toggles ─────────────────────────────────────────────────────
    private boolean useSilentAim     = false;
    private boolean showBlockCount   = true;
    private boolean resetAngle       = true;
    private boolean returnToSlot     = true;
    private boolean allowStaircaseUp = false;
    private boolean clutchMoveDelay  = false;
    private int     maxBlocks        = 0;

    public boolean isUseSilentAim()              { return useSilentAim; }
    public void    setUseSilentAim(boolean v)    { useSilentAim = v; }
    public boolean isShowBlockCount()            { return showBlockCount; }
    public void    setShowBlockCount(boolean v)  { showBlockCount = v; }
    public boolean isResetAngle()                { return resetAngle; }
    public void    setResetAngle(boolean v)      { resetAngle = v; }
    public boolean isReturnToSlot()              { return returnToSlot; }
    public void    setReturnToSlot(boolean v)    { returnToSlot = v; }
    public boolean isAllowStaircaseUp()          { return allowStaircaseUp; }
    public void    setAllowStaircaseUp(boolean v){ allowStaircaseUp = v; }
    public boolean isClutchMoveDelay()           { return clutchMoveDelay; }
    public void    setClutchMoveDelay(boolean v) { clutchMoveDelay = v; }
    public int     getMaxBlocks()                { return maxBlocks; }
    public void    setMaxBlocks(int v)           { maxBlocks = Math.max(0, v); }

    // ─── Block filter ─────────────────────────────────────────────────────────
    public enum FilterMode { BLACKLIST, WHITELIST }
    private FilterMode       filterMode = FilterMode.BLACKLIST;
    private final List<Item> filterList = new ArrayList<Item>();

    public FilterMode getFilterMode()             { return filterMode; }
    public void       setFilterMode(FilterMode m) { filterMode = m; }
    public List<Item> getFilterList()             { return filterList; }

    // ─── Runtime state ────────────────────────────────────────────────────────
    private boolean clutching      = false;
    private int     blocksPlaced   = 0;
    private int     savedSlot      = -1;
    private float   savedYaw       = 0f;
    private float   savedPitch     = 0f;
    private int     moveDelayTicks = 0;

    // BUG FIX: track staircase jump cooldown to avoid spamming jump every tick
    private int jumpCooldown = 0;

    public boolean isClutching()    { return clutching; }
    public int     getBlocksPlaced(){ return blocksPlaced; }

    /** Count of usable block items across hotbar. */
    public int availableBlocks() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return 0;
        int total = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (isUsableStack(s)) total += s.stackSize;
        }
        return total;
    }

    // ─── Main tick ────────────────────────────────────────────────────────────

    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled || mc.thePlayer == null || mc.theWorld == null) return;

        // Move-delay cooldown after a clutch finishes
        if (moveDelayTicks > 0) {
            moveDelayTicks--;
            mc.thePlayer.setSprinting(false);
            net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    mc.gameSettings.keyBindForward.getKeyCode(), false);
            return;
        }

        if (jumpCooldown > 0) jumpCooldown--;

        if (!shouldActivate(mc)) {
            if (clutching) finishClutch(mc);
            return;
        }

        if (!clutching) startClutch(mc);

        // Max-block cap
        if (maxBlocks > 0 && blocksPlaced >= maxBlocks) {
            finishClutch(mc);
            return;
        }

        int slot = findBlockSlot(mc);
        if (slot == -1) { finishClutch(mc); return; }

        // Switch hotbar slot (send packet so server knows)
        if (mc.thePlayer.inventory.currentItem != slot) {
            mc.thePlayer.inventory.currentItem = slot;
            mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(slot));
        }

        // Aim downward
        aimDown(mc);

        // BUG FIX: original code placed at `below` (the air block) then sent
        // C08 targeting `below.down()` (two blocks below feet) — the placement
        // target was wrong. The correct MC 1.8.9 placement sends C08 with the
        // block the player is clicking ON (the support face), with face UP.
        // We want to place a block at `posY - 1`, so the support is `posY - 2`.
        BlockPos feetPos    = new BlockPos(mc.thePlayer);          // floor of player pos
        BlockPos placePos   = feetPos.down();                      // block directly below feet
        BlockPos supportPos = placePos.down();                     // the face we click

        // Only place if the target position is actually air
        if (mc.theWorld.getBlockState(placePos).getBlock() instanceof BlockAir) {
            // BUG FIX: also verify support exists; if it's also air, try one
            // block lower (handles the player falling through multiple air blocks
            // per tick at high speeds).
            if (!(mc.theWorld.getBlockState(supportPos).getBlock() instanceof BlockAir)) {
                placeAt(mc, supportPos, EnumFacing.UP);
            } else {
                // Both blocks below are air; try clicking on a side neighbour
                // as a scaffold (common clutch technique for voids).
                for (EnumFacing face : new EnumFacing[]{
                        EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST}) {
                    BlockPos side = placePos.offset(face);
                    if (!(mc.theWorld.getBlockState(side).getBlock() instanceof BlockAir)) {
                        placeAt(mc, side, face.getOpposite());
                        break;
                    }
                }
            }
        }

        // Staircase: jump periodically so the player rides the placed block up
        if (allowStaircaseUp && jumpCooldown <= 0 && mc.thePlayer.onGround) {
            mc.thePlayer.jump();
            jumpCooldown = 4; // BUG FIX: was jumping every tick — too spammy
        }
    }

    // ─── Activation check ────────────────────────────────────────────────────

    private boolean shouldActivate(Minecraft mc) {
        EntityPlayerSP p = mc.thePlayer;

        // BUG FIX: original threshold was >= -0.1, which fires even when the
        // player is moving upward slightly (e.g. stepping up a block). A player
        // genuinely falling will have motionY < -0.3 after one tick of gravity.
        if (p.motionY > -0.1) return false;

        // BUG FIX: don't activate while the player is on the ground.
        if (p.onGround) return false;

        float fallDist = p.fallDistance;

        if (activateOnVoid && isOverVoid(mc)) return true;

        if (activateOnLethalFall) {
            // Vanilla damage = fallDist - 3.  Account for armour via getTotalArmorValue().
            float armorReduction = p.getTotalArmorValue() / 25.0f; // rough ~0–1 reduction
            float damage = (fallDist - 3f) * (1f - armorReduction * 0.25f);
            if (damage >= p.getHealth()) return true;
        }

        if (activateOnMoreThanX && fallDist >= triggerBlocks) return true;

        return false;
    }

    /**
     * Returns true when no solid block exists below ANY corner of the player's
     * bounding box all the way down to y=1.
     *
     * BUG FIX: original code returned true as soon as ONE column was clear,
     * even if the other three were solid (i.e. the player was standing right on
     * the edge). Now ALL four columns must be void for the void condition to
     * trigger — avoids false positives on cliff edges.
     *
     * Also fixed: the offset calculation used `off[0] * 0.6` which collapsed
     * both the +0.3 and −0.3 offsets to the same integer column for most
     * positions. Changed to explicit ±0.3 offsets.
     */
    private boolean isOverVoid(Minecraft mc) {
        double px  = mc.thePlayer.posX;
        double pz  = mc.thePlayer.posZ;
        int    baseY = (int) Math.floor(mc.thePlayer.posY) - 1;
        World  world = mc.theWorld;

        double[] xOffsets = { -0.3, 0.3 };
        double[] zOffsets = { -0.3, 0.3 };

        for (double dx : xOffsets) {
            for (double dz : zOffsets) {
                int cx = (int) Math.floor(px + dx);
                int cz = (int) Math.floor(pz + dz);
                boolean columnClear = true;
                for (int y = baseY; y > 0; y--) {
                    if (!(world.getBlockState(new BlockPos(cx, y, cz)).getBlock() instanceof BlockAir)) {
                        columnClear = false;
                        break;
                    }
                }
                if (!columnClear) return false; // at least one column has ground
            }
        }
        return true; // all four corners are void
    }

    // ─── Block slot selection ─────────────────────────────────────────────────

    private int findBlockSlot(Minecraft mc) {
        // BUG FIX: prefer the current slot if it's already a valid block,
        // to minimise unnecessary slot-switch packets (suspicious on AC).
        int current = mc.thePlayer.inventory.currentItem;
        if (isUsableStack(mc.thePlayer.inventory.getStackInSlot(current))) return current;

        for (int i = 0; i < 9; i++) {
            if (i == current) continue; // already checked
            if (isUsableStack(mc.thePlayer.inventory.getStackInSlot(i))) return i;
        }
        return -1;
    }

    private boolean isUsableStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (!(stack.getItem() instanceof ItemBlock)) return false;
        if (stack.stackSize <= 0) return false; // BUG FIX: 0-size stacks should be excluded
        Item item = stack.getItem();
        switch (filterMode) {
            case BLACKLIST: return !filterList.contains(item);
            case WHITELIST: return filterList.isEmpty() || filterList.contains(item);
            default:        return false;
        }
    }

    // ─── Angle & placement ───────────────────────────────────────────────────

    private void aimDown(Minecraft mc) {
        if (useSilentAim && ClutchMod.SILENT_AIM != null) {
            // Aim at the block we're about to place
            Vec3 target = new Vec3(mc.thePlayer.posX,
                                   mc.thePlayer.posY - 1.5,
                                   mc.thePlayer.posZ);
            ClutchMod.SILENT_AIM.aimAt(target);
        } else {
            // BUG FIX: also set yaw to straight down by not changing it,
            // and clamp pitch to 90 (was already correct, kept for clarity)
            mc.thePlayer.rotationPitch = 90f;
        }
    }

    /**
     * Send a C08 packet to place a block on the given face of `support`.
     *
     * @param support the existing (non-air) block being clicked
     * @param face    the face of `support` to place on
     */
    private void placeAt(Minecraft mc, BlockPos support, EnumFacing face) {
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || held.stackSize <= 0) return;

        mc.thePlayer.sendQueue.addToSendQueue(
            new C08PacketPlayerBlockPlacement(
                support,
                face.getIndex(),
                held,
                0.5f, 1.0f, 0.5f
            )
        );
        mc.thePlayer.swingItem();
        blocksPlaced++;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    private void startClutch(Minecraft mc) {
        clutching    = true;
        blocksPlaced = 0;
        savedSlot    = mc.thePlayer.inventory.currentItem;
        savedYaw     = mc.thePlayer.rotationYaw;
        savedPitch   = mc.thePlayer.rotationPitch;
    }

    private void finishClutch(Minecraft mc) {
        if (!clutching) return;
        clutching = false;

        if (resetAngle && !useSilentAim) {
            // BUG FIX: don't reset angle when silent aim is active; SilentAim
            // controls server-side rotation independently. Restoring visual yaw
            // mid-silent-aim caused a visible camera snap.
            mc.thePlayer.rotationYaw   = savedYaw;
            mc.thePlayer.rotationPitch = savedPitch;
        }

        if (returnToSlot && savedSlot != -1
                && mc.thePlayer.inventory.currentItem != savedSlot) {
            mc.thePlayer.inventory.currentItem = savedSlot;
            mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(savedSlot));
        }

        if (clutchMoveDelay && blocksPlaced > 3) {
            moveDelayTicks = 6;
        }

        if (ClutchMod.SILENT_AIM != null) ClutchMod.SILENT_AIM.clearOverride();
        jumpCooldown = 0;
    }
}
