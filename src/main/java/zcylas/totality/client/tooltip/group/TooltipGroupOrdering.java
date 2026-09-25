package zcylas.totality.client.tooltip.group;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.api.core.rpgutils.rarity.TooltipProfileComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic order of the body groups present on one tooltip:
 * <ol>
 *   <li>groups named by the item's authored order override, in that order (absent ones are skipped);</li>
 *   <li>then the group that leads for the item's primary (first) classification category, if present;</li>
 *   <li>then every remaining ordinary group by {@link TooltipGroup#priority()}, ties broken by id;</li>
 *   <li>then the {@link TooltipGroup.Placement#BOTTOM bottom} sections (Requirements, Energy, Durability) by
 *       priority — never moved by the override or the classification lead.</li>
 * </ol>
 * {@link #order} is pure — no item, font or registry lookups; unit-tested.
 */
public final class TooltipGroupOrdering {

    public static List<TooltipGroup> order(Collection<TooltipGroup> present, List<Identifier> authoredOrder,
                                           @Nullable ItemType primaryCategory) {
        List<TooltipGroup> ordinary = present.stream().filter(g -> g.placement() == TooltipGroup.Placement.ORDINARY).toList();
        Set<TooltipGroup> ordered = new LinkedHashSet<>();
        for (Identifier id : authoredOrder) {
            for (TooltipGroup group : ordinary) {
                if (group.id().equals(id)) ordered.add(group);
            }
        }
        if (primaryCategory != null) {
            ordinary.stream()
                    .filter(group -> group.leadsFor().contains(primaryCategory))
                    .sorted(BY_PRIORITY)
                    .findFirst()
                    .ifPresent(ordered::add);
        }
        List<TooltipGroup> rest = new ArrayList<>(ordinary);
        rest.sort(BY_PRIORITY);
        ordered.addAll(rest);
        present.stream().filter(g -> g.placement() == TooltipGroup.Placement.BOTTOM).sorted(BY_PRIORITY).forEach(ordered::add);
        return List.copyOf(ordered);
    }

    /** The item's authored group order override ({@code TooltipProfileComponent.groupOrder}); empty when none. */
    public static List<Identifier> authoredOrder(ItemStack stack) {
        var type = ItemComponents.getTooltipProfile();
        TooltipProfileComponent profile = type == null ? null : stack.get(type);
        return profile == null ? List.of() : profile.groupOrder();
    }

    private static final Comparator<TooltipGroup> BY_PRIORITY =
            Comparator.comparingInt(TooltipGroup::priority).thenComparing(group -> group.id().toString());

    private TooltipGroupOrdering() {}
}
