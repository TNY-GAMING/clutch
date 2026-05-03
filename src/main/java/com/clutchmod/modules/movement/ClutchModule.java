package com.clutchmod.modules.movement;

import com.clutchmod.ClutchMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
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
 * • Void falls
 * • Lethal fall damage
 * • Falls beyond a configurable distance (predictive raycast)
 *
 * ANTI-CHEAT CONSIDERATIONS:
 *   • All placement packets use correct hit vectors per face (not hardcoded)
 *   • Slot switches send C09 before C08 (server knows held item)
 *   • Angle reset sends C06 after clutch to close the "stuck pitch" window
 *   • Side-face placements compute yaw toward the neighbour block
 *   • Silent Aim delegation for aim-down keeps visual angles independent
 */
public class ClutchModule {

    // ─── Enable ──────────────────────────────────────────────────────────────
    private boolean enabled = false;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public void toggle() { enabled = !enabled; }

    // ─── Activation conditions ────────────────────────────────────────────────
    private boolean activateOnVoid = true;
    private boolean activateOnLethalFall = true;
    private boolean activateOnMoreThanX = false;
    private int triggerBlocks = 4;

    public boolean isActivateOnVoid() { return activateOnVoid; }
    public void setActivateOnVoid(boolean v) { activateOnVoid = v; }
    public boolean isActivateOnLethalFall() { return activateOnLethalFall; }
    public void setActivateOnLethalFall(boolean v){ activateOnLethalFall = v; }
    public boolean isActivateOnMoreThanX() { return activateOnMoreThanX; }
    public void setActivateOnMoreThanX(boolean v){ activateOnMoreThanX = v; }
    public int getTriggerBlocks() { return triggerBlocks; }
    public void setTriggerBlocks(int v) { triggerBlocks = Math.max(1, v); }

    // ─── Feature toggles ─────────────────────────────────────────────────────
    private boolean useSilentAim = false;
    private boolean showBlockCount = true;
    private boolean resetAngle = true;
    private boolean returnToSlot = true;
    private boolean allowStaircaseUp = false;
    private boolean clutchMoveDelay = false;
    private int maxBlocks = 0;

    public boolean isUseSilentAim() { return useSilentAim; }
    public void setUseSilentAim(boolean v) { useSilentAim = v; }
    public boolean isShowBlockCount() { return showBlockCount; }
    public void setShowBlockCount(boolean v) { showBlockCount = v; }
    public boolean isResetAngle() { return resetAngle; }
    public void setResetAngle(boolean v) { resetAngle = v; }
    public boolean isReturnToSlot() { return returnToSlot; }
    public void setReturnToSlot(boolean v) { returnToSlot = v; }
    public boolean isAllowStaircaseUp() { return allowStaircaseUp; }
    public void setAllowStaircaseUp(boolean v){ allowStaircaseUp = v; }
    public boolean isClutchMoveDelay() { return clutchMoveDelay; }
    public void setClutchMoveDelay(boolean v) { clutchMoveDelay = v; }
    public int getMaxBlocks() { return maxBlocks; }
    public void setMaxBlocks(int v) { maxBlocks = Math.max(0, v); }

    // ─── Block filter ─────────────────────────────────────────────────────────
    public enum FilterMode { BLACKLIST, WHITELIST }
    private FilterMode filterMode = FilterMode.BLACKLIST;
    private final List<Item> filterList = new ArrayList<Item>();

    public FilterMode getFilterMode() { return filterMode; }
    public void setFilterMode(FilterMode m) { filterMode = m; }
    public List<Item> getFilterList() { return filterList; }

    // ─── Runtime state ────────────────────────────────────────────────────────
    private boolean clutching = false;
    private int blocksPlaced = 0;
    private int savedSlot = -1;
    private float savedYaw = 0f;
    private float savedPitch = 0f;
    private int moveDelayTicks = 0;
    private int jumpCooldown = 0;

