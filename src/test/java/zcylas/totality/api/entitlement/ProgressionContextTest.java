package zcylas.totality.api.entitlement;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Correction 3: the non-progression scope is per player, nests, and always closes. */
class ProgressionContextTest {

    private final UUID debugPlayer = UUID.randomUUID();
    private final UUID otherPlayer = UUID.randomUUID();

    @Test
    void scopeAppliesOnlyToItsPlayerAndOnlyWhileRunning() {
        assertFalse(ProgressionContext.inNonProgressionScope(debugPlayer));
        boolean[] seen = new boolean[2];
        ProgressionContext.runNonProgression(debugPlayer, () -> {
            seen[0] = ProgressionContext.inNonProgressionScope(debugPlayer);
            seen[1] = ProgressionContext.inNonProgressionScope(otherPlayer);
        });
        assertTrue(seen[0]);
        assertFalse(seen[1], "another player's progression is unaffected");
        assertFalse(ProgressionContext.inNonProgressionScope(debugPlayer), "the scope ends with the action");
    }

    @Test
    void nestedScopesAndExceptionsAlwaysUnwind() {
        ProgressionContext.runNonProgression(debugPlayer, () ->
                ProgressionContext.runNonProgression(debugPlayer, () -> {}));
        assertFalse(ProgressionContext.inNonProgressionScope(debugPlayer));
        assertThrows(IllegalStateException.class, () -> ProgressionContext.runNonProgression(debugPlayer, () -> {
            throw new IllegalStateException("spell failed");
        }));
        assertFalse(ProgressionContext.inNonProgressionScope(debugPlayer), "a throwing action cannot leak the scope");
    }
}
