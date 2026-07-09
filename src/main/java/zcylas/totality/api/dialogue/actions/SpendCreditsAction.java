package zcylas.totality.api.dialogue.actions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dialogue.DialogueAction;
import zcylas.totality.api.economy.currency.CreditPaymentHelper;

/** Deducts Credits — by default account balance first, then physical Credits (matches
 *  {@link zcylas.totality.api.dialogue.conditions.CreditCostCondition}'s default). Set
 *  {@code physical_only: true} to match that condition's physical-only mode instead.
 *  Pair with the matching condition on the same choice so this can never be picked
 *  without affordability already having been verified. */
public record SpendCreditsAction(long amount, boolean physicalOnly) implements DialogueAction {
    public static final MapCodec<SpendCreditsAction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.fieldOf("amount").forGetter(SpendCreditsAction::amount),
            Codec.BOOL.optionalFieldOf("physical_only", false).forGetter(SpendCreditsAction::physicalOnly)
    ).apply(i, SpendCreditsAction::new));

    @Override
    public void execute(ServerPlayer player) {
        if (physicalOnly) CreditPaymentHelper.payPhysical(player, amount);
        else CreditPaymentHelper.pay(player, amount);
    }

    @Override
    public String type() { return "spend_credits"; }
}
