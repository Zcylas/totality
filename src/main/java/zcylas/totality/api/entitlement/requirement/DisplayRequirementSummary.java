package zcylas.totality.api.entitlement.requirement;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/** A disclosure-filtered requirement explanation that may be sent to the client (canonical §7.3). */
public record DisplayRequirementSummary(Identifier reasonCode, Optional<Identifier> conditionId, String messageKey) {

    public void write(FriendlyByteBuf buf) {
        buf.writeIdentifier(reasonCode);
        buf.writeBoolean(conditionId.isPresent());
        conditionId.ifPresent(buf::writeIdentifier);
        buf.writeUtf(messageKey);
    }

    public static DisplayRequirementSummary read(FriendlyByteBuf buf) {
        Identifier reason = buf.readIdentifier();
        Optional<Identifier> condition = buf.readBoolean() ? Optional.of(buf.readIdentifier()) : Optional.empty();
        return new DisplayRequirementSummary(reason, condition, buf.readUtf());
    }
}
