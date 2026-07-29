package zcylas.totality.item.potion.dnd;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link HealingPotionItem}'s actor-generic contract at the method-signature level,
 * via reflection only.
 *
 * <p>An actual instance cannot be constructed under plain JUnit: {@code Item}'s constructor calls
 * into {@code BuiltInRegistries.ITEM.createIntrusiveHolder(...)}, which throws once the item
 * registry is frozen (post-{@code Bootstrap.bootStrap()}), and throws a different
 * "Not bootstrapped" error before that — confirmed by direct probing during this task. No other
 * test in this repository constructs a Minecraft {@code Item}/{@code ItemStack} for the same
 * reason, so this test instead checks the class's declared method signatures directly, which
 * requires only loading (not initializing) the class.
 */
class HealingPotionItemContractTest {

    @Test
    void getUseDurationTakesLivingEntityNotAPlayerSubtype() throws Exception {
        Method m = HealingPotionItem.class.getDeclaredMethod("getUseDuration", ItemStack.class, LivingEntity.class);
        assertEquals(int.class, m.getReturnType());
    }

    @Test
    void getUseAnimationIsDeclaredAndReturnsItemUseAnimation() throws Exception {
        Method m = HealingPotionItem.class.getDeclaredMethod("getUseAnimation", ItemStack.class);
        assertEquals(ItemUseAnimation.class, m.getReturnType());
    }

    @Test
    void finishUsingItemTakesLivingEntityNotAPlayerSubtype() throws Exception {
        Method m = HealingPotionItem.class.getDeclaredMethod("finishUsingItem", ItemStack.class, Level.class, LivingEntity.class);
        assertEquals(ItemStack.class, m.getReturnType());
    }

    @Test
    void noDeclaredMethodOnHealingPotionItemAcceptsAPlayerOrServerPlayerParameter() {
        for (Method m : HealingPotionItem.class.getDeclaredMethods()) {
            for (Class<?> param : m.getParameterTypes()) {
                assertFalse(Player.class.isAssignableFrom(param),
                        "HealingPotionItem." + m.getName() + " must not require a Player-family parameter merely to apply healing, found: " + param);
            }
        }
    }

    @Test
    void constructorAcceptsHealingAmountDurationAndProperties() throws Exception {
        Constructor<?> ctor = HealingPotionItem.class.getDeclaredConstructor(HealingAmount.class, int.class, Item.Properties.class);
        assertTrue(Modifier.isPublic(ctor.getModifiers()));
    }
}
