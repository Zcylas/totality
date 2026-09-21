package zcylas.totality.api.rpg.resources.state;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.ResourceModel;

import static org.junit.jupiter.api.Assertions.*;

class ScalarResourceStateTest {

    @Test
    void singleArgConstructorDefaultsOverflowAndRemainderToZero() {
        ScalarResourceState state = new ScalarResourceState(42);
        assertEquals(42, state.currentUnits());
        assertEquals(0, state.overflowUnits());
        assertEquals(0, state.regenerationRemainder());
        assertEquals(ResourceModel.SCALAR, state.model());
    }

    @Test
    void settersMutateInPlace() {
        ScalarResourceState state = new ScalarResourceState(0);
        state.setCurrentUnits(50);
        state.setOverflowUnits(5);
        state.setRegenerationRemainder(1);

        assertEquals(50, state.currentUnits());
        assertEquals(5, state.overflowUnits());
        assertEquals(1, state.regenerationRemainder());
    }

    @Test
    void copyProducesIndependentInstanceWithSameValues() {
        ScalarResourceState original = new ScalarResourceState(10, 2, 1);
        ScalarResourceState copy = original.copy();

        assertNotSame(original, copy);
        assertEquals(original.currentUnits(), copy.currentUnits());
        assertEquals(original.overflowUnits(), copy.overflowUnits());
        assertEquals(original.regenerationRemainder(), copy.regenerationRemainder());

        copy.setCurrentUnits(999);
        assertEquals(10, original.currentUnits(), "mutating the copy must not affect the original");
    }
}
