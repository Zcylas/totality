package zcylas.totality.networking.notification;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Sent server → client to display a notification in the top-left corner.
 * The client's NotificationManager queues and renders it.
 *
 * @param message  The text to display
 * @param color    ARGB color for the text (e.g. 0xFFFFFFFF for white)
 */
public record SendNotificationPayload(String message, int color)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SendNotificationPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "notification"));

    public static final StreamCodec<FriendlyByteBuf, SendNotificationPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SendNotificationPayload::message,
                    ByteBufCodecs.INT,         SendNotificationPayload::color,
                    SendNotificationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ── Convenience colors ────────────────────────────────────────────────────
    public static final int WHITE   = 0xFFFFFFFF;
    public static final int RED     = 0xFFFF4444;
    public static final int GREEN   = 0xFF44FF44;
    public static final int YELLOW  = 0xFFFFFF44;
    public static final int BLUE    = 0xFF4444FF;
    public static final int GOLD    = 0xFFFFAA00;
    public static final int GRAY    = 0xFFAAAAAA;

    /**
     * Sends a notification to the given player from the server.
     * Import and call this wherever you previously used sendSystemMessage.
     */
    public static void send(net.minecraft.server.level.ServerPlayer player,
                            String message, int color) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player, new SendNotificationPayload(message, color));
    }
}