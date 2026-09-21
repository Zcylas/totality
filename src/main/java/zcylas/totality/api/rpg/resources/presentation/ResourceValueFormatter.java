package zcylas.totality.api.rpg.resources.presentation;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

/**
 * The one authoritative display-value converter for a given resource. See
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §19.2.
 *
 * Deliberately returns {@code long} display values rather than a rich-text {@code Component}: no
 * current HUD/tooltip consumer in this codebase needs formatter-owned localization/abbreviation —
 * {@code TotalityHudRenderer} and {@code InventoryItemDetail} already build their own plain-text
 * layout around a numeric value (see {@code TotalityHudRenderer.buildText}/{@code formatValue}),
 * and that layout responsibility is explicitly out of Phase 2A's scope. This still satisfies "no
 * consumer hardcodes ×5" (canonical §7.4): the multiplier lives only inside the registered
 * {@link ResourceDisplayConversion}, reached exclusively through this interface.
 */
public interface ResourceValueFormatter {

    Identifier resourceId();

    /** Converts a raw mechanical unit amount (current, maximum, or a delta) to its display value. */
    long toDisplayValue(long mechanicalUnits, long unitScale);

    default long toDisplayCurrent(ResourceSnapshot snapshot) {
        return toDisplayValue(snapshot.currentUnits(), snapshot.unitScale());
    }

    default long toDisplayMaximum(ResourceSnapshot snapshot) {
        return toDisplayValue(snapshot.maximumUnits(), snapshot.unitScale());
    }

    /**
     * Formats a mechanical delta (e.g. Food restoration) rather than an absolute current/maximum.
     * Same conversion math as {@link #toDisplayValue}; a named entry point purely for call-site
     * clarity (canonical §19.9: "Bread restoring 6 mechanical Food displays +30 Food").
     */
    default long toDisplayDelta(long mechanicalDeltaUnits, long unitScale) {
        return toDisplayValue(mechanicalDeltaUnits, unitScale);
    }

    /** The standard formatter: applies a {@link ResourceDisplayConversion} with no other logic. */
    static ResourceValueFormatter ofConversion(Identifier resourceId, ResourceDisplayConversion conversion) {
        return new ResourceValueFormatter() {
            @Override
            public Identifier resourceId() {
                return resourceId;
            }

            @Override
            public long toDisplayValue(long mechanicalUnits, long unitScale) {
                return conversion.convertUnitsToDisplay(mechanicalUnits, unitScale);
            }
        };
    }
}
