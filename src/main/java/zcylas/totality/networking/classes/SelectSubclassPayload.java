package zcylas.totality.networking.classes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Sent client → server to apply a subclass choice to a class the player ALREADY owns — the
 * counterpart to {@link SelectClassPayload}'s subclass field, which only ever applies during a
 * player's very first-ever class selection ({@code SelectClassHandler} rejects the request
 * outright once {@code PlayerClassComponent.hasAnyClass()} is true).
 *
 * <p>Reached when a class's subclass-unlock milestone ({@code ClassData
 * .subclassUnlockClassLevel()}) is crossed by ordinary leveling (via {@link AddClassLevelPayload})
 * for a class the player already has one or more levels in — {@code ClassLevelUpRegistry}'s
 * per-class handler sends {@link OpenSubclassSelectionPayload} to force the subclass-selection
 * screen open, and this is the payload its eventual confirmation sends (see {@code
 * ConfirmClassScreen}).
 */
public record SelectSubclassPayload(String classId, String subclassId) implements CustomPacketPayload {

    public static final Type<SelectSubclassPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "select_subclass"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectSubclassPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SelectSubclassPayload::classId,
                    ByteBufCodecs.STRING_UTF8, SelectSubclassPayload::subclassId,
                    SelectSubclassPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
