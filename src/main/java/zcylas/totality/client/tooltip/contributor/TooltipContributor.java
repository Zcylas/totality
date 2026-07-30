package zcylas.totality.client.tooltip.contributor;

import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.TooltipSectionGroup;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.List;
import java.util.Set;

/**
 * Converts semantic gameplay values into {@link TooltipSection}s for one item stack.
 * Contributors discover applicability themselves — an interface check ({@code instanceof
 * UEItem}), a class check, a data-component check — there is no central "does this item support
 * X" registry beyond the ordered list in {@link TooltipContributorRegistry}. Returning an empty
 * list means "nothing to contribute for this stack," never "this item is ineligible for the
 * whole tooltip" (that decision belongs to the explicit opt-in check, not to contributors).
 */
public interface TooltipContributor {
    List<TooltipSection> contribute(TooltipContext ctx);

    /**
     * Which disclosure levels this contributor has <em>meaningful</em> content for on this
     * stack, independent of the level currently selected. This exists because {@link #contribute}
     * often only emits Details/Technical content when the caller is already viewing that level
     * (e.g. exact energy figures, extra healing-roll detail) — inferring "is Details available"
     * from what {@link #contribute} happened to emit at the <em>current</em> level would miss
     * that entirely. Default: declares nothing extra (most contributors only ever produce
     * {@code DEFAULT}-level content, which needs no declaration).
     */
    default Set<TooltipDisclosureLevel> availableDisclosureLevels(TooltipContext ctx) {
        return Set.of();
    }

    /**
     * Which stable high-level body position this contributor's <em>entire</em> output belongs
     * in, relative to every other contributor. All sections a single {@link #contribute} call
     * emits into the body stay together as one block, in exactly the order emitted — grouping
     * only decides where that whole block sits relative to other contributors' blocks, never
     * reorders sections within it. A future contributor declares this instead of the renderer
     * needing an {@code instanceof}/hardcoded case for it. Default: {@link TooltipSectionGroup#PRIMARY}.
     */
    default TooltipSectionGroup sectionGroup() {
        return TooltipSectionGroup.PRIMARY;
    }
}
