package zcylas.totality.client.tooltip.contributor;

import org.junit.jupiter.api.Test;
import zcylas.totality.client.tooltip.group.TooltipGroups;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which semantic body group each existing contributor's content is shown under. {@code bodyGroup} does not
 * read the context for any of these, so {@code null} is passed (contribute() itself needs a real ItemStack).
 */
class TooltipContributorBodyGroupsTest {

    @Test
    void existingContentIsMigratedIntoSemanticGroups() {
        assertEquals(TooltipGroups.MINING, new MiningToolContributor().bodyGroup(null));
        assertEquals(TooltipGroups.PROPERTIES, new BlockDurabilityContributor().bodyGroup(null));
        assertEquals(TooltipGroups.COMBAT, new WeaponContributor().bodyGroup(null));
        assertEquals(TooltipGroups.ENERGY, new EnergyContributor().bodyGroup(null));
        assertEquals(TooltipGroups.MAGIC, new GrimoireContributor().bodyGroup(null));
        assertEquals(TooltipGroups.EFFECTS, new HealingPotionContributor().bodyGroup(null));
        assertEquals(TooltipGroups.PROPERTIES, new FuelContributor().bodyGroup(null));
        assertEquals(TooltipGroups.DURABILITY, new DurabilityContributor().bodyGroup(null));
        assertEquals(TooltipGroups.REQUIREMENTS, new AttunementContributor().bodyGroup(null));
        assertEquals(TooltipGroups.ENCHANTMENTS, new EnchantmentsContributor().bodyGroup(null));
    }

    @Test
    void loreVanillaAndTechnicalContentStayUnheaded() {
        assertNull(new MetadataContributor().bodyGroup(null));
        assertNull(new ExternalContentContributor().bodyGroup(null));
        assertNull(new LegacyExtensionAdapterContributor().bodyGroup(null));
        assertNull(new TechnicalInfoContributor().bodyGroup(null));
    }

    @Test
    void theWeaponContributorNoLongerEmitsItsOwnHeadingBecauseTheCombatGroupHeadingReplacesIt() throws Exception {
        String source = Files.readString(Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/WeaponContributor.java"));
        assertFalse(source.contains("new TooltipSection.Heading(\"Weapon\")"));
    }

    @Test
    void miningStatIconsEffectiveValuesAndInlineShiftBreakdownsAreUnchanged() throws Exception {
        String source = Files.readString(Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/MiningToolContributor.java"));
        // Existing custom stat icons, not generic glyphs.
        assertTrue(source.contains("new TooltipSection.StatIcon.Item(new ItemStack(Items.IRON_PICKAXE)),\n                \"Mining Damage\""));
        assertTrue(source.contains("new TooltipSection.StatIcon.Item(new ItemStack(Items.SUGAR)),\n                \"Mining Speed\""));
        assertTrue(source.contains("new TooltipSection.StatIcon.Effect(MobEffects.JUMP_BOOST),\n                \"Mining Tier\""));
        // Normal mode shows the EFFECTIVE value of the same breakdown gameplay uses...
        assertTrue(source.contains("MiningStatFormat.integer(damage.value())"));
        assertTrue(source.contains("MiningStatFormat.decimal(speed.value()) + \"/s\""));
        // ...and SHIFT (DETAILS) adds that entry's own breakdown directly after it.
        assertTrue(source.contains("DAMAGE_COLOR));\n        if (details) sections.add(provenance(damage, \"\"));"));
        assertTrue(source.contains("SPEED_COLOR));\n        if (details) sections.add(provenance(speed, \"/s\"));"));
    }
}
