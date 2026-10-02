package zcylas.totality.entity.magic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fireball V2's shared cosmetic particle budget (B4): distance quality and load scaling. */
class FireballParticleBudgetTest {

    @Test
    void fullRecipesNearbyFewerFarAway() {
        assertEquals(22, FireballParticleBudget.allow(22, 10.0, 0));
        assertEquals(11, FireballParticleBudget.allow(22, 30.0, 0));
        assertEquals(6, FireballParticleBudget.allow(22, 60.0, 0));
        assertEquals(0, FireballParticleBudget.allow(0, 10.0, 0));
    }

    @Test
    void recipesShrinkUnderLoadAndStopAtTheCap() {
        int max = FireballParticleBudget.MAX_ALIVE;
        assertEquals(22, FireballParticleBudget.allow(22, 10.0, max / 2), "untouched up to half the budget");
        assertEquals(11, FireballParticleBudget.allow(22, 10.0, max * 3 / 4));
        assertEquals(0, FireballParticleBudget.allow(22, 10.0, max));
        assertEquals(0, FireballParticleBudget.allow(22, 10.0, max + 50));
        for (int alive = 0; alive <= max; alive += 7) {
            assertTrue(alive + FireballParticleBudget.allow(1000, 0.0, alive) <= max, "never past the cap at " + alive);
        }
    }

    @Test
    void countersReturnToZero() {
        FireballParticleBudget.reset();
        FireballParticleBudget.onCreated();
        FireballParticleBudget.onCreated();
        FireballParticleBudget.onRemoved();
        FireballParticleBudget.onRemoved();
        FireballParticleBudget.onRemoved();
        assertEquals(0, FireballParticleBudget.alive(), "never negative");
    }
}
