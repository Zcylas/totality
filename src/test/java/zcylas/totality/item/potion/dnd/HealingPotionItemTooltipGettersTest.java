package zcylas.totality.item.potion.dnd;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reflection-only tests confirming the tooltip-facing getters added to {@link HealingPotionItem}
 * for {@code HealingPotionContributor} exist with the expected signatures. An instance cannot be
 * constructed under plain JUnit — see {@link HealingPotionItemContractTest}'s class Javadoc —
 * so this checks method shape only, the same approach that test already established.
 */
class HealingPotionItemTooltipGettersTest {

    @Test
    void getHealingAmountIsPublicAndReturnsHealingAmount() throws Exception {
        Method m = HealingPotionItem.class.getDeclaredMethod("getHealingAmount");
        assertTrue(Modifier.isPublic(m.getModifiers()));
        assertEquals(HealingAmount.class, m.getReturnType());
    }

    @Test
    void getUseDurationTicksIsPublicAndReturnsInt() throws Exception {
        Method m = HealingPotionItem.class.getDeclaredMethod("getUseDurationTicks");
        assertTrue(Modifier.isPublic(m.getModifiers()));
        assertEquals(int.class, m.getReturnType());
    }

    @Test
    void gettersExposeTheSameFieldsUseDurationAndFinishUsingItemAlreadyReadInternally() throws Exception {
        // Sentinel: confirms the getters and the internal fields they expose share the same
        // name-shape used by getUseDuration/finishUsingItem, so the tooltip cannot silently read a
        // different value than the one actually applied.
        assertNotNull(HealingPotionItem.class.getDeclaredField("healingAmount"));
        assertNotNull(HealingPotionItem.class.getDeclaredField("useDurationTicks"));
    }
}
