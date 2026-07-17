package zcylas.totality.api.rpg.resources.integration;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.ResourceAmount;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers every canonical grant-initialization variant (canonical §16.6) and the malformed
 * declarations each one's compact constructor rejects.
 */
class ResourceGrantInitializationTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    @Test
    void atMinimumAndAtMaximumCarryNoData() {
        assertDoesNotThrow(ResourceGrantInitialization.AtMinimum::new);
        assertDoesNotThrow(ResourceGrantInitialization.AtMaximum::new);
    }

    @Test
    void preserveExistingCarriesNoData() {
        assertDoesNotThrow(ResourceGrantInitialization.PreserveExisting::new);
    }

    @Test
    void atFractionValidConstructionSucceeds() {
        var half = new ResourceGrantInitialization.AtFraction(1, 2);
        assertEquals(1, half.numerator());
        assertEquals(2, half.denominator());
    }

    @Test
    void atFractionRejectsZeroDenominator() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceGrantInitialization.AtFraction(1, 0));
    }

    @Test
    void atFractionRejectsNegativeDenominator() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceGrantInitialization.AtFraction(1, -1));
    }

    @Test
    void atFractionRejectsNegativeNumerator() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceGrantInitialization.AtFraction(-1, 2));
    }

    @Test
    void atFractionAllowsZeroNumerator() {
        // 0/denominator is a valid "start at zero" declaration, not malformed.
        assertDoesNotThrow(() -> new ResourceGrantInitialization.AtFraction(0, 1));
    }

    @Test
    void atAbsoluteValidConstructionSucceeds() {
        ResourceAmount amount = ResourceAmount.scalar(id("mana"), 50);
        var atAbsolute = new ResourceGrantInitialization.AtAbsolute(amount);
        assertEquals(amount, atAbsolute.amount());
    }

    @Test
    void atAbsoluteRejectsNullAmount() {
        assertThrows(NullPointerException.class,
                () -> new ResourceGrantInitialization.AtAbsolute(null));
    }

    @Test
    void customValidConstructionSucceeds() {
        var custom = new ResourceGrantInitialization.Custom(id("some_strategy"));
        assertEquals(id("some_strategy"), custom.strategyId());
    }

    @Test
    void customRejectsMissingStrategyIdentifier() {
        assertThrows(NullPointerException.class,
                () -> new ResourceGrantInitialization.Custom(null));
    }

    @Test
    void variantsAreDistinctSealedImplementations() {
        // Structural sanity: every variant actually implements the sealed interface.
        ResourceGrantInitialization[] variants = {
                new ResourceGrantInitialization.AtMinimum(),
                new ResourceGrantInitialization.AtMaximum(),
                new ResourceGrantInitialization.AtFraction(1, 2),
                new ResourceGrantInitialization.AtAbsolute(ResourceAmount.scalar(id("mana"), 1)),
                new ResourceGrantInitialization.PreserveExisting(),
                new ResourceGrantInitialization.Custom(id("x"))
        };
        for (ResourceGrantInitialization variant : variants) {
            assertNotNull(variant);
        }
    }
}
