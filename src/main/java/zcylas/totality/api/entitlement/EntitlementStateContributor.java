package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.entitlement.requirement.EntitlementDependencyKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Lets a domain system expose state it owns to queries without Entitlement duplicating it
 * (canonical §8.4): discovery level, domain-owned knowledge, ownership, selection, extra
 * authorization paths and domain suspensions. Contributors must be read-only.
 */
public interface EntitlementStateContributor {

    Identifier id();

    boolean appliesTo(Identifier typeId);

    void contribute(EntitlementKey key, EntitlementQueryContext context, Collector collector);

    /** The owner-reported state this contributor reads; the owner calls
     *  {@code EntitlementService#invalidate} with it when that state changes. */
    default Set<EntitlementDependencyKey> dependencies() {
        return Set.of();
    }

    /**
     * Keys of {@code typeId} that the owning API currently knows about for this player — e.g. spells learned
     * through a future Spells API or technologies completed through a Technology API — so bulk queries
     * ({@code accessible}, the display view) include domain-owned content that has no Entitlement fact or
     * grant. Each enumerated key is still decided by the normal query (policy, contributor state,
     * requirements). The owner reports membership changes through {@link #dependencies()}.
     */
    default Set<EntitlementKey> enumerate(Identifier typeId, EntitlementQueryContext context) {
        return Set.of();
    }

    /** Mutable accumulation of everything contributors reported for one key. */
    final class Collector {
        private boolean known;
        private boolean owned;
        private boolean selected;
        private Optional<Identifier> discoveryLevel = Optional.empty();
        private final List<AuthorizationPath> paths = new ArrayList<>();
        private final List<Identifier> suspensionReasons = new ArrayList<>();

        public void known() { known = true; }
        public void owned() { owned = true; }
        public void selected() { selected = true; }
        public void discoveryLevel(Identifier level) { discoveryLevel = Optional.of(level); }
        public void authorize(AuthorizationPath path) { paths.add(path); }
        public void suspend(Identifier reasonCode) { suspensionReasons.add(reasonCode); }

        public boolean isKnown() { return known; }
        public boolean isOwned() { return owned; }
        public boolean isSelected() { return selected; }
        public Optional<Identifier> discoveryLevel() { return discoveryLevel; }
        public List<AuthorizationPath> paths() { return List.copyOf(paths); }
        public List<Identifier> suspensionReasons() { return List.copyOf(suspensionReasons); }
    }
}
