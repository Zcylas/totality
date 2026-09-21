package zcylas.totality.client.tooltip;

import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The assembled, ordered set of semantic sections for one tooltip, plus the explicit set of
 * disclosure levels contributors declared as having meaningful content for this stack
 * (independent of what happens to be in {@link #sections()} at the currently-selected level —
 * see {@link zcylas.totality.client.tooltip.contributor.TooltipContributor#availableDisclosureLevels}).
 * Contributors never see this type directly except through the {@link Builder} they append to —
 * ordering, layout, and disclosure filtering are the renderer's job, not the document's.
 */
public record TooltipDocument(List<TooltipSection> sections, Set<TooltipDisclosureLevel> availableLevels) {

    public TooltipDocument {
        sections = List.copyOf(sections);
        availableLevels = Set.copyOf(availableLevels);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<TooltipSection> sections = new ArrayList<>();
        private final EnumSet<TooltipDisclosureLevel> availableLevels = EnumSet.noneOf(TooltipDisclosureLevel.class);

        public Builder add(TooltipSection section) {
            sections.add(section);
            return this;
        }

        public Builder addAll(List<TooltipSection> toAdd) {
            sections.addAll(toAdd);
            return this;
        }

        public Builder addAvailable(Set<TooltipDisclosureLevel> levels) {
            availableLevels.addAll(levels);
            return this;
        }

        public TooltipDocument build() {
            return new TooltipDocument(sections, availableLevels);
        }
    }
}
