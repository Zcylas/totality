package zcylas.totality.api.entitlement.integration;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.entitlement.EntitlementGrant;
import zcylas.totality.api.entitlement.EntitlementGrantProvider;
import zcylas.totality.api.entitlement.EntitlementKey;
import zcylas.totality.api.entitlement.GrantSourceRef;
import zcylas.totality.api.entitlement.GrantSourceTypes;
import zcylas.totality.api.entitlement.integration.LegacyAbilityMigration.AbilityInfo;
import zcylas.totality.api.entitlement.integration.LegacyAbilityMigration.Classification;
import zcylas.totality.api.entitlement.integration.LegacyAbilityMigration.Outcome;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests of the Totality integration adapters. They avoid {@code AbilityRegistry} (its static
 * initializer needs a bootstrapped game) by exercising the providers' pure projection functions.
 */
class EntitlementIntegrationTest {

    private static Identifier t(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private static final EntitlementKey RAGE = EntitlementKey.of(AbilityEntitlements.ABILITY_TYPE, t("barbarian_rage"));
    private static final EntitlementKey PATH_FEATURE = EntitlementKey.of(AbilityEntitlements.ABILITY_TYPE, t("frenzy"));
    private static final EntitlementKey DARKVISION = EntitlementKey.of(AbilityEntitlements.ABILITY_TYPE, t("darkvision"));

    private static final Map<Identifier, AbilityInfo> REGISTRY = Map.of(
            t("harvest"), new AbilityInfo(false, true, Ability.Source.DEFAULT),
            t("barbarian_rage"), new AbilityInfo(false, false, Ability.Source.CLASS),
            t("viltrumite_physiology"), new AbilityInfo(false, false, Ability.Source.ANCESTRY),
            t("veinminer"), new AbilityInfo(false, false, Ability.Source.MASTERY),
            t("fireball"), new AbilityInfo(true, true, Ability.Source.SPELL),
            t("wish"), new AbilityInfo(true, false, Ability.Source.SPELL),
            t("ancient_technique"), new AbilityInfo(false, false, Ability.Source.DEFAULT));

    private static List<Classification> classify(Set<Identifier> legacy, Set<Identifier> explained, Set<Identifier> processed) {
        return LegacyAbilityMigration.classify(legacy, id -> Optional.ofNullable(REGISTRY.get(id)), explained, processed);
    }

    private static Outcome outcomeOf(List<Classification> list, String path) {
        return list.stream().filter(c -> c.abilityId().equals(t(path))).findFirst().orElseThrow().outcome();
    }

    @Test
    void legacyMigrationReconstructsProvenanceWithoutFabricatingIt() {
        Set<Identifier> legacy = Set.of(t("harvest"), t("barbarian_rage"), t("viltrumite_physiology"), t("veinminer"),
                t("fireball"), t("wish"), t("ancient_technique"), t("deleted_ability"));
        // Current sources: baseline grants Harvest, the Barbarian class grants Rage.
        List<Classification> result = classify(legacy, Set.of(t("harvest"), t("barbarian_rage")), Set.of());

        assertEquals(Outcome.SOURCE_BOUND_EXPLAINED, outcomeOf(result, "harvest"));
        assertEquals(Outcome.SOURCE_BOUND_EXPLAINED, outcomeOf(result, "barbarian_rage"));
        assertEquals(Outcome.STALE_SOURCE_BOUND, outcomeOf(result, "viltrumite_physiology"),
                "recognizable ancestry content no current source grants is not made permanent");
        assertEquals(Outcome.STALE_SOURCE_BOUND, outcomeOf(result, "veinminer"));
        assertEquals(Outcome.IGNORED_DEVELOPMENT_SPELL, outcomeOf(result, "fireball"),
                "default-unlocked development spells never become permanent knowledge");
        assertEquals(Outcome.AMBIGUOUS_SPELL, outcomeOf(result, "wish"));
        assertEquals(Outcome.LEGACY_PERMANENT, outcomeOf(result, "ancient_technique"));
        assertEquals(Outcome.UNKNOWN_QUARANTINED, outcomeOf(result, "deleted_ability"));
    }

    @Test
    void defaultSpellsAreIgnoredEvenWhenDebugAccessCurrentlyGrantsThem() {
        List<Classification> result = classify(Set.of(t("fireball")), Set.of(t("fireball")), Set.of());
        assertEquals(Outcome.IGNORED_DEVELOPMENT_SPELL, outcomeOf(result, "fireball"));
    }

    @Test
    void legacyMigrationIsIdempotentPerId() {
        Set<Identifier> legacy = Set.of(t("ancient_technique"), t("fireball"), t("deleted_ability"));
        List<Classification> first = classify(legacy, Set.of(), Set.of());
        assertEquals(3, first.size());
        // The ledger records every processed id except quarantined unknown ones.
        Set<Identifier> processed = Set.of(t("ancient_technique"), t("fireball"));
        List<Classification> second = classify(legacy, Set.of(), processed);
        assertEquals(List.of(new Classification(t("deleted_ability"), Outcome.UNKNOWN_QUARANTINED)), second,
                "nothing is re-migrated; only still-unresolvable ids are re-examined");
    }

    @Test
    void classGrantsCarryIndependentClassAndSubclassProvenance() {
        Identifier testClass = t("entitlement_test_class");
        Identifier berserker = t("entitlement_test_path");
        ClassEntitlementGrantProvider.registerClassRule(testClass, (level, subclass, grants) -> {
            if (level >= 1) grants.fromClass(RAGE);
            if (level >= 3) grants.fromSubclass(PATH_FEATURE);
        });

        EntitlementGrantProvider.Collector classless = new EntitlementGrantProvider.Collector(ClassEntitlementGrantProvider.ID);
        ClassEntitlementGrantProvider.collect(Map.of(), Map.of(), classless);
        assertTrue(classless.grants().isEmpty(), "a classless character simply has no class grants");

        EntitlementGrantProvider.Collector level1 = new EntitlementGrantProvider.Collector(ClassEntitlementGrantProvider.ID);
        ClassEntitlementGrantProvider.collect(Map.of(testClass, 1), Map.of(), level1);
        assertEquals(List.of(GrantSourceRef.of(GrantSourceTypes.CLASS, testClass)),
                level1.grants().stream().map(EntitlementGrant::source).toList());

        EntitlementGrantProvider.Collector level3 = new EntitlementGrantProvider.Collector(ClassEntitlementGrantProvider.ID);
        ClassEntitlementGrantProvider.collect(Map.of(testClass, 3), Map.of(testClass, berserker), level3);
        assertEquals(Set.of(GrantSourceRef.of(GrantSourceTypes.CLASS, testClass), GrantSourceRef.of(GrantSourceTypes.SUBCLASS, berserker)),
                Set.copyOf(level3.grants().stream().map(EntitlementGrant::source).toList()));

        EntitlementGrantProvider.Collector noSubclassYet = new EntitlementGrantProvider.Collector(ClassEntitlementGrantProvider.ID);
        ClassEntitlementGrantProvider.collect(Map.of(testClass, 3), Map.of(), noSubclassYet);
        assertEquals(1, noSubclassYet.grants().size());

        assertThrows(IllegalStateException.class, () -> ClassEntitlementGrantProvider.registerClassRule(testClass, (l, s, g) -> {}));
    }

    @Test
    void speciesGrantsAreAttributedToTheSpeciesSource() {
        Identifier dwarf = t("entitlement_test_dwarf");
        Identifier elf = t("entitlement_test_elf");
        AncestryEntitlementGrantProvider.registerSpeciesGrant(dwarf, DARKVISION);
        AncestryEntitlementGrantProvider.registerSpeciesGrant(elf, DARKVISION);

        EntitlementGrantProvider.Collector asElf = new EntitlementGrantProvider.Collector(AncestryEntitlementGrantProvider.ID);
        AncestryEntitlementGrantProvider.collect(elf, null, asElf);
        EntitlementGrantProvider.Collector asDwarf = new EntitlementGrantProvider.Collector(AncestryEntitlementGrantProvider.ID);
        AncestryEntitlementGrantProvider.collect(dwarf, null, asDwarf);

        EntitlementGrant elfGrant = asElf.grants().get(0);
        EntitlementGrant dwarfGrant = asDwarf.grants().get(0);
        assertEquals(DARKVISION, elfGrant.key());
        assertEquals(DARKVISION, dwarfGrant.key());
        assertNotEquals(elfGrant.grantId(), dwarfGrant.grantId(), "same key, independent provenance");
        assertEquals(GrantSourceRef.of(GrantSourceTypes.SPECIES, dwarf), dwarfGrant.source());
    }

    @Test
    void debugUniversalSpellAccessIsDisabledByDefault() {
        assertFalse(Boolean.getBoolean(DebugSpellAccessProvider.SYSTEM_PROPERTY));
        assertFalse(DebugSpellAccessProvider.isEnabled());
        assertTrue(DebugSpellAccessProvider.SOURCE.isDebug());
    }
}
