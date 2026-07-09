package zcylas.totality.item.tools;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.economy.currency.CreditTier;
import zcylas.totality.api.item.TotalityItemComponents;
import zcylas.totality.client.tooltip.TooltipExtension;

import java.util.ArrayList;
import java.util.List;

/**
 * Physical Credits (₵) — one item, amount stored in {@link TotalityItemComponents#CREDIT_AMOUNT}.
 * Display name/tooltip/tier all derive from that amount. Does not stack normally
 * (stacksTo(1)); same-item stacks merge their amounts together on pickup instead
 * (see {@code mixin.InventoryCreditsMergeMixin}), capped at {@link #MAX_PER_STACK}.
 * TODO(browser/Fable pass): per-tier textures/models — currently one shared visual.
 *
 * Tooltip uses {@link TooltipExtension}, not {@code appendHoverText} — this mod's
 * tooltip rendering ({@code TotalityTooltipRenderer}) is fully custom and never calls
 * vanilla's hover-text pipeline, only {@code TooltipExtension.addTooltipLines}.
 */
public class CreditsItem extends Item implements TooltipExtension {

    public static final long MAX_PER_STACK = 10_000;

    public CreditsItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static long getAmount(ItemStack stack) {
        Long amount = stack.get(TotalityItemComponents.CREDIT_AMOUNT);
        return amount != null ? amount : 0L;
    }

    public static void setAmount(ItemStack stack, long amount) {
        stack.set(TotalityItemComponents.CREDIT_AMOUNT, amount);
    }

    public ItemStack createStack(long amount) {
        ItemStack stack = new ItemStack(this);
        setAmount(stack, amount);
        return stack;
    }

    /** Splits a total amount into stacks capped at {@link #MAX_PER_STACK} each. */
    public List<ItemStack> createStacks(long totalAmount) {
        List<ItemStack> stacks = new ArrayList<>();
        long remaining = totalAmount;
        while (remaining > 0) {
            long chunk = Math.min(remaining, MAX_PER_STACK);
            stacks.add(createStack(chunk));
            remaining -= chunk;
        }
        return stacks;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(CreditTier.forAmount(getAmount(stack)).displayName)
                .withStyle(ChatFormatting.AQUA);
    }

    @Override
    public void addTooltipLines(ItemStack stack, Font font, List<Component> lines) {
        long amount = getAmount(stack);
        lines.add(Component.literal("Value: " + amount + "₵").withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("Physical currency").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Drops on death").withStyle(ChatFormatting.DARK_GRAY));
    }
}
