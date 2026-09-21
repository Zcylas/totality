package zcylas.totality.client.tooltip.contributor;

import java.util.List;

/**
 * The single ordered list of contributors the renderer consults for every tooltip. Adding a
 * future Food, Armor, Spell, or Machine contributor means appending an entry here — never
 * editing {@code TotalityTooltipRenderer}'s draw sequence. The renderer does not order individual
 * section kinds on screen: each contributor's own output stays together, in exactly the order
 * that contributor emitted it, and only the resulting whole blocks are ordered relative to each
 * other by the {@link zcylas.totality.client.tooltip.TooltipSectionGroup} each contributor
 * declares via {@link TooltipContributor#sectionGroup()}. This list's own order therefore only
 * matters (1) as the stable tie-breaker between two contributors that declare the same group, and
 * (2) when more than one contributor can emit content for the same stack (e.g. {@link
 * LegacyExtensionAdapterContributor} running after every dedicated contributor it exists as a
 * fallback for) — never as a section-kind draw sequence.
 */
public final class TooltipContributorRegistry {

    private static final List<TooltipContributor> ORDERED = List.of(
            new MetadataContributor(),
            new WeaponContributor(),
            new EnergyContributor(),
            new FuelContributor(),
            new AttunementContributor(),
            new WeightContributor(),
            new GrimoireContributor(),
            new HealingPotionContributor(),
            new LegacyExtensionAdapterContributor(),
            new ExternalContentContributor(),
            new TechnicalInfoContributor()
    );

    public static List<TooltipContributor> ordered() {
        return ORDERED;
    }

    private TooltipContributorRegistry() {}
}
