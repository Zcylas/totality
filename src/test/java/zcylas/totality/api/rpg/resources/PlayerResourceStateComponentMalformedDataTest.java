package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PlayerResourceStateComponent#readData} wraps each entry in a try/catch so one corrupt
 * player-NBT entry cannot crash the whole player load (canonical §17.4). That method itself can't
 * be exercised here without a real {@code ValueInput} (see the class-level note on
 * {@link PlayerResourceStateComponentTest}), so this test instead pins that the exact failure
 * conditions the catch block is designed to swallow really do throw when hit directly — i.e. that
 * there is something real for the try/catch to guard against, not a dead catch clause.
 */
class PlayerResourceStateComponentMalformedDataTest {

    @Test
    void malformedIdentifierStringThrows() {
        assertThrows(RuntimeException.class, () -> Identifier.parse("not a valid identifier!!!"));
    }

    @Test
    void unknownResourceModelNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> ResourceModel.valueOf("NOT_A_REAL_MODEL"));
    }
}
