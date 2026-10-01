package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Registration of one entitlement type (canonical §2.2). New systems register their own types through
 * {@link EntitlementCatalog#registerType} — there is no central enum to edit.
 *
 * <p>Totality additions beyond the canonical record: {@link #contentResolver()} links the type to the
 * content registry that already owns its ids (so there are never two independently editable ids for the
 * same content, §2.3), {@link #defaultRetention()}, {@link #visibleByDefault()}, {@link #clientDisplayView()}
 * (whether display snapshots of this type are synchronized to the owning client) and
 * {@link #trackAvailability()} (whether availability changes fire events for owning systems).
 */
public final class EntitlementTypeDefinition {

    private final Identifier id;
    private final Identifier ownerSystemId;
    private final Identifier defaultAuthorizationPolicyId;
    private final Set<Identifier> supportedActionIds;
    private final boolean supportsPermanentKnown;
    private final boolean supportsPermanentUnlock;
    private final EntitlementRetentionPolicy defaultRetention;
    private final boolean visibleByDefault;
    private final boolean clientDisplayView;
    private final boolean trackAvailability;
    private final Identifier displayActionId;
    private final @Nullable Predicate<Identifier> contentResolver;

    private EntitlementTypeDefinition(Builder b) {
        this.id = b.id;
        this.ownerSystemId = b.ownerSystemId;
        this.defaultAuthorizationPolicyId = b.policyId;
        this.supportedActionIds = Set.copyOf(b.actions);
        this.supportsPermanentKnown = b.permanentKnown;
        this.supportsPermanentUnlock = b.permanentUnlock;
        this.defaultRetention = b.retention;
        this.visibleByDefault = b.visibleByDefault;
        this.clientDisplayView = b.clientDisplayView;
        this.trackAvailability = b.trackAvailability;
        this.displayActionId = b.displayActionId;
        this.contentResolver = b.contentResolver;
    }

    public static Builder builder(Identifier id, Identifier ownerSystemId) {
        return new Builder(id, ownerSystemId);
    }

    public Identifier id() { return id; }
    public Identifier ownerSystemId() { return ownerSystemId; }
    public Identifier defaultAuthorizationPolicyId() { return defaultAuthorizationPolicyId; }
    public Set<Identifier> supportedActionIds() { return supportedActionIds; }
    public boolean supportsPermanentKnown() { return supportsPermanentKnown; }
    public boolean supportsPermanentUnlock() { return supportsPermanentUnlock; }
    public EntitlementRetentionPolicy defaultRetention() { return defaultRetention; }
    public boolean visibleByDefault() { return visibleByDefault; }
    public boolean clientDisplayView() { return clientDisplayView; }
    public boolean trackAvailability() { return trackAvailability; }
    /** The action whose decision a client display snapshot (and availability tracking) reports. */
    public Identifier displayActionId() { return displayActionId; }
    public @Nullable Predicate<Identifier> contentResolver() { return contentResolver; }

    /** {@code view} is implicitly supported by every type. */
    public boolean supportsAction(Identifier actionId) {
        return EntitlementActions.VIEW.equals(actionId) || supportedActionIds.contains(actionId);
    }

    public boolean supportsFact(PermanentEntitlementFact fact) {
        return fact == PermanentEntitlementFact.KNOWN ? supportsPermanentKnown : supportsPermanentUnlock;
    }

    @Override
    public String toString() {
        return "EntitlementType[" + id + "]";
    }

    public static final class Builder {
        private final Identifier id;
        private final Identifier ownerSystemId;
        private Identifier policyId = EntitlementPolicies.UNLOCK_OR_GRANT;
        private final Set<Identifier> actions = new LinkedHashSet<>();
        private boolean permanentKnown;
        private boolean permanentUnlock;
        private EntitlementRetentionPolicy retention = EntitlementRetentionPolicy.SOURCE_BOUND;
        private boolean visibleByDefault = true;
        private boolean clientDisplayView;
        private boolean trackAvailability;
        private Identifier displayActionId = EntitlementActions.USE;
        private @Nullable Predicate<Identifier> contentResolver;

        private Builder(Identifier id, Identifier ownerSystemId) {
            this.id = Objects.requireNonNull(id);
            this.ownerSystemId = Objects.requireNonNull(ownerSystemId);
        }

        public Builder policy(Identifier policyId) { this.policyId = Objects.requireNonNull(policyId); return this; }
        public Builder actions(Identifier... actionIds) { actions.addAll(Set.of(actionIds)); return this; }
        public Builder permanentKnown() { this.permanentKnown = true; return this; }
        public Builder permanentUnlock() { this.permanentUnlock = true; return this; }
        public Builder retention(EntitlementRetentionPolicy retention) { this.retention = Objects.requireNonNull(retention); return this; }
        public Builder hiddenByDefault() { this.visibleByDefault = false; return this; }
        public Builder clientDisplayView() { this.clientDisplayView = true; return this; }
        public Builder trackAvailability() { this.trackAvailability = true; return this; }
        public Builder displayAction(Identifier actionId) { this.displayActionId = Objects.requireNonNull(actionId); return this; }
        public Builder content(Predicate<Identifier> resolver) { this.contentResolver = Objects.requireNonNull(resolver); return this; }

        public EntitlementTypeDefinition build() {
            return new EntitlementTypeDefinition(this);
        }
    }
}
