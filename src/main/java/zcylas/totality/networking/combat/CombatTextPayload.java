// networking/combat/CombatTextPayload.java
package zcylas.totality.networking.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.client.combat.CombatTextEntry;

public record CombatTextPayload(
        CombatTextEntry.TextType textType,
        Identifier damageTypeId,   // null for CONDITION/HEAL
        float amount,
        String label,              // condition name or "IMMUNE", null otherwise
        double x, double y, double z,
        boolean resisted,
        boolean vulnerable,
        int entityId,
        int attackerEntityId,
        int style                  // presentation band only (0 = default); never gameplay
) implements CustomPacketPayload {

    /** Entity combat text and every pre-existing sender: default presentation. */
    public CombatTextPayload(CombatTextEntry.TextType textType, Identifier damageTypeId, float amount, String label,
                             double x, double y, double z, boolean resisted, boolean vulnerable,
                             int entityId, int attackerEntityId) {
        this(textType, damageTypeId, amount, label, x, y, z, resisted, vulnerable, entityId, attackerEntityId, 0);
    }

    public static final CustomPacketPayload.Type<CombatTextPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "combat_text"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatTextPayload> CODEC =
            StreamCodec.of(CombatTextPayload::encode, CombatTextPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, CombatTextPayload p) {
        buf.writeEnum(p.textType());
        buf.writeBoolean(p.damageTypeId() != null);
        if (p.damageTypeId() != null) buf.writeIdentifier(p.damageTypeId());
        buf.writeFloat(p.amount());
        buf.writeBoolean(p.label() != null);
        if (p.label() != null) buf.writeUtf(p.label());
        buf.writeDouble(p.x());
        buf.writeDouble(p.y());
        buf.writeDouble(p.z());
        buf.writeBoolean(p.resisted());
        buf.writeBoolean(p.vulnerable());
        buf.writeInt(p.entityId());
        buf.writeInt(p.attackerEntityId());
        buf.writeByte(p.style());
    }

    private static CombatTextPayload decode(RegistryFriendlyByteBuf buf) {
        CombatTextEntry.TextType type = buf.readEnum(CombatTextEntry.TextType.class);
        Identifier damageTypeId = buf.readBoolean() ? buf.readIdentifier() : null;
        float amount = buf.readFloat();
        String label = buf.readBoolean() ? buf.readUtf() : null;
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        boolean resisted = buf.readBoolean();
        boolean vulnerable = buf.readBoolean();
        int entityId = buf.readInt();
        int attackerEntityId = buf.readInt();
        int style = buf.readByte();
        return new CombatTextPayload(type, damageTypeId, amount,
                label, x, y, z, resisted, vulnerable, entityId, attackerEntityId, style);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}