package zcylas.totality.item.tools;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.economy.currency.CurrencyComponents;
import zcylas.totality.api.economy.currency.CurrencyHelper;
import zcylas.totality.api.economy.currency.Denomination;

import java.util.function.Consumer;

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
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!level.isClientSide() && user instanceof ServerPlayer serverPlayer) {
            long rawValue = getValue(stack);
            CurrencyComponents.WALLET.get((ComponentProvider) serverPlayer).modify(rawValue);
            stack.shrink(stack.getCount());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
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