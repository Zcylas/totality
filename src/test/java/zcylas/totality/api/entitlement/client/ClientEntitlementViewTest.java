package zcylas.totality.api.entitlement.client;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.entitlement.EntitlementDisplaySnapshot;
import zcylas.totality.api.entitlement.EntitlementDisplayState;
import zcylas.totality.api.entitlement.EntitlementKey;
import zcylas.totality.api.entitlement.EntitlementReasons;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Correction 4b: the client view never carries a previous server's access across a connection change. */
class ClientEntitlementViewTest {

    private static final EntitlementKey BANK = EntitlementKey.of(Identifier.fromNamespaceAndPath("totality", "phone_app"),
            Identifier.fromNamespaceAndPath("totality", "bank"));

    @AfterEach
    void reset() {
        ClientEntitlementView.clear();
    }

    private static void receive(long revision, List<EntitlementDisplaySnapshot> entries) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        EntitlementDisplaySnapshot.writeView(buf, revision, entries);
        ClientEntitlementView.apply(buf);
    }

    @Test
    void clearForgetsThePreviousSessionsAccess() {
        receive(7, List.of(new EntitlementDisplaySnapshot(BANK, EntitlementDisplayState.AVAILABLE_PERMANENT,
                EntitlementReasons.ALLOWED, true, false, false, List.of(), 7)));
        assertTrue(ClientEntitlementView.isSelectable(BANK));
        assertEquals(7, ClientEntitlementView.revision());

        ClientEntitlementView.clear(); // disconnect / join of another server or world
        assertFalse(ClientEntitlementView.isSelectable(BANK));
        assertEquals(EntitlementDisplayState.HIDDEN, ClientEntitlementView.state(BANK));
        assertEquals(-1, ClientEntitlementView.revision());
    }

    @Test
    void aNewViewReplacesTheOldOneEntirely() {
        receive(3, List.of(new EntitlementDisplaySnapshot(BANK, EntitlementDisplayState.AVAILABLE_PERMANENT,
                EntitlementReasons.ALLOWED, true, false, false, List.of(), 3)));
        receive(1, List.of());
        assertFalse(ClientEntitlementView.isSelectable(BANK), "absent from the new server's view means hidden");
    }
}
