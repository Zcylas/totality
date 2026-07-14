package zcylas.totality.api.shop;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.Set;

/**
 * Lightweight, non-persistent {@link MerchantRuntime} — resets on server restart. Explicitly a
 * PLACEHOLDER (design document Section 2a/11 step 3): the first real Provisioner will replace
 * this with NBT-persisted per-entity state, without needing to change any SELL/BUY logic that
 * only depends on the {@link MerchantRuntime} interface.
 */
public final class InMemoryMerchantRuntime implements MerchantRuntime {

    private final Identifier merchantId;
    private final Set<TagKey<Item>> acceptedTags;
    private long currentCredits;

    public InMemoryMerchantRuntime(Identifier merchantId, long startingCredits, Set<TagKey<Item>> acceptedTags) {
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId must not be null");
        }
        if (acceptedTags == null) {
            throw new IllegalArgumentException("acceptedTags must not be null");
        }
        if (startingCredits < 0) {
            throw new IllegalArgumentException("startingCredits must not be negative, was " + startingCredits);
        }
        this.merchantId = merchantId;
        this.currentCredits = startingCredits;
        // Copied (and made immutable) so a caller holding the original Set reference can never
        // mutate this merchant's accepted-goods policy out from under it after construction.
        this.acceptedTags = Set.copyOf(acceptedTags);
    }

    @Override
    public Identifier merchantId() {
        return merchantId;
    }

    @Override
    public long currentCredits() {
        return currentCredits;
    }

    @Override
    public void setCurrentCredits(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("currentCredits must not be negative, was " + value);
        }
        this.currentCredits = value;
    }

    @Override
    public Set<TagKey<Item>> acceptedTags() {
        return acceptedTags;
    }
}
