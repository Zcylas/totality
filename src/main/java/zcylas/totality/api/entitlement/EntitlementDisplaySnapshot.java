package zcylas.totality.api.entitlement;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.entitlement.requirement.DisplayRequirementSummary;
import zcylas.totality.api.entitlement.requirement.RequirementEvaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The only form of entitlement state that reaches a client (canonical §7.3): a display state, a reason,
 * and disclosure-filtered requirement summaries. Hidden entries are never produced, and the raw ledger,
 * grant provenance and hidden condition ids are never included. Advisory only — the server revalidates.
 */
public record EntitlementDisplaySnapshot(
        EntitlementKey key,
        EntitlementDisplayState state,
        Identifier primaryReasonCode,
        boolean selectable,
        boolean temporary,
        boolean debugOnly,
        List<DisplayRequirementSummary> requirements,
        long revision
) {

    public EntitlementDisplaySnapshot {
        requirements = List.copyOf(requirements);
    }

    /** Empty for a hidden decision — hidden content produces nothing the client could see. */
    public static Optional<EntitlementDisplaySnapshot> from(EntitlementDecision decision) {
        if (decision.kind() == EntitlementDecision.Kind.HIDDEN) return Optional.empty();
        List<DisplayRequirementSummary> requirements = new ArrayList<>();
        for (RequirementEvaluation failure : decision.failures()) requirements.addAll(failure.displayableFailures());
        return Optional.of(new EntitlementDisplaySnapshot(decision.key(), decision.displayState(),
                decision.primaryReasonCode(), decision.allowed(), decision.snapshot().temporarilyAccessible(),
                decision.snapshot().debugOnly(), requirements, decision.playerEntitlementRevision()));
    }

    public void write(FriendlyByteBuf buf) {
        key.write(buf);
        buf.writeEnum(state);
        buf.writeIdentifier(primaryReasonCode);
        buf.writeBoolean(selectable);
        buf.writeBoolean(temporary);
        buf.writeBoolean(debugOnly);
        buf.writeVarInt(requirements.size());
        for (DisplayRequirementSummary requirement : requirements) requirement.write(buf);
        buf.writeVarLong(revision);
    }

    public static EntitlementDisplaySnapshot read(FriendlyByteBuf buf) {
        EntitlementKey key = EntitlementKey.read(buf);
        EntitlementDisplayState state = buf.readEnum(EntitlementDisplayState.class);
        Identifier reason = buf.readIdentifier();
        boolean selectable = buf.readBoolean();
        boolean temporary = buf.readBoolean();
        boolean debugOnly = buf.readBoolean();
        int count = buf.readVarInt();
        List<DisplayRequirementSummary> requirements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) requirements.add(DisplayRequirementSummary.read(buf));
        return new EntitlementDisplaySnapshot(key, state, reason, selectable, temporary, debugOnly, requirements, buf.readVarLong());
    }

    /** Writes a whole view: revision, then entries. */
    public static void writeView(FriendlyByteBuf buf, long revision, List<EntitlementDisplaySnapshot> entries) {
        buf.writeVarLong(revision);
        buf.writeVarInt(entries.size());
        for (EntitlementDisplaySnapshot entry : entries) entry.write(buf);
    }

    public static List<EntitlementDisplaySnapshot> readViewEntries(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<EntitlementDisplaySnapshot> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(read(buf));
        return entries;
    }
}
