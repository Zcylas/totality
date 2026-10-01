package zcylas.totality.api.entitlement.requirement;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;

/**
 * Names a piece of external state a condition reads, e.g. {@code (totality:class_level, totality:wizard)}
 * (canonical §5.7). When the owning system reports that state changed, only cached decisions that read
 * it are invalidated — nothing is re-evaluated every tick.
 */
public record EntitlementDependencyKey(Identifier kind, Optional<Identifier> subject) {

    public EntitlementDependencyKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subject, "subject");
    }

    public static EntitlementDependencyKey of(Identifier kind) {
        return new EntitlementDependencyKey(kind, Optional.empty());
    }

    public static EntitlementDependencyKey of(Identifier kind, Identifier subject) {
        return new EntitlementDependencyKey(kind, Optional.of(subject));
    }

    /** A kind-wide key ({@code class_level}) also matches every subject-specific key of the same kind. */
    public boolean matches(EntitlementDependencyKey changed) {
        if (!kind.equals(changed.kind)) return false;
        return subject.isEmpty() || changed.subject.isEmpty() || subject.equals(changed.subject);
    }

    @Override
    public String toString() {
        return kind + subject.map(s -> "/" + s).orElse("");
    }
}
