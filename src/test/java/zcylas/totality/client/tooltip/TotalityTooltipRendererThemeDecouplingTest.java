package zcylas.totality.client.tooltip;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reflection-only tests proving classification cannot influence tooltip theme/border selection —
 * structurally, not just behaviorally. {@code TotalityTooltipRenderer} cannot be exercised
 * end-to-end here (it needs a real {@code Font}/{@code ItemStack}), so this inspects the actual
 * method signatures the correction requires: {@code resolveTheme} must take only an
 * {@link ItemRarity}, and the old classification-driven {@code typeBorderStyle} mapping must no
 * longer exist at all.
 */
class TotalityTooltipRendererThemeDecouplingTest {

    @Test
    void resolveThemeAcceptsOnlyAnItemRarityParameter() throws Exception {
        Method resolveTheme = TotalityTooltipRenderer.class.getDeclaredMethod("resolveTheme", ItemRarity.class);
        assertEquals(1, resolveTheme.getParameterCount(),
                "resolveTheme must take exactly one parameter — reordering or adding a classification "
                        + "argument would let classification influence the theme again");
        assertEquals(ItemRarity.class, resolveTheme.getParameterTypes()[0]);
    }

    @Test
    void noOverloadOfResolveThemeAcceptsAClassificationOrItemTypeArgument() {
        boolean anyOverloadTakesMoreThanOneArg = Arrays.stream(TotalityTooltipRenderer.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("resolveTheme"))
                .anyMatch(m -> m.getParameterCount() != 1);
        assertFalse(anyOverloadTakesMoreThanOneArg,
                "resolveTheme must have exactly one single-argument form — no classification-aware overload");
    }

    @Test
    void typeBorderStyleMappingNoLongerExistsOnTheRenderer() {
        boolean stillPresent = Arrays.stream(TotalityTooltipRenderer.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("typeBorderStyle"));
        assertFalse(stillPresent, "the classification-driven typeBorderStyle(ItemType) mapping must be fully removed, "
                + "not merely unused, since it had no remaining valid use");
    }

    @Test
    void rarityBorderStyleAcceptsOnlyAnItemRarityParameter() throws Exception {
        Method rarityBorderStyle = TotalityTooltipRenderer.class.getDeclaredMethod("rarityBorderStyle", ItemRarity.class);
        assertEquals(1, rarityBorderStyle.getParameterCount());
    }
}
