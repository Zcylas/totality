package zcylas.totality.api.dialogue.conditions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dialogue.DialogueCondition;
import zcylas.totality.api.dialogue.NarrativeFlagsComponent;
import zcylas.totality.api.economy.currency.CreditPaymentHelper;

/** Gates a choice behind being able to afford {@code amount} Credits. By default checks
 *  account balance first, then physical Credits (matches any normal Credits cost) — set
 *  {@code physical_only: true} for costs that must be paid before an account exists (e.g.
 *  the Banker's account-opening fee, where checking the account would be circular). */
public record CreditCostCondition(long amount, boolean physicalOnly) implements DialogueCondition {
    public static final MapCodec<CreditCostCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.fieldOf("amount").forGetter(CreditCostCondition::amount),
            Codec.BOOL.optionalFieldOf("physical_only", false).forGetter(CreditCostCondition::physicalOnly)
    ).apply(i, CreditCostCondition::new));

    @Override
    public boolean test(ServerPlayer player, NarrativeFlagsComponent flags) {
        return physicalOnly
                ? CreditPaymentHelper.canAffordPhysical(player, amount)
                : CreditPaymentHelper.canAfford(player, amount);
    }

    @Override
    public String type() { return "credit_cost"; }

    @Override
    public String lockReason() {
        return physicalOnly ? "Requires " + amount + "₵ in physical Credits" : "Requires " + amount + "₵";
    }
}
