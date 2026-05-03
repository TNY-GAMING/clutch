package com.clutchmod.mixins;

import com.clutchmod.ModState;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.lang.reflect.Field;

/**
 * Injects server-side yaw/pitch into C03PacketPlayer at the NetworkManager layer.
 *
 * WHY HERE NOT IN EntityPlayerSP.onUpdateWalkingPlayer:
 *   Feather/Essential may also inject into onUpdateWalkingPlayer. Patching at
 *   NetHandlerPlayClient.addToSendQueue catches ALL packets regardless of origin.
 *   This avoids double-application and mixin-order conflicts.
 *
 * SRG NAMES (1.8.9):
 *   C03PacketPlayer.yaw   = field_149476_e
 *   C03PacketPlayer.pitch = field_149473_f
 *   NetHandlerPlayClient.addToSendQueue = func_147297_a
 *   NetworkManager.sendPacket = func_179290_a
 *
 * HARDENING:
 *   The original code used a fragile float-field-index fallback that could silently
 *   corrupt unrelated float fields (e.g. x, y, z coordinates) if SRG lookup failed.
 *   We now use verified SRG names only. If lookup fails, injectionSafe becomes false
 *   and the mixin self-disables with a fatal log message.
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient {

    private static final Logger LOGGER = LogManager.getLogger("ClutchMod/MixinNetHandler");

    /**
     * If false, SRG field lookup failed. The mixin will skip all packet modification
     * to prevent corrupting packets with writes to wrong fields.
     */
    private static final boolean injectionSafe;

    private static final Field YAW_FIELD;
    private static final Field PITCH_FIELD;

    static {
        Field y = findFieldStrict(C03PacketPlayer.class, "field_149476_e", "yaw");
        Field p = findFieldStrict(C03PacketPlayer.class, "field_149473_f", "pitch");

        if (y == null || p == null) {
            LOGGER.fatal("[ClutchMod] CRITICAL: Could not locate C03PacketPlayer yaw/pitch fields. Expected SRG: field_149476_e (yaw), field_149473_f (pitch). Silent Aim packet injection is DISABLED to prevent packet corruption. This usually means the mod was built against wrong mappings.");
            injectionSafe = false;
            YAW_FIELD = null;
            PITCH_FIELD = null;
        } else {
            injectionSafe = true;
            YAW_FIELD = y;
            PITCH_FIELD = p;
            LOGGER.info("[ClutchMod] C03PacketPlayer field injection verified (SRG).");
        }
    }

    @ModifyArg(
        method = "func_147297_a", // addToSendQueue
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkManager;func_179290_a(Lnet/minecraft/network/Packet;)V",
            remap = false
        ),
        index = 0,
        remap = false
    )
    private Packet clutchmod$onSendPacket(Packet packet) {
        // Fast path: skip all checks if injection is unsafe
        if (!injectionSafe) return packet;

        if (ModState.SILENT_AIM != null
            && ModState.SILENT_AIM.hasOverride()
            && packet instanceof C03PacketPlayer) {
            setPacketRotation((C03PacketPlayer) packet,
                ModState.SILENT_AIM.getServerYaw(),
                ModState.SILENT_AIM.getServerPitch());
        }
        return packet;
    }

    private static void setPacketRotation(C03PacketPlayer packet, float yaw, float pitch) {
        try {
            // These fields are never null when injectionSafe is true
            YAW_FIELD.setFloat(packet, yaw);
            PITCH_FIELD.setFloat(packet, pitch);
        } catch (IllegalAccessException e) {
            // Should never happen — fields were set accessible in findFieldStrict
            LOGGER.error("[ClutchMod] IllegalAccessException patching C03PacketPlayer", e);
        }
    }

    /**
     * Looks up a field by SRG name first, then MCP fallback.
     * NEVER falls back to index scanning — that silently corrupts data.
     *
     * @param cls the class to search
     * @param srgName primary SRG name (e.g. field_149476_e)
     * @param mcpName fallback MCP name (e.g. yaw)
     * @return the Field, or null if neither name resolves
     */
    private static Field findFieldStrict(Class<?> cls, String srgName, String mcpName) {
        for (String name : new String[] { srgName, mcpName }) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // Expected in obfuscated environment when using the wrong name
            }
        }
        return null;
    }
}