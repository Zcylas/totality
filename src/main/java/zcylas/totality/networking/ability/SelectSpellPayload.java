package zcylas.totality.networking.ability;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;

/** C2S — spell equivalent of {@link EquipAbilityPayload}, sent when picking a spell in the Spell
 *  radial so the selection persists server-side (survives a disconnect) instead of only living in
 *  the client-only {@code ClientSelectedSpellManager}. */
public record SelectSpellPayload(@Nullable Identifier spellId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SelectSpellPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "select_spell"));

    public static final StreamCodec<FriendlyByteBuf, SelectSpellPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.spellId() != null);
                        if (p.spellId() != null) buf.writeIdentifier(p.spellId());
                    },
                    buf -> new SelectSpellPayload(buf.readBoolean() ? buf.readIdentifier() : null)
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
