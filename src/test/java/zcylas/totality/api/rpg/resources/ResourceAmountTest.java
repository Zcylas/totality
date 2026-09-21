package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceAmountTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    @Test
    void scalarFactoryHasNoPartition() {
        ResourceAmount amount = ResourceAmount.scalar(id("stamina"), 100);
        assertEquals(id("stamina"), amount.resourceId());
        assertEquals(100, amount.units());
        assertTrue(amount.partition().isEmpty());
    }

    @Test
    void partitionedFactoryCarriesThePartition() {
        ResourceAmount amount = ResourceAmount.partitioned(id("spell_slots"), 3, 1);
        assertEquals(id("spell_slots"), amount.resourceId());
        assertEquals(1, amount.units());
        assertEquals(3, amount.partition().orElseThrow());
    }

    @Test
    void rejectsNullResourceId() {
        assertThrows(NullPointerException.class, () -> ResourceAmount.scalar(null, 1));
    }
}
