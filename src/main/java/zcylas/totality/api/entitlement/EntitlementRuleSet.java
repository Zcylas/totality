package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.entitlement.requirement.EntitlementRequirement;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Requirement ownership categories of one entitlement (canonical §5.3).
 *
 * @param visibility            failing it returns {@code HIDDEN}, not merely disabled
 * @param acquisition           checked by owning systems before they commit a permanent acquisition;
 *                              also shown as the "how to unlock" explanation while locked
 * @param persistentEligibility checked on every query against durable (permanent) access only: failing it
 *                              makes earned content unavailable without forgetting it
 * @param actionRequirements    per-action use-time requirements (prepare, activate, open_service, ...)
 */
public record EntitlementRuleSet(
        Optional<EntitlementRequirement> visibility,
        Optional<EntitlementRequirement> acquisition,
        Optional<EntitlementRequirement> persistentEligibility,
        Map<Identifier, EntitlementRequirement> actionRequirements
) {

    public static final EntitlementRuleSet EMPTY =
            new EntitlementRuleSet(Optional.empty(), Optional.empty(), Optional.empty(), Map.of());

    public EntitlementRuleSet {
        actionRequirements = Map.copyOf(actionRequirements);
    }

    public EntitlementRuleSet withVisibility(EntitlementRequirement requirement) {
        return new EntitlementRuleSet(Optional.of(requirement), acquisition, persistentEligibility, actionRequirements);
    }

    public EntitlementRuleSet withAcquisition(EntitlementRequirement requirement) {
        return new EntitlementRuleSet(visibility, Optional.of(requirement), persistentEligibility, actionRequirements);
    }

    public EntitlementRuleSet withPersistentEligibility(EntitlementRequirement requirement) {
        return new EntitlementRuleSet(visibility, acquisition, Optional.of(requirement), actionRequirements);
    }

    public EntitlementRuleSet withAction(Identifier actionId, EntitlementRequirement requirement) {
        HashMap<Identifier, EntitlementRequirement> copy = new HashMap<>(actionRequirements);
        copy.put(actionId, requirement);
        return new EntitlementRuleSet(visibility, acquisition, persistentEligibility, copy);
    }
}
