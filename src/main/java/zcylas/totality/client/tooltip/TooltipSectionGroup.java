package zcylas.totality.client.tooltip;

import zcylas.totality.client.tooltip.contributor.TooltipContributor;

/**
 * A small, closed set of stable high-level body positions a contributor's entire output is
 * placed into, ordered by {@link #ordinal()}. This replaces sorting individual sections by their
 * Java record kind (which scattered a single contributor's own output — e.g. a weapon's property
 * badges could end up sorted after an unrelated contributor's attunement row purely because
 * {@code StatRow} outranked {@code PropertyBadges}). Grouping now happens per-<em>contributor</em>,
 * not per-section: every section a single {@link TooltipContributor} call emits into the body
 * stays together, in exactly the order that contributor emitted it, and only the resulting
 * blocks are ordered relative to each other by group.
 *
 * Header, the rarity badge, and classification badges are not part of this model at all — they
 * are filtered out into renderer-owned shell positions before grouping ever runs.
 */
public enum TooltipSectionGroup {
    /** An item's primary functional/combat identity — e.g. Weapon, Energy, Grimoire, Healing Potion. */
    PRIMARY,
    /** Reserved for a future contributor whose entire output is a standalone property list. */
    PROPERTIES,
    /** Reserved for a future contributor whose entire output is a standalone requirement/warning list. */
    REQUIREMENTS,
    /** Secondary/auxiliary resource facts — e.g. vanilla fuel burn time. */
    RESOURCES,
    /** Attunement requirement/status and attunement-gated bonuses. */
    ATTUNEMENT,
    /** Weight and other secondary physical metadata. */
    WEIGHT,
    /** Lore/description text. */
    LORE,
    /** Preserved vanilla/third-party content — always after every semantic group. */
    EXTERNAL,
    /** Technical/diagnostic content — always last. */
    TECHNICAL
}
