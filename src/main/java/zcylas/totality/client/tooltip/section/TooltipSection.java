package zcylas.totality.client.tooltip.section;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.core.rpgutils.rarity.Classification;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.TooltipVisibility;
import zcylas.totality.client.tooltip.group.TooltipGroup;

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

    record ClassificationBadges(List<Classification> entries, TooltipVisibility visibility,
                                TooltipDisclosureLevel minDisclosure) implements TooltipSection {
        public ClassificationBadges {
            entries = List.copyOf(entries);
        }

        /** Category-only entries, e.g. from the legacy flat classification list. */
        public ClassificationBadges(List<ItemType> classifications) {
            this(classifications.stream().map(Classification::of).toList(),
                    TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }

        /** The broad categories in authored order. */
        public List<ItemType> classifications() {
            return entries.stream().map(Classification::category).toList();
        }
    }

    /**
     * A semantic body group's centred heading. Inserted by the renderer in front of each non-empty group;
     * contributors declare their group with {@code TooltipContributor.bodyGroup} instead of emitting this.
     */
    record GroupHeading(TooltipGroup group, TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure)
            implements TooltipSection {
        public GroupHeading(TooltipGroup group) {
            this(group, TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }
    }

    record Heading(String label, TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure)
            implements TooltipSection {
        public Heading(String label) {
            this(label, TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }
    }

    /**
     * One label/value row, e.g. weight, an AC bonus, a single technical fact. {@code labelColor} is
     * {@link #DEFAULT_LABEL_COLOR} (the renderer's shared secondary label color) unless a row's label itself carries
     * meaning, e.g. a curse's name.
     */
    record StatRow(String iconGlyph, int iconColor, String label, String value, int valueColor,
                   TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure, int labelColor) implements TooltipSection {
        public static final int DEFAULT_LABEL_COLOR = 0;

        public StatRow(String iconGlyph, int iconColor, String label, String value, int valueColor) {
            this(iconGlyph, iconColor, label, value, valueColor, TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT, DEFAULT_LABEL_COLOR);
        }

        public StatRow withVisibility(TooltipVisibility visibility) {
            return new StatRow(iconGlyph, iconColor, label, value, valueColor, visibility, minDisclosure, labelColor);
        }

        public StatRow withMinDisclosure(TooltipDisclosureLevel minDisclosure) {
            return new StatRow(iconGlyph, iconColor, label, value, valueColor, visibility, minDisclosure, labelColor);
        }

        public StatRow withLabelColor(int labelColor) {
            return new StatRow(iconGlyph, iconColor, label, value, valueColor, visibility, minDisclosure, labelColor);
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

    /**
     * A resource's bar and centred figures (Energy, Durability), drawn under its group heading. The owning
     * contributor supplies the real amounts, fill color and figures text — the renderer only draws them.
     *
     * @param current   the exact current amount (the bar fill is computed from the exact {@code long}s, never a
     *                  rounded float, so a nearly-full resource can never draw as full)
     * @param max       the exact maximum
     * @param fillColor bar fill color
     * @param figures   e.g. {@code "31.2k / 48k UE (65%)"}
     */
    record ResourceGauge(long current, long max, int fillColor, String figures, int figuresColor,
                         TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure) implements TooltipSection {
        public ResourceGauge(long current, long max, int fillColor, String figures, int figuresColor) {
            this(current, max, fillColor, figures, figuresColor, TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
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

    /** A real Minecraft sprite for an {@link IconStatRow} — never a glyph/emoji (Block Breaking V2 §11). */
    sealed interface StatIcon {
        record Item(ItemStack stack) implements StatIcon {}
        record Effect(Holder<MobEffect> effect) implements StatIcon {}
    }

    /**
     * A primary stat row led by a real item-texture or status-effect icon (Block Breaking V2 §9/§11)
     * — the tool/block Mining Damage/Speed/Tier/Durability rows. Distinct from {@link StatRow} (the
     * older custom-glyph-font icon), which every existing contributor keeps using unchanged.
     */
    record IconStatRow(StatIcon icon, String label, String value, int valueColor,
                       TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure) implements TooltipSection {
        public IconStatRow(StatIcon icon, String label, String value, int valueColor) {
            this(icon, label, value, valueColor, TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT);
        }

        public IconStatRow withMinDisclosure(TooltipDisclosureLevel minDisclosure) {
            return new IconStatRow(icon, label, value, valueColor, visibility, minDisclosure);
        }
    }

    /** One provenance child line under an {@link IconStatRow}, e.g. "Base Tool: 75" or "Impact V: +25". */
    record ProvenanceLine(String label, String value) {}

    /**
     * Shift-only provenance breakdown for the {@link IconStatRow} immediately above it (Block
     * Breaking V2 §10) — rendered as indented {@code ├}/{@code └} lines, no icon of its own.
     */
    record ProvenanceGroup(List<ProvenanceLine> lines, TooltipVisibility visibility, TooltipDisclosureLevel minDisclosure)
            implements TooltipSection {
        public ProvenanceGroup(List<ProvenanceLine> lines) {
            this(List.copyOf(lines), TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DETAILS);
        }
    }
}
