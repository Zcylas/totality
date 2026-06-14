package zcylas.totality.server;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * A stub {@link ServerPlayer} with a no-op packet handler, suitable for
 * triggering block/item interactions from machine automation without a real client.
 *
 * <p>Ported from Collective (Serilum). Original connection assignment was commented out;
 * this version wires it up correctly.</p>
 *
 * <p>Typical uses inside Totality:
 * <ul>
 *   <li>Abyssal Engine — trigger {@code useItemOn} or {@code harvest} as the bound demon.</li>
 *   <li>Storage network auto-crafting — simulate player placement.</li>
 *   <li>Machine output — interact with adjacent inventories.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Create once per machine and cache while it exists:
 * GameProfile profile = new GameProfile(UUID.randomUUID(), "[AbyssalEngine]");
 * TotalityFakePlayer fake = TotalityFakePlayer.create(serverLevel, profile);
 *
 * // Use it like a normal player for interaction purposes:
 * fake.setItemInHand(InteractionHand.MAIN_HAND, tool);
 * BlockHitResult hit = ...;
 * fake.gameMode.useItemOn(fake, serverLevel, tool, InteractionHand.MAIN_HAND, hit);
 * }</pre>
 */
public class TotalityFakePlayer extends ServerPlayer {

    /**
     * Create a fake player in the given level with the given profile.
     * The profile's name is used as the display name; generate a stable UUID
     * if you want a consistent fake-player identity.
     */
    public static TotalityFakePlayer create(ServerLevel level, GameProfile profile) {
        return new TotalityFakePlayer(level, profile);
    }

    /**
     * Convenience factory — creates a fake player named {@code displayName}
     * with a freshly-generated UUID.
     */
    public static TotalityFakePlayer create(ServerLevel level, String displayName) {
        return new TotalityFakePlayer(level, new GameProfile(UUID.randomUUID(), displayName));
    }

    // -----------------------------------------------------------------------

    private TotalityFakePlayer(ServerLevel level, GameProfile profile) {
        super(level.getServer(), level, profile, ClientInformation.createDefault());
        this.connection = new NoopPacketHandler(level.getServer(), this);
    }

    @Override
    public void awardStat(@NotNull Stat<?> stat, int amount) {}

    @Override
    public boolean isInvulnerableTo(@NotNull ServerLevel level, @NotNull DamageSource source) {
        return true;
    }

    @Override
    public boolean canHarmPlayer(@NotNull Player player) {
        return false;
    }

    @Override
    public void die(@NotNull DamageSource source) {}

    @Override
    public void tick() {}

    @Override
    public void updateOptions(@NotNull ClientInformation info) {}


    // -----------------------------------------------------------------------
    // No-op packet handler — silently drops all packets
    // -----------------------------------------------------------------------

    private static final class NoopPacketHandler extends ServerGamePacketListenerImpl {

        private static final net.minecraft.network.Connection DUMMY =
                new net.minecraft.network.Connection(PacketFlow.CLIENTBOUND);

