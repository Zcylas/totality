package zcylas.totality.item.tools;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import zcylas.totality.api.economy.currency.CurrencyHelper;
import zcylas.totality.api.economy.currency.Denomination;

import java.util.function.Consumer;

/**
 * Physical Coins — pure loot/inventory item, no wallet interaction on its own.
 * The Wallet ({@link zcylas.totality.api.economy.currency.CurrencyComponents#WALLET})
 * holds Credits only; exchanging Coins for Credits happens via a Banker, not by
 * right-clicking the coin (that auto-deposit behavior was removed 2026-07-08).
 */
public class CoinItem extends Item {

    private final Denomination denomination;
    private final Style nameStyle;

    public CoinItem(Denomination denomination, Properties properties) {
        super(properties);
        this.denomination = denomination;
        this.nameStyle = Style.EMPTY.withColor(TextColor.fromRgb(denomination.color));
    }

    public Denomination getDenomination() {
        return denomination;
    }

    public long getValue(ItemStack stack) {
        return denomination.toRawValue(stack.getCount());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        long rawValue = getValue(stack);
        builder.accept(
                Component.literal(CurrencyHelper.format(rawValue))
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(denomination.color)))
        );
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(nameStyle);
    }
}