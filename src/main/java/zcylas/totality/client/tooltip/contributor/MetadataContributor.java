package zcylas.totality.client.tooltip.contributor;

import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.api.core.rpgutils.rarity.LoreComponent;
import zcylas.totality.api.core.rpgutils.rarity.RarityComponent;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipSectionGroup;
import zcylas.totality.client.tooltip.TooltipVisibility;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Header, rarity badge, ordered classification badges, and lore/description — the generic
 * "panel shell" content. Never invents a rarity or classification for an item that doesn't have
 * one: an explicitly opted-in item without an authored rarity simply gets no
 * {@link TooltipSection.RarityBadge}, and the renderer falls back to a neutral theme.
 *
 * Per the locked unidentified-item policy, exact rarity and lore/history are identified-only
 * ({@link TooltipVisibility#WHEN_IDENTIFIED}) — the item's classification badges (a plainly
 * observable fact, e.g. "this is a weapon") and its name stay {@link TooltipVisibility#ALWAYS}.
 * {@link zcylas.totality.client.tooltip.TooltipKnowledgeView#of} treats a stack with no
 * <em>explicit</em> identification component as identified for tooltip purposes — so this gating
 * has no visible effect on any current stack (nothing in the repository yet authors an explicit
 * {@code UNIDENTIFIED}/{@code PARTIALLY} component) until a future Identification API
 * deliberately marks a stack otherwise.
 */
public final class MetadataContributor implements TooltipContributor {

    /** Only the lore Description ever reaches the body (Header/RarityBadge/ClassificationBadges are shell). */
    @Override
    public TooltipSectionGroup sectionGroup() {
        return TooltipSectionGroup.LORE;
    }

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        ItemStack stack = ctx.stack();
        List<TooltipSection> sections = new ArrayList<>();

        sections.add(new TooltipSection.Header(stack.getHoverName()));

        var rarityType = ItemComponents.getRarity();
        if (rarityType != null && stack.has(rarityType)) {
            RarityComponent rarity = stack.get(rarityType);
            if (rarity != null) {
                sections.add(new TooltipSection.RarityBadge(rarity.rarity())
                        .withVisibility(TooltipVisibility.WHEN_IDENTIFIED));
            }
        }

        List<ItemType> classifications = ItemComponents.classificationsOf(stack);
        if (!classifications.isEmpty()) {
            sections.add(new TooltipSection.ClassificationBadges(classifications));
        }

        var loreType = ItemComponents.getLore();
        if (loreType != null && stack.has(loreType)) {
            LoreComponent lore = stack.get(loreType);
            if (lore != null && lore.text() != null && !lore.text().isEmpty()) {
                sections.add(new TooltipSection.Description(lore.text())
                        .withVisibility(TooltipVisibility.WHEN_IDENTIFIED));
            }
        }

        return sections;
    }
}