        NoopPacketHandler(MinecraftServer server, ServerPlayer player) {
            super(server, DUMMY, player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override public void tick() {}
        @Override public void resetPosition() {}
        @Override public void disconnect(@NotNull Component msg) {}
        @Override public void send(@NotNull Packet<?> packet) {}

        @Override public void handlePlayerInput(@NotNull ServerboundPlayerInputPacket p) {}
        @Override public void handleMoveVehicle(@NotNull ServerboundMoveVehiclePacket p) {}
        @Override public void handleAcceptTeleportPacket(@NotNull ServerboundAcceptTeleportationPacket p) {}
        @Override public void handleRecipeBookSeenRecipePacket(@NotNull ServerboundRecipeBookSeenRecipePacket p) {}
        @Override public void handleRecipeBookChangeSettingsPacket(@NotNull ServerboundRecipeBookChangeSettingsPacket p) {}
        @Override public void handleSeenAdvancements(@NotNull ServerboundSeenAdvancementsPacket p) {}
        @Override public void handleCustomCommandSuggestions(@NotNull ServerboundCommandSuggestionPacket p) {}
        @Override public void handleSetCommandBlock(@NotNull ServerboundSetCommandBlockPacket p) {}
        @Override public void handleSetCommandMinecart(@NotNull ServerboundSetCommandMinecartPacket p) {}
        @Override public void handleRenameItem(@NotNull ServerboundRenameItemPacket p) {}
        @Override public void handleSetBeaconPacket(@NotNull ServerboundSetBeaconPacket p) {}
        @Override public void handleSetStructureBlock(@NotNull ServerboundSetStructureBlockPacket p) {}
        @Override public void handleSetJigsawBlock(@NotNull ServerboundSetJigsawBlockPacket p) {}
        @Override public void handleJigsawGenerate(@NotNull ServerboundJigsawGeneratePacket p) {}
        @Override public void handleSelectTrade(@NotNull ServerboundSelectTradePacket p) {}
        @Override public void handleEditBook(@NotNull ServerboundEditBookPacket p) {}
        @Override public void handleMovePlayer(@NotNull ServerboundMovePlayerPacket p) {}
        @Override public void teleport(double x, double y, double z, float yaw, float pitch) {}
        @Override public void handlePlayerAction(@NotNull ServerboundPlayerActionPacket p) {}
        @Override public void handleUseItemOn(@NotNull ServerboundUseItemOnPacket p) {}
        @Override public void handleUseItem(@NotNull ServerboundUseItemPacket p) {}
        @Override public void handleTeleportToEntityPacket(@NotNull ServerboundTeleportToEntityPacket p) {}
        @Override public void handleResourcePackResponse(@NotNull ServerboundResourcePackPacket p) {}
        @Override public void handlePaddleBoat(@NotNull ServerboundPaddleBoatPacket p) {}
        @Override public void handleSetCarriedItem(@NotNull ServerboundSetCarriedItemPacket p) {}
        @Override public void handleChat(@NotNull ServerboundChatPacket p) {}
        @Override public void handleAnimate(@NotNull ServerboundSwingPacket p) {}
        @Override public void handlePlayerCommand(@NotNull ServerboundPlayerCommandPacket p) {}
        @Override public void handleInteract(@NotNull ServerboundInteractPacket p) {}
        @Override public void handleClientCommand(@NotNull ServerboundClientCommandPacket p) {}
        @Override public void handleContainerClose(@NotNull ServerboundContainerClosePacket p) {}
        @Override public void handleContainerClick(@NotNull ServerboundContainerClickPacket p) {}
        @Override public void handlePlaceRecipe(@NotNull ServerboundPlaceRecipePacket p) {}
        @Override public void handleContainerButtonClick(@NotNull ServerboundContainerButtonClickPacket p) {}
        @Override public void handleSetCreativeModeSlot(@NotNull ServerboundSetCreativeModeSlotPacket p) {}
        @Override public void handleSignUpdate(@NotNull ServerboundSignUpdatePacket p) {}
        @Override public void handleKeepAlive(@NotNull ServerboundKeepAlivePacket p) {}
        @Override public void handleCustomPayload(@NotNull ServerboundCustomPayloadPacket p) {}
        @Override public void handleClientInformation(@NotNull ServerboundClientInformationPacket p) {}
        @Override public void handlePlayerAbilities(@NotNull ServerboundPlayerAbilitiesPacket p) {}
        @Override public void handleChangeDifficulty(@NotNull ServerboundChangeDifficultyPacket p) {}
        @Override public void handleLockDifficulty(@NotNull ServerboundLockDifficultyPacket p) {}
        @Override public void ackBlockChangesUpTo(int sequence) {}
        @Override public void handleChatCommand(@NotNull ServerboundChatCommandPacket p) {}
        @Override public void handleChatAck(@NotNull ServerboundChatAckPacket p) {}
        @Override public void sendPlayerChatMessage(@NotNull PlayerChatMessage msg, ChatType.@NotNull Bound bound) {}
        @Override public void sendDisguisedChatMessage(@NotNull Component content, ChatType.@NotNull Bound bound) {}
        @Override public void handleChatSessionUpdate(@NotNull ServerboundChatSessionUpdatePacket p) {}
    }
}
