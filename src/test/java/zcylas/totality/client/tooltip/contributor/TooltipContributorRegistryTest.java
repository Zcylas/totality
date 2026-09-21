package zcylas.totality.client.tooltip.contributor;

import org.junit.jupiter.api.Test;
import zcylas.totality.client.tooltip.TooltipSectionGroup;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for the ordered contributor list. Does not call {@code contribute(...)} on any
 * entry — that needs a real {@code ItemStack}, which (like {@code Item}) cannot be constructed
 * under plain JUnit in this repository (see {@code HealingPotionItemContractTest}'s class
 * Javadoc). Constructing the contributor objects themselves is safe: none of them touch a
 * Minecraft registry in their (no-arg) constructors.
 */
class TooltipContributorRegistryTest {

    @Test
    void registryIsNonEmptyAndEveryEntryIsDistinctType() {
        List<TooltipContributor> ordered = TooltipContributorRegistry.ordered();
        assertFalse(ordered.isEmpty());

        Set<Class<?>> types = new HashSet<>();
        for (TooltipContributor contributor : ordered) {
            assertTrue(types.add(contributor.getClass()),
                    "contributor type appears more than once: " + contributor.getClass());
        }
    }

    @Test
    void registryIncludesEveryRepresentativeMigrationContributor() {
        Set<Class<?>> types = TooltipContributorRegistry.ordered().stream()
                .map(TooltipContributor::getClass)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(types.contains(MetadataContributor.class));
        assertTrue(types.contains(EnergyContributor.class));
        assertTrue(types.contains(WeaponContributor.class));
        assertTrue(types.contains(AttunementContributor.class));
        assertTrue(types.contains(WeightContributor.class));
        assertTrue(types.contains(GrimoireContributor.class));
        assertTrue(types.contains(HealingPotionContributor.class));
        assertTrue(types.contains(LegacyExtensionAdapterContributor.class));
        assertTrue(types.contains(ExternalContentContributor.class));
        assertTrue(types.contains(TechnicalInfoContributor.class));
    }

    @Test
    void metadataContributorRunsBeforeAnyDedicatedContributor() {
        List<TooltipContributor> ordered = TooltipContributorRegistry.ordered();
        assertInstanceOf(MetadataContributor.class, ordered.get(0),
                "Metadata (header/rarity/classification/lore) should be assembled first");
    }

    @Test
    void legacyExtensionAdapterRunsAfterEveryDedicatedContributorItIsAFallbackFor() {
        List<TooltipContributor> ordered = TooltipContributorRegistry.ordered();
        int legacyIndex = indexOf(ordered, LegacyExtensionAdapterContributor.class);
        int grimoireIndex = indexOf(ordered, GrimoireContributor.class);
        int attunementIndex = indexOf(ordered, AttunementContributor.class);

        assertTrue(legacyIndex > grimoireIndex, "Legacy adapter must run after GrimoireContributor");
        assertTrue(legacyIndex > attunementIndex, "Legacy adapter must run after AttunementContributor");
    }

    @Test
    void externalAndTechnicalContributorsRunLast() {
        List<TooltipContributor> ordered = TooltipContributorRegistry.ordered();
        assertInstanceOf(TechnicalInfoContributor.class, ordered.get(ordered.size() - 1));
        assertInstanceOf(ExternalContentContributor.class, ordered.get(ordered.size() - 2));
    }

    @Test
    void orderedListIsTheSameInstanceAcrossCalls() {
        // Confirms the registry is a fixed, declared list — not rebuilt/reordered per call.
        assertSame(TooltipContributorRegistry.ordered(), TooltipContributorRegistry.ordered());
    }

    // ── Micro-correction: sectionGroup() proofs against the real contributor instances ──────
    // Direct proof that each contributor's own sectionGroup() override actually returns the
    // declared group — catches exactly the class of bug the final-correction-pass review found:
    // an import present, reports/manifest claiming the override exists, but no actual
    // @Override on the class (silently inheriting TooltipContributor's default PRIMARY).

    @Test
    void attunementContributorSectionGroupIsAttunement() {
        assertEquals(TooltipSectionGroup.ATTUNEMENT, new AttunementContributor().sectionGroup(),
                "AttunementContributor must override sectionGroup() to return ATTUNEMENT, not silently "
                        + "inherit the TooltipContributor default of PRIMARY");
    }

    @Test
    void fuelContributorSectionGroupIsResources() {
        assertEquals(TooltipSectionGroup.RESOURCES, new FuelContributor().sectionGroup());
    }

    @Test
    void weightContributorSectionGroupIsWeight() {
        assertEquals(TooltipSectionGroup.WEIGHT, new WeightContributor().sectionGroup());
    }

    @Test
    void legacyExtensionAdapterContributorSectionGroupIsExternal() {
        assertEquals(TooltipSectionGroup.EXTERNAL, new LegacyExtensionAdapterContributor().sectionGroup());
    }

    @Test
    void externalContentContributorSectionGroupIsExternal() {
        assertEquals(TooltipSectionGroup.EXTERNAL, new ExternalContentContributor().sectionGroup());
    }

    @Test
    void technicalInfoContributorSectionGroupIsTechnical() {
        assertEquals(TooltipSectionGroup.TECHNICAL, new TechnicalInfoContributor().sectionGroup());
    }

    @Test
    void metadataContributorSectionGroupIsLore() {
        assertEquals(TooltipSectionGroup.LORE, new MetadataContributor().sectionGroup());
    }

    @Test
    void contributorsWithNoOverrideKeepTheDefaultPrimaryGroup() {
        // WeaponContributor/EnergyContributor/GrimoireContributor/HealingPotionContributor
        // deliberately rely on TooltipContributor's default rather than an explicit override —
        // confirmed here so a future accidental override isn't silently missed either.
        assertEquals(TooltipSectionGroup.PRIMARY, new WeaponContributor().sectionGroup());
        assertEquals(TooltipSectionGroup.PRIMARY, new EnergyContributor().sectionGroup());
        assertEquals(TooltipSectionGroup.PRIMARY, new GrimoireContributor().sectionGroup());
        assertEquals(TooltipSectionGroup.PRIMARY, new HealingPotionContributor().sectionGroup());
    }

    private static int indexOf(List<TooltipContributor> list, Class<?> type) {
        for (int i = 0; i < list.size(); i++) {
            if (type.isInstance(list.get(i))) return i;
        }
        return -1;
    }
}
