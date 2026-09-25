package zcylas.totality.client.tooltip.group;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;

import java.util.Set;

/**
 * A semantic body group — a section of the tooltip body such as Mining or Combat, shown under a centred
 * heading. The identity is independent of any item: every contributor that supplies Mining information
 * targets the same Mining group, and their content is merged under one heading.
 *
 * @param id        stable identifier; also the localization key {@link #translationKey()}
 * @param priority  default ordering, lower first (see {@link TooltipGroupOrdering})
 * @param icon      the heading's icon (texture, or tinted glyph)
 * @param leadsFor  item categories whose items show this group first when it is the item's primary (first)
 *                  classification — e.g. Mining for a {@code TOOL}, Combat for a {@code WEAPON}
 * @param placement whether the group is an ordinary body group or a fixed bottom section
 */
public record TooltipGroup(Identifier id, int priority, TooltipGroupIcon icon, Set<ItemType> leadsFor, Placement placement) {

    /**
     * Where a group sits in the body. {@link #ORDINARY} groups are ordered by authored override, primary
     * classification and priority. {@link #BOTTOM} sections (Requirements, then the Energy and Durability
     * resources) always follow every ordinary group, in priority order, and are never moved by an override
     * or classification — so they cannot land in the middle of unrelated information.
     */
    public enum Placement { ORDINARY, BOTTOM }

    public TooltipGroup {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (icon == null) throw new IllegalArgumentException("every group must have an icon");
        if (placement == null) throw new IllegalArgumentException("placement must not be null");
        leadsFor = Set.copyOf(leadsFor);
    }

    /** An ordinary body group. */
    public TooltipGroup(Identifier id, int priority, TooltipGroupIcon icon, Set<ItemType> leadsFor) {
        this(id, priority, icon, leadsFor, Placement.ORDINARY);
    }

    /** Localization key of the display name, e.g. {@code tooltip_group.totality.mining}. */
    public String translationKey() {
        return translationKey(id);
    }

    public static String translationKey(Identifier id) {
        return "tooltip_group." + id.getNamespace() + "." + id.getPath();
    }

    /** Display fallback when no translation exists: the path with separators as spaces, e.g. {@code block properties}. */
    public String fallbackName() {
        return id.getPath().replace('_', ' ');
    }
}
