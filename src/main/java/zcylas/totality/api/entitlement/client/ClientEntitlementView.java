package zcylas.totality.api.entitlement.client;

import net.minecraft.network.FriendlyByteBuf;
import zcylas.totality.api.entitlement.EntitlementDisplaySnapshot;
import zcylas.totality.api.entitlement.EntitlementDisplayState;
import zcylas.totality.api.entitlement.EntitlementKey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The client's advisory copy of its own entitlement display view. It only ever contains what the server
 * chose to reveal; absence means hidden. Nothing here authorizes anything — the server revalidates every
 * protected action.
 */
public final class ClientEntitlementView {

    private static Map<EntitlementKey, EntitlementDisplaySnapshot> entries = Map.of();
    private static long revision = -1;

    private ClientEntitlementView() {}

    public static void apply(FriendlyByteBuf buf) {
        long newRevision = buf.readVarLong();
        List<EntitlementDisplaySnapshot> list = EntitlementDisplaySnapshot.readViewEntries(buf);
        Map<EntitlementKey, EntitlementDisplaySnapshot> map = new LinkedHashMap<>();
        for (EntitlementDisplaySnapshot entry : list) map.put(entry.key(), entry);
        entries = Map.copyOf(map);
        revision = newRevision;
    }

    public static Optional<EntitlementDisplaySnapshot> get(EntitlementKey key) {
        return Optional.ofNullable(entries.get(key));
    }

    public static EntitlementDisplayState state(EntitlementKey key) {
        EntitlementDisplaySnapshot entry = entries.get(key);
        return entry == null ? EntitlementDisplayState.HIDDEN : entry.state();
    }

    public static boolean isSelectable(EntitlementKey key) {
        EntitlementDisplaySnapshot entry = entries.get(key);
        return entry != null && entry.selectable();
    }

    public static long revision() {
        return revision;
    }

    public static void clear() {
        entries = Map.of();
        revision = -1;
    }
}
