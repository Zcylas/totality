package zcylas.totality.api.shop;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Minimal merchant-side runtime state required by SELL (design document Section 2/5). Deliberately
 * an interface, not tied to any concrete NPC — Phase 2 explicitly does NOT build
 * {@code ProvisionerNpcEntity}; the interface exists so {@link TradeSessionManager}'s SELL/BUY
 * logic can be written once against this contract and later handed a persistent, per-entity
 * implementation (backed by real NBT on a Provisioner) without changing that logic at all.
 *
 * <p>{@code currentCredits} is the merchant's LIVE business balance (Section 2) — a business
 * concept, not a physical item and not {@code WalletComponent} (which is hard-coupled to a
 * {@code ServerPlayer} and represents a player's account, an entirely different thing).
 */
public interface MerchantRuntime {

    /** Stable identity for this merchant, surfaced to {@code PricingContext.merchantId()}. */
    Identifier merchantId();

    long currentCredits();

    /** @throws IllegalArgumentException if {@code value} is negative — a merchant's balance is
     *          never allowed to go negative; callers must validate affordability before calling
     *          this, not rely on it to clamp an invalid result. */
    void setCurrentCredits(long value);

    /** Item tags this merchant will buy from a player. An EMPTY set means this merchant rejects
     *  every SELL attempt — there is no implicit "accept everything" fallback. */
    Set<TagKey<Item>> acceptedTags();

    /** True if {@code stack} matches at least one of {@link #acceptedTags()}. */
    default boolean accepts(ItemStack stack) {
        for (TagKey<Item> tag : acceptedTags()) {
            if (stack.is(tag)) return true;
        }
        return false;
    }

    /** Translation key for this merchant's archetype label (Phase 4, Part B header — "Provisioner",
     *  etc.), resolved client-side via a normal translatable {@code Component} rather than a
     *  hardcoded server-authored string (Part H). Defaults to a generic "Merchant" label for the
     *  shared/registry-backed path ({@link InMemoryMerchantRuntime}, the {@code test_trader} shop);
     *  {@code ProvisionerNpcEntity} overrides this with its own archetype key. */
    default String archetypeTranslationKey() {
        return "totality.trading.archetype.merchant";
    }
}
