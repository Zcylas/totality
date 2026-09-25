package zcylas.totality.init;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.LoreComponent;
import zcylas.totality.api.core.rpgutils.rarity.RarityComponent;

import java.util.Map;

/**
 * COMMON Rarity + short Lore for six baseline vanilla blocks (Block Breaking V2 §8-9 of the
 * "tooltip eligibility + wrong-tool" review-fix pass) — demonstrates that ordinary vanilla items
 * can carry Totality presentation data without being Totality's own registrations. Uses Fabric's
 * {@link DefaultItemComponentEvents} (the standard mechanism for adding default data components to
 * an already-registered, non-Totality item) and the existing {@link ItemComponents#RARITY}/
 * {@link ItemComponents#LORE} components — no second lore/rarity system.
 *
 * <p>The Totality tooltip eligibility gate ({@code TotalityTooltipRenderer#isEligible}) does NOT
 * depend on this — these six items would already show a Totality tooltip via
 * {@code MiningToolContributor}/{@code BlockDurabilityContributor} with or without this class; this
 * only adds the Rarity badge and flavor line on top.
 */
public final class VanillaItemPresentation {

    private VanillaItemPresentation() {}

    /** One concise, factual flavor line per item — not gameplay stats, those have their own tooltip rows. */
    private static final Map<Item, String> LORE = Map.of(
            // Oak Log/Stone wording adjusted in the Tier/wrong-tool review-fix pass: the previous Oak
            // Log line implied already-hewn timber rather than a plain cut log, and the previous Stone
            // line named a phrase that collides with Bedrock, the distinct Minecraft block.
            Items.OAK_LOG, "A cut length of oak trunk, valued for building and crafting alike.",
            Items.STONE, "Common natural rock, quarried from underground and used widely in construction.",
            Items.DIORITE, "An intermediate igneous rock with a speckled, salt-and-pepper grain from slow cooling.",
            Items.ANDESITE, "Andesite's fine grey grain comes from volcanic magma cooling quickly at the surface.",
            Items.GRANITE, "A hard, coarse-grained igneous rock, mottled with quartz and feldspar crystals.",
            Items.COBBLESTONE, "Fieldstone broken down into rough, irregular chunks fit for simple construction."
    );

    public static void register() {
        DefaultItemComponentEvents.MODIFY.register(context -> LORE.forEach((item, lore) ->
                context.modify(item, builder -> {
                    builder.set(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON));
                    builder.set(ItemComponents.LORE, new LoreComponent(lore));
                })));
    }
}
