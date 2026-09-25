package zcylas.totality.client.tooltip.group;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.client.tooltip.group.TooltipGroups.*;

class TooltipGroupOrderingTest {

    @Test
    void withoutClassificationOrOverrideGroupsFollowDefaultPriority() {
        assertEquals(List.of(MINING, COMBAT, PROPERTIES, ENCHANTMENTS),
                TooltipGroupOrdering.order(Set.of(ENCHANTMENTS, PROPERTIES, COMBAT, MINING), List.of(), null));
    }

    @Test
    void theItemsPrimaryClassificationLeads() {
        // A weapon-first axe leads with Combat; a tool-first axe with Mining.
        assertEquals(List.of(COMBAT, MINING, PROPERTIES),
                TooltipGroupOrdering.order(Set.of(MINING, COMBAT, PROPERTIES), List.of(), ItemType.WEAPON));
        assertEquals(List.of(MINING, COMBAT, PROPERTIES),
                TooltipGroupOrdering.order(Set.of(MINING, COMBAT, PROPERTIES), List.of(), ItemType.TOOL));
    }

    @Test
    void aLeadingGroupTheItemHasNoContentForChangesNothing() {
        assertEquals(List.of(MINING, PROPERTIES),
                TooltipGroupOrdering.order(Set.of(PROPERTIES, MINING), List.of(), ItemType.WEAPON));
    }

    @Test
    void anAuthoredOverrideWinsOverClassificationAndPriority() {
        // The unusual magical axe: tool-first, but authored to lead with Magic.
        assertEquals(List.of(MAGIC, MINING, COMBAT, PROPERTIES), TooltipGroupOrdering.order(
                Set.of(MINING, COMBAT, PROPERTIES, MAGIC), List.of(MAGIC.id()), ItemType.TOOL));
        assertEquals(List.of(PROPERTIES, COMBAT, MINING), TooltipGroupOrdering.order(
                Set.of(MINING, COMBAT, PROPERTIES), List.of(PROPERTIES.id(), COMBAT.id()), ItemType.TOOL));
    }

    @Test
    void overrideEntriesForAbsentOrUnknownGroupsAreIgnored() {
        assertEquals(List.of(MINING, COMBAT), TooltipGroupOrdering.order(Set.of(COMBAT, MINING),
                List.of(ENCHANTMENTS.id(), Identifier.fromNamespaceAndPath("x", "unknown")), null));
    }

    @Test
    void equalPrioritiesAreBrokenByIdSoOrderIsDeterministic() {
        TooltipGroup b = new TooltipGroup(Identifier.fromNamespaceAndPath("ordertest", "b"), 50, new TooltipGroupIcon.Glyph("*", "*"), Set.of());
        TooltipGroup a = new TooltipGroup(Identifier.fromNamespaceAndPath("ordertest", "a"), 50, new TooltipGroupIcon.Glyph("*", "*"), Set.of());
        for (int i = 0; i < 5; i++) {
            assertEquals(List.of(a, b), TooltipGroupOrdering.order(i % 2 == 0 ? List.of(b, a) : List.of(a, b), List.of(), null));
        }
    }

    @Test
    void bottomSectionsFollowEveryOrdinaryGroupAndCannotBeMovedByAnOverride() {
        assertEquals(List.of(MAGIC, MINING, REQUIREMENTS, ENERGY, DURABILITY), TooltipGroupOrdering.order(
                Set.of(DURABILITY, ENERGY, REQUIREMENTS, MINING, MAGIC),
                List.of(DURABILITY.id(), ENERGY.id(), MAGIC.id()), ItemType.TOOL));
    }

    @Test
    void requirementsIsTheLastSectionWhenThereIsNoResource() {
        assertEquals(List.of(COMBAT, PROPERTIES, REQUIREMENTS),
                TooltipGroupOrdering.order(Set.of(REQUIREMENTS, PROPERTIES, COMBAT), List.of(), null));
    }

    @Test
    void energyComesBeforeDurability() {
        assertEquals(List.of(ENERGY, DURABILITY), TooltipGroupOrdering.order(Set.of(DURABILITY, ENERGY), List.of(), ItemType.BATTERY));
    }

    @Test
    void everyPresentGroupAppearsExactlyOnce() {
        List<TooltipGroup> ordered = TooltipGroupOrdering.order(Set.of(MINING, COMBAT, MAGIC),
                List.of(MINING.id(), MINING.id()), ItemType.TOOL);
        assertEquals(3, ordered.size());
        assertEquals(Set.of(MINING, COMBAT, MAGIC), Set.copyOf(ordered));
    }
}
