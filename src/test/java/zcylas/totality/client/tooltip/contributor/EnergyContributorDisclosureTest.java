package zcylas.totality.client.tooltip.contributor;

import org.junit.jupiter.api.Test;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.client.tooltip.TooltipDisclosureLevel.*;

/** The Energy section's I/O Rate disclosure: hidden normally, revealed by SHIFT directly beneath the figures. */
class EnergyContributorDisclosureTest {

    /** A Copper Battery: 12,345 / 48,000 UE, 32 / 32 UE/t, inactive, default (SHIFT-only) rate presentation. */
    private static List<TooltipSection> copperBattery(TooltipDisclosureLevel disclosure) {
        return visible(EnergyContributor.sections(12_345, 48_000, 32, 32, false, false, disclosure), disclosure);
    }

    /** The renderer's own disclosure filter ({@code disclosure.includes(section.minDisclosure())}). */
    private static List<TooltipSection> visible(List<TooltipSection> sections, TooltipDisclosureLevel disclosure) {
        return sections.stream().filter(s -> disclosure.includes(s.minDisclosure())).toList();
    }

    private static boolean isIoRate(TooltipSection s) {
        return s instanceof TooltipSection.StatRow r && r.label().equals("I/O Rate");
    }

    @Test
    void theNormalEnergySectionShowsGaugeAndStatusButNoIoRate() {
        List<TooltipSection> normal = copperBattery(DEFAULT);
        assertEquals(2, normal.size());
        assertInstanceOf(TooltipSection.ResourceGauge.class, normal.get(0));
        assertEquals("12.3k / 48k UE (25%)", ((TooltipSection.ResourceGauge) normal.get(0)).figures());
        assertEquals("Inactive", ((TooltipSection.Requirement) normal.get(1)).text());
        assertTrue(normal.stream().noneMatch(EnergyContributorDisclosureTest::isIoRate));
    }

    @Test
    void shiftRevealsTheIoRateDirectlyBeneathTheFiguresWithItsIconValuesAndUnits() {
        List<TooltipSection> shift = copperBattery(DETAILS);
        assertEquals(3, shift.size());
        assertInstanceOf(TooltipSection.ResourceGauge.class, shift.get(0));
        TooltipSection.StatRow io = assertInstanceOf(TooltipSection.StatRow.class, shift.get(1));
        assertEquals("I/O Rate", io.label());
        assertEquals("32 / 32 UE/t", io.value());
        assertEquals(zcylas.totality.client.tooltip.TotalityIcons.ENERGY, io.iconGlyph());
        assertInstanceOf(TooltipSection.Requirement.class, shift.get(2), "status stays last");
    }

    @Test
    void ctrlAloneDoesNotRevealTheIoRateAndShiftPlusCtrlShowsItExactlyOnce() {
        assertTrue(copperBattery(TECHNICAL).stream().noneMatch(EnergyContributorDisclosureTest::isIoRate),
                "CTRL is not the key for I/O information");
        List<TooltipSection> both = copperBattery(DETAILS_AND_TECHNICAL);
        assertEquals(1, both.stream().filter(EnergyContributorDisclosureTest::isIoRate).count());
        assertEquals(both.subList(0, 3).stream().map(Object::getClass).toList(),
                copperBattery(DETAILS).stream().map(Object::getClass).toList(), "same order as SHIFT alone");
    }

    @Test
    void anItemFamilyMayStillAuthorAlwaysVisibleRates() {
        List<TooltipSection> normal = visible(EnergyContributor.sections(10, 100, 5, 5, true, null, DEFAULT), DEFAULT);
        assertTrue(isIoRate(normal.get(1)));
    }

    @Test
    void batteriesUseTheDefaultShiftOnlyRatePresentation() throws Exception {
        String battery = Files.readString(Path.of("src/main/java/zcylas/totality/item/energy/BatteryItem.java"));
        assertFalse(battery.contains("showsEnergyRatesByDefault"), "batteries must not opt back into always-visible rates");
        String ueItem = Files.readString(Path.of("src/main/java/zcylas/totality/api/industrial/energy/UEItem.java"));
        assertTrue(ueItem.contains("default boolean showsEnergyRatesByDefault() {\r\n        return false;")
                || ueItem.contains("default boolean showsEnergyRatesByDefault() {\n        return false;"));
    }
}
