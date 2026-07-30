package zcylas.totality.client.tooltip.section;

import net.minecraft.network.chat.Component;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.TooltipVisibility;

import java.util.List;

/**
 * A small, closed set of semantic section kinds a contributor can emit. Sections hold semantic
 * values — a label, a value, a color meaningful to the gameplay data, a disclosure/visibility
 * gate — never pixel coordinates, scissor regions, viewport height, scroll offsets, or final
 * panel width. Those all belong to the renderer.
 *
 * The Totality footer (credit line + dynamic disclosure hints) is deliberately NOT a section
 * here — it is renderer-owned chrome, always drawn last, so it can never be suppressed,
 * duplicated, or reordered by a contributor.
 */
public sealed interface TooltipSection {

    /** Identification-based visibility gate. Most sections are {@link TooltipVisibility#ALWAYS}. */
    TooltipVisibility visibility();

    /** Minimum disclosure level required to show this section. Most sections are {@code DEFAULT}. */
    TooltipDisclosureLevel minDisclosure();

    record Header(Component title, TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure)
            implements TooltipSection {
        public Header(Component title) {
            this(title, TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }
    }

    record RarityBadge(ItemRarity rarity, TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure)
            implements TooltipSection {
        public RarityBadge(ItemRarity rarity) {
            this(rarity, TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }

        public RarityBadge withVisibility(TooltipVisibility visibility) {
            return new RarityBadge(rarity, visibility, minDisclosure);
        }
    }

    record ClassificationBadges(List<ItemType> classifications, TooltipVisibility visibility,
                                TooltipDisclosureLevel minDisclosure) implements TooltipSection {
        public ClassificationBadges(List<ItemType> classifications) {
            this(List.copyOf(classifications), TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }
    }

    record Heading(String label, TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure)
            implements TooltipSection {
        public Heading(String label) {
            this(label, TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }
    }

    /** One label/value row, e.g. weight, an AC bonus, a single technical fact. */
    record StatRow(String iconGlyph, int iconColor, String label, String value, int valueColor,
                   TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure) implements TooltipSection {
        public StatRow(String iconGlyph, int iconColor, String label, String value, int valueColor) {
            this(iconGlyph, iconColor, label, value, valueColor, TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }

        public StatRow withVisibility(TooltipVisibility visibility) {
            return new StatRow(iconGlyph, iconColor, label, value, valueColor, visibility, minDisclosure);
        }

        public StatRow withMinDisclosure(TooltipDisclosureLevel minDisclosure) {
            return new StatRow(iconGlyph, iconColor, label, value, valueColor, visibility, minDisclosure);
        }
    }

    /** One row inside a boxed {@link StatBlock} — same shape as {@link StatRow} without its own gating. */
    record StatLine(String iconGlyph, int iconColor, String label, String value, int valueColor) {}

    /** A boxed group of stat lines, e.g. the energy box (current/capacity + I/O). */
    record StatBlock(List<StatLine> lines, TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure)
            implements TooltipSection {
        public StatBlock(List<StatLine> lines) {
            this(List.copyOf(lines), TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }
    }

    /** A single fill bar, e.g. battery charge. Fraction is clamped [0,1] by the renderer. */
    record ProgressBar(float fraction, int color, TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure)
            implements TooltipSection {
        public ProgressBar(float fraction, int color) {
            this(fraction, color, TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }
    }

    /** Flowing badges, e.g. weapon properties (Finesse, Thrown, Light...). Wraps onto new rows as needed. */
    record PropertyBadges(List<String> labels, TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure)
            implements TooltipSection {
        public PropertyBadges(List<String> labels) {
            this(List.copyOf(labels), TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }
    }

    record Description(String text, TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure)
            implements TooltipSection {
        public Description(String text) {
            this(text, TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }

        public Description withVisibility(TooltipVisibility visibility) {
            return new Description(text, visibility, minDisclosure);
        }
    }

    /** A requirement or warning line, e.g. "Requires Attunement". */
    record Requirement(String text, boolean warning, TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure)
            implements TooltipSection {
        public Requirement(String text, boolean warning) {
            this(text, warning, TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }

        public Requirement withVisibility(TooltipVisibility visibility) {
            return new Requirement(text, warning, visibility, minDisclosure);
        }
    }

    /** Preserved vanilla/third-party tooltip lines the mixin captured but the panel doesn't own. */
    record ExternalContent(List<Component> lines, TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure)
            implements TooltipSection {
        public ExternalContent(List<Component> lines) {
            this(List.copyOf(lines), TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }

        public ExternalContent withVisibility(TooltipVisibility visibility) {
            return new ExternalContent(lines, visibility, minDisclosure);
        }
    }

    /** Registry id / component count / other diagnostics — Technical disclosure only. */
    record TechnicalInfo(List<String> lines, TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure)
            implements TooltipSection {
        public TechnicalInfo(List<String> lines) {
            this(List.copyOf(lines), TooltipVisibility.TECHNICAL, TooltipDisclosureLevel.TECHNICAL);
        }
    }
}
