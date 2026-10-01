package zcylas.totality.api.entitlement;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Code-owned runtime context of one query (canonical §6.1). The player is nullable so the pure engine
 * can be exercised without a running server; production queries always supply one.
 */
public record EntitlementQueryContext(@Nullable ServerPlayer player, Purpose purpose, EntitlementClock clock) {

    public enum Purpose {
        /** A protected server action is executing now. Never served from cache. */
        SERVER_ENFORCEMENT,
        UI_PREVIEW,
        TOOLTIP,
        DIALOGUE_CONDITION,
        DEBUG_EXPLAIN;

        public boolean clientFacing() {
            return this == UI_PREVIEW || this == TOOLTIP || this == DIALOGUE_CONDITION;
        }
    }
}