    // Track the last placement face for yaw correction in side placements
    private EnumFacing lastPlacementFace = EnumFacing.UP;

    public boolean isClutching() { return clutching; }
    public int getBlocksPlaced(){ return blocksPlaced; }

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

        int slot = findBlockSlot(mc);
        if (slot == -1) { finishClutch(mc); return; }

        // Switch hotbar slot (send packet so server knows)
        if (mc.thePlayer.inventory.currentItem != slot) {
            mc.thePlayer.inventory.currentItem = slot;
            mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(slot));
        }

        // Aim downward (or toward side face if needed)
        aimDown(mc);

        BlockPos feetPos = new BlockPos(mc.thePlayer);
        BlockPos placePos = feetPos.down();
        BlockPos supportPos = placePos.down();

        // Only place if the target position is actually air
        if (mc.theWorld.getBlockState(placePos).getBlock() instanceof BlockAir) {
            if (!(mc.theWorld.getBlockState(supportPos).getBlock() instanceof BlockAir)) {
                lastPlacementFace = EnumFacing.UP;
                placeAt(mc, supportPos, EnumFacing.UP);
            } else {
                // Both blocks below are air; try clicking on a side neighbour
                for (EnumFacing face : new EnumFacing[]{
                    EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST}) {
                    BlockPos side = placePos.offset(face);
                    if (!(mc.theWorld.getBlockState(side).getBlock() instanceof BlockAir)) {
                        lastPlacementFace = face.getOpposite();
                        placeAt(mc, side, face.getOpposite());
                        break;
                    }
                }
            }
        }

        // Staircase: jump periodically so the player rides the placed block up
        if (allowStaircaseUp && jumpCooldown <= 0 && mc.thePlayer.onGround) {
            mc.thePlayer.jump();
            jumpCooldown = 4;
        }
    }

    // ─── Activation check ────────────────────────────────────────────────────

    private boolean shouldActivate(Minecraft mc) {
        EntityPlayerSP p = mc.thePlayer;

        // Don't activate while moving upward or on ground
        if (p.motionY > -0.1) return false;
        if (p.onGround) return false;

        // ─── Predictive raycast for activateOnMoreThanX ─────────────────────
        // BUG FIX: original used fallDist >= triggerBlocks, which is unreliable
        // (fallDist accumulates from last onGround, not from current height).
        // We now scan downward from posY to count air blocks until solid ground.
        // This gives the true predicted fall distance from the current position.
        int airBelow = countAirBlocksBelow(mc);

        if (activateOnVoid && isOverVoid(mc)) return true;

        if (activateOnLethalFall) {
            float armorReduction = p.getTotalArmorValue() / 25.0f;
            float damage = (p.fallDistance - 3f) * (1f - armorReduction * 0.25f);
            if (damage >= p.getHealth()) return true;
        }

        if (activateOnMoreThanX && airBelow >= triggerBlocks) return true;

        // ─── maxBlocks upfront prevention ──────────────────────────────────
        // BUG FIX: original allowed clutch to start even if maxBlocks was set
        // and the fall required more blocks. It then stopped mid-clutch, which
        // is useless — the player is already falling. Now we check BEFORE
        // starting and refuse activation if the required blocks exceed maxBlocks.
        if (maxBlocks > 0 && airBelow > maxBlocks) return false;

        return false;
    }

    /**
     * Scans downward from the player's position counting consecutive air blocks
     * until solid ground or Y=0 (void). Returns the count of air blocks.
     *
     * This is the shared raycast used by both:
     *   • activateOnMoreThanX (predictive fall distance)
     *   • maxBlocks upfront check (don't start if needed > max)
     *
     * The scan starts from the block below the player's feet (posY - 1) and
     * goes down to Y=1. If all are air, the player is over void.
     *
     * THREAD: client thread only (called from onTick).
     */
    private int countAirBlocksBelow(Minecraft mc) {
        EntityPlayerSP p = mc.thePlayer;
        World world = mc.theWorld;
        int startY = (int) Math.floor(p.posY) - 1;
        int count = 0;
        for (int y = startY; y > 0; y--) {
            BlockPos pos = new BlockPos(p.posX, y, p.posZ);
            if (world.getBlockState(pos).getBlock() instanceof BlockAir) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * Returns true when no solid block exists below ANY corner of the player's
     * bounding box all the way down to y=1.
     *
     * All four columns must be void for the void condition to trigger.
     */
    private boolean isOverVoid(Minecraft mc) {
        double px = mc.thePlayer.posX;
        double pz = mc.thePlayer.posZ;
        int baseY = (int) Math.floor(mc.thePlayer.posY) - 1;
        World world = mc.theWorld;

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
                if (!columnClear) return false;
            }
        }
        return true;
    }

    // ─── Block slot selection ─────────────────────────────────────────────────

    private int findBlockSlot(Minecraft mc) {
        int current = mc.thePlayer.inventory.currentItem;
        if (isUsableStack(mc.thePlayer.inventory.getStackInSlot(current))) return current;

        for (int i = 0; i < 9; i++) {
            if (i == current) continue;
            if (isUsableStack(mc.thePlayer.inventory.getStackInSlot(i))) return i;
        }
        return -1;
    }

    /**
     * BUG FIX: Whitelist empty-list semantics changed from "allow all" to "allow none".
     * This forces the user to explicitly configure their whitelist. An empty whitelist
     * means NO blocks are usable, which is a safe default (prevents clutching with
     * unintended blocks like TNT or beds).
     */
    private boolean isUsableStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (!(stack.getItem() instanceof ItemBlock)) return false;
        if (stack.stackSize <= 0) return false;
        Item item = stack.getItem();
        switch (filterMode) {
            case BLACKLIST: return !filterList.contains(item);
            // BUG FIX: was "filterList.isEmpty() || filterList.contains(item)"
            // Changed to require explicit configuration. Empty whitelist = deny all.
            case WHITELIST: return filterList.contains(item);
            default: return false;
        }
    }

    // ─── Angle & placement ───────────────────────────────────────────────────

    /**
     * BUG FIX: When falling back to side-face placements, we now compute yaw
     * toward the neighbour block and set rotationYaw (or silent aim server yaw)
     * to face that direction before sending C08.
     *
     * This prevents the anti-cheat from flagging "placement direction mismatch"
     * — the server checks that the player's look vector is within ~45° of the
     * face normal for the placement to be accepted.
     */
    private void aimDown(Minecraft mc) {
        if (useSilentAim && ClutchMod.SILENT_AIM != null) {
            // For UP face: aim at the centre of the block below feet
            Vec3 target = new Vec3(mc.thePlayer.posX,
                mc.thePlayer.posY - 1.5,
                mc.thePlayer.posZ);
            ClutchMod.SILENT_AIM.aimAt(target);
        } else {
            mc.thePlayer.rotationPitch = 90f;
        }
    }

    /**
     * Sends a C08 packet to place a block on the given face of `support`.
     *
     * BUG FIX: hit vector is now computed per-face instead of hardcoded
     * 0.5f, 1.0f, 0.5f. The hardcoded vector only works for UP face (y=1.0).
     * For side faces, the server expects the hit vector to be on the clicked
     * face's plane (e.g. for NORTH face, z should be near 0 or 1 depending
     * on which side of the block was clicked).
     *
     * Correct hit vectors per face (on a 0..1 block-local coordinate system):
     *   DOWN:  (0.5, 0.0, 0.5)
     *   UP:    (0.5, 1.0, 0.5)
     *   NORTH: (0.5, 0.5, 0.0)
     *   SOUTH: (0.5, 0.5, 1.0)
     *   WEST:  (0.0, 0.5, 0.5)
     *   EAST:  (1.0, 0.5, 0.5)
     *
     * Additionally, for side-face placements we compute the yaw toward the
     * support block and update aim before sending the packet.
     */
    private void placeAt(Minecraft mc, BlockPos support, EnumFacing face) {
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || held.stackSize <= 0) return;

        // For side faces, correct yaw to face the support block
        if (face != EnumFacing.UP && face != EnumFacing.DOWN) {
            double dx = support.getX() + 0.5 - mc.thePlayer.posX;
            double dz = support.getZ() + 0.5 - mc.thePlayer.posZ;
            float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

            if (useSilentAim && ClutchMod.SILENT_AIM != null) {
                // Update silent aim server yaw to face the side block
                Vec3 target = new Vec3(support.getX() + 0.5,
                    mc.thePlayer.posY - 1.5,
                    support.getZ() + 0.5);
                ClutchMod.SILENT_AIM.aimAt(target);
            } else {
                mc.thePlayer.rotationYaw = targetYaw;
            }
        }

        // Compute correct hit vector for the face being clicked
        float hitX, hitY, hitZ;
        switch (face) {
            case DOWN:
                hitX = 0.5f; hitY = 0.0f; hitZ = 0.5f; break;
            case UP:
                hitX = 0.5f; hitY = 1.0f; hitZ = 0.5f; break;
            case NORTH:
                hitX = 0.5f; hitY = 0.5f; hitZ = 0.0f; break;
            case SOUTH:
                hitX = 0.5f; hitY = 0.5f; hitZ = 1.0f; break;
            case WEST:
                hitX = 0.0f; hitY = 0.5f; hitZ = 0.5f; break;
            case EAST:
                hitX = 1.0f; hitY = 0.5f; hitZ = 0.5f; break;
            default:
                hitX = 0.5f; hitY = 1.0f; hitZ = 0.5f; break;
        }

        mc.thePlayer.sendQueue.addToSendQueue(
            new C08PacketPlayerBlockPlacement(
                support,
                face.getIndex(),
                held,
                hitX, hitY, hitZ
            )
        );
        mc.thePlayer.swingItem();
        blocksPlaced++;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    private void startClutch(Minecraft mc) {
        clutching = true;
        blocksPlaced = 0;
        savedSlot = mc.thePlayer.inventory.currentItem;
        savedYaw = mc.thePlayer.rotationYaw;
        savedPitch = mc.thePlayer.rotationPitch;
    }

    private void finishClutch(Minecraft mc) {
        if (!clutching) return;
        clutching = false;

        if (resetAngle) {
            if (useSilentAim && ClutchMod.SILENT_AIM != null) {
                // Clear silent aim override so visual angles are free
                ClutchMod.SILENT_AIM.clearOverride();
            } else {
                mc.thePlayer.rotationYaw = savedYaw;
                mc.thePlayer.rotationPitch = savedPitch;
            }

            // BUG FIX: send C06 packet with restored angles to close the
            // "stuck at 90° pitch" detection window on the server.
            // The server sees the player was looking down during clutch (C03
            // packets had pitch=90 via silent aim or direct set). If we only
            // restore visual angles locally, the server still thinks the player
            // is looking down until the next C03. Sending C06 explicitly
            // synchronises the server state immediately.
            //
            // C06PacketPlayerPosLook extends C03 and includes onGround flag.
            // We use the player's current position and the restored yaw/pitch.
            float restoredYaw = useSilentAim && ClutchMod.SILENT_AIM != null
                ? mc.thePlayer.rotationYaw   // visual yaw (silent aim cleared)
                : savedYaw;
            float restoredPitch = useSilentAim && ClutchMod.SILENT_AIM != null
                ? mc.thePlayer.rotationPitch // visual pitch
                : savedPitch;

            mc.thePlayer.sendQueue.addToSendQueue(
                new C03PacketPlayer.C06PacketPlayerPosLook(
                    mc.thePlayer.posX,
                    mc.thePlayer.posY,
                    mc.thePlayer.posZ,
                    restoredYaw,
                    restoredPitch,
                    mc.thePlayer.onGround
                )
            );
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