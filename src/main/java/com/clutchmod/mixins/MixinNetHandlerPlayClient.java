package com.clutchmod.mixins;

import com.clutchmod.ClutchMod;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.lang.reflect.Field;

@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient {

    // BUG FIX: original code called findField() on every single packet (60/sec).
    // Cache the reflected fields at class-load time instead.
    private static final Field YAW_FIELD;
    private static final Field PITCH_FIELD;

    static {
        Field y = findField(C03PacketPlayer.class, "yaw",   "field_149624_e");
        Field p = findField(C03PacketPlayer.class, "pitch", "field_149622_f");

        // Fallback: scan float fields by index if name lookup failed
        if (y == null || p == null) {
            Field[] floatFields = new Field[2];
            int idx = 0;
            for (Field f : C03PacketPlayer.class.getDeclaredFields()) {
                if (f.getType() == float.class && idx < 2) {
                    f.setAccessible(true);
                    floatFields[idx++] = f;
                }
            }
            y = floatFields[0];
            p = floatFields[1];
        }

        YAW_FIELD   = y;
        PITCH_FIELD = p;
    }

    @ModifyArg(
        method = "func_147297_a",   // addToSendQueue
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkManager;func_179290_a(Lnet/minecraft/network/Packet;)V",
            remap = false
        ),
        index = 0,
        remap = false
    )
    private Packet<?> clutchmod$onSendPacket(Packet<?> packet) {
        if (ClutchMod.SILENT_AIM != null
                && ClutchMod.SILENT_AIM.hasOverride()
                && packet instanceof C03PacketPlayer) {
            setPacketRotation((C03PacketPlayer) packet,
                    ClutchMod.SILENT_AIM.getServerYaw(),
                    ClutchMod.SILENT_AIM.getServerPitch());
        }
        return packet;
    }

    private static void setPacketRotation(C03PacketPlayer packet, float yaw, float pitch) {
        try {
            if (YAW_FIELD   != null) YAW_FIELD.setFloat(packet, yaw);
            if (PITCH_FIELD != null) PITCH_FIELD.setFloat(packet, pitch);
        } catch (Exception ignored) {}
    }

    private static Field findField(Class<?> cls, String... names) {
        for (String name : names) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
}
