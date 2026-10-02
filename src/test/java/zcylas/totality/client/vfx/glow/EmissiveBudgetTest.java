package zcylas.totality.client.vfx.glow;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The emissive layer's per-group budgets and final global cap (Fireball V2 B4, decision D5). */
class EmissiveBudgetTest {

    private record Source(float demand, String group) implements EmissiveSource {
        @Override
        public void emit(EmissiveBuffer buffer, Vec3 camera, float partialTick) {}

        @Override
        public float emissiveDemand() {
            return demand;
        }

        @Override
        public String budgetGroup() {
            return group;
        }
    }

    @Test
    void aFewEffectsKeepTheirNormalGlow() {
        EmissiveBudget b = new EmissiveBudget();
        b.setGroupLimit("fireball", 2.5f);
        Source explosion = new Source(1.0f, "fireball"), projectile = new Source(0.25f, "fireball"), beams = new Source(1.0f, "heat_vision");
        b.resolve(List.of(explosion, projectile, beams), EmissiveGlowSettings.DEFAULT_GLOBAL_LIMIT);
        assertEquals(1.0f, b.scale(explosion));
        assertEquals(1.0f, b.scale(projectile));
        assertEquals(1.0f, b.scale(beams));
    }

    @Test
    void manyFireballsShareTheirGroupLimitAndNeverDimHeatVision() {
        EmissiveBudget b = new EmissiveBudget();
        b.setGroupLimit("fireball", 2.5f);
        Source explosions = new Source(20.0f, "fireball"), projectiles = new Source(5.0f, "fireball"), beams = new Source(1.0f, "heat_vision");
        b.resolve(List.of(explosions, projectiles, beams), EmissiveGlowSettings.DEFAULT_GLOBAL_LIMIT);
        assertEquals(0.1f, b.scale(explosions), 1e-6f, "25 demanded, 2.5 allowed");
        assertEquals(0.1f, b.scale(projectiles), 1e-6f, "the whole group is scaled alike");
        assertEquals(1.0f, b.scale(beams), "2.5 + 1 stays under the global limit: Heat Vision is untouched");
        assertEquals(25.0f, b.groupDemand("fireball"));
        assertEquals(0.1f, b.groupScale("fireball"), 1e-6f);
        assertEquals(3.5f, b.lastTotal(), 1e-6f);
    }

    @Test
    void theGlobalLimitScalesEverythingOnlyWhenTheCappedTotalExceedsIt() {
        EmissiveBudget b = new EmissiveBudget();
        Source a = new Source(4.0f, "a"), c = new Source(4.0f, null), zero = new Source(0.0f, null);
        b.resolve(List.of(a, c, zero), 6.0f);
        assertEquals(0.75f, b.scale(a), 1e-6f);
        assertEquals(0.75f, b.scale(c), 1e-6f);
        assertEquals(0.75f, b.lastGlobalScale(), 1e-6f);
        b.resolve(List.of(a), 6.0f);
        assertEquals(1.0f, b.scale(a));
        assertEquals(1.0f, b.scale(c), "a source missing from the frame keeps scale 1");
    }

    @Test
    void removingAGroupLimit() {
        EmissiveBudget b = new EmissiveBudget();
        b.setGroupLimit("fireball", 2.5f);
        b.setGroupLimit("fireball", 0.0f);
        Source s = new Source(5.0f, "fireball");
        b.resolve(List.of(s), 64.0f);
        assertEquals(1.0f, b.scale(s));
    }
}
