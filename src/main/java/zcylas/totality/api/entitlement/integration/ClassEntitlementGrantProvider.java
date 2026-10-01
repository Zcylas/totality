package zcylas.totality.api.entitlement.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.entitlement.EntitlementGrantProvider;
import zcylas.totality.api.entitlement.EntitlementKey;
import zcylas.totality.api.entitlement.GrantSourceRef;
import zcylas.totality.api.entitlement.GrantSourceTypes;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.PlayerClassComponent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Projects Class-derived grants from {@code PlayerClassComponent} (canonical §9.2). Each class implementation
 * registers its own {@link ClassGrantRule} — the class is authoritative for what it grants at which of its own
 * levels; this provider only reads the player's current classes and stamps provenance. A classless character
 * simply has no class grants, and every class held is evaluated independently (no overall-level formula and
 * no universal class-level cap is assumed here).
 */
public final class ClassEntitlementGrantProvider implements EntitlementGrantProvider {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "class_grants");

    private static final Map<Identifier, ClassGrantRule> RULES = new LinkedHashMap<>();

    /** A class implementation's authoritative grant rule, evaluated against that class's own level. */
    @FunctionalInterface
    public interface ClassGrantRule {
        void collect(int classLevel, @Nullable Identifier subclassId, Grants grants);
    }

    /** Provenance-stamping sink: a grant is attributed to the class itself or to its chosen subclass. */
    public interface Grants {
        void fromClass(EntitlementKey key);

        void fromSubclass(EntitlementKey key);
    }

    public static void registerClassRule(Identifier classId, ClassGrantRule rule) {
        if (RULES.putIfAbsent(classId, rule) != null) {
            throw new IllegalStateException("Duplicate class entitlement rule for " + classId);
        }
    }

    @Override
    public Identifier providerId() {
        return ID;
    }

    @Override
    public Set<Identifier> sourceTypeIds() {
        return Set.of(GrantSourceTypes.CLASS, GrantSourceTypes.SUBCLASS);
    }

    @Override
    public void collectGrants(ServerPlayer player, Collector collector) {
        PlayerClassComponent classes = ClassComponents.get(player);
        collect(classes.getAllClassLevels(), classes.getAllSubclassIds(), collector);
    }

    /** Pure projection from class state, shared by {@link #collectGrants} and tests. */
    static void collect(Map<Identifier, Integer> classLevels, Map<Identifier, Identifier> subclassIds, Collector collector) {
        classLevels.forEach((classId, level) -> {
            ClassGrantRule rule = RULES.get(classId);
            if (rule == null) return;
            Identifier subclassId = subclassIds.get(classId);
            rule.collect(level, subclassId, new Grants() {
                @Override
                public void fromClass(EntitlementKey key) {
                    collector.grant(key, GrantSourceRef.of(GrantSourceTypes.CLASS, classId));
                }

                @Override
                public void fromSubclass(EntitlementKey key) {
                    if (subclassId != null) collector.grant(key, GrantSourceRef.of(GrantSourceTypes.SUBCLASS, subclassId));
                }
            });
        });
    }
}
