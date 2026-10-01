package zcylas.totality.api.entitlement;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.entitlement.requirement.DisclosurePolicy;
import zcylas.totality.api.entitlement.requirement.EntitlementRequirement;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.api.entitlement.EntitlementTestFixture.*;
import static zcylas.totality.api.entitlement.requirement.EntitlementRequirement.condition;

class EntitlementDecisionTest {

    private final EntitlementTestFixture f = new EntitlementTestFixture();

    private EntitlementKey app(String path, EntitlementRuleSet rules, boolean visibleByDefault) {
        EntitlementKey key = EntitlementKey.of(APP, id(path));
        f.catalog.registerDefinition(EntitlementDefinition.of(key, "app." + path).withRules(rules)
                .withVisibleByDefault(visibleByDefault));
        return key;
    }

    private EntitlementSuspension suspension(EntitlementKey key, boolean hide, Set<Identifier> actions) {
        return new EntitlementSuspension(UUID.randomUUID(), key, source(QUEST_SOURCE, "story"), actions,
                id("story_lockdown"), Optional.empty(), 0, hide, true);
    }

    @Test
    void decisionsDistinguishEveryAccessState() {
        Identifier tier = f.condition("phone_tier_2", DisclosurePolicy.PUBLIC);
        Identifier quest = f.condition("secret_quest", DisclosurePolicy.SECRET);
        EntitlementKey hidden = app("transit", EntitlementRuleSet.EMPTY.withVisibility(condition(quest)), true);
        EntitlementKey bank = app("bank", EntitlementRuleSet.EMPTY, true);
        EntitlementKey premium = app("premium", EntitlementRuleSet.EMPTY.withPersistentEligibility(condition(tier)), true);
        EntitlementKey store = app("store", EntitlementRuleSet.EMPTY.withAction(EntitlementActions.OPEN_SERVICE, condition(tier)), true);
        f.catalog.registerDefinition(EntitlementDefinition.of(EntitlementKey.of(APP, id("kiosk")), "k")
                .withPolicy(EntitlementPolicies.REQUIREMENTS_ONLY)
                .withRules(EntitlementRuleSet.EMPTY.withAction(EntitlementActions.OPEN_SERVICE, condition(tier))));
        EntitlementKey kiosk = EntitlementKey.of(APP, id("kiosk"));
        f.unlock(premium, source(QUEST_SOURCE, "q"));
        f.unlock(store, source(QUEST_SOURCE, "q"));

        Identifier open = EntitlementActions.OPEN_SERVICE;
        assertEquals(EntitlementDisplayState.HIDDEN, f.ui(hidden, open).displayState());
        assertEquals(EntitlementDecision.Kind.HIDDEN, f.server(hidden, open).kind());
        assertEquals(EntitlementDisplayState.LOCKED, f.ui(bank, open).displayState());
        assertTrue(f.ui(bank, EntitlementActions.VIEW).allowed(), "visible but locked");
        assertEquals(EntitlementDisplayState.KNOWN_UNAVAILABLE, f.ui(premium, open).displayState());
        assertTrue(f.ui(premium, open).snapshot().permanentlyUnlocked(), "eligibility failure does not forget the unlock");
        assertEquals(EntitlementDisplayState.KNOWN_UNAVAILABLE, f.ui(store, open).displayState());
        assertEquals(EntitlementDisplayState.MISSING_REQUIREMENT, f.ui(kiosk, open).displayState());

        f.flags.add("phone_tier_2");
        f.state.bumpRevision(); // the test flag has no owner signal; force re-evaluation
        assertEquals(EntitlementDisplayState.AVAILABLE_PERMANENT, f.ui(premium, open).displayState());
        assertEquals(EntitlementDisplayState.AVAILABLE_PERMANENT, f.ui(store, open).displayState());
        assertTrue(f.ui(kiosk, open).allowed());

        f.engine.addSuspension(f.state, suspension(premium, false, Set.of()), f.clock);
        EntitlementDecision suspended = f.ui(premium, open);
        assertEquals(EntitlementDisplayState.SUSPENDED, suspended.displayState());
        assertTrue(suspended.snapshot().permanentlyUnlocked(), "suspension does not delete progression");
        assertTrue(f.ui(premium, EntitlementActions.VIEW).allowed(), "a disable-suspension keeps the app visible");

        f.engine.reconcileProvider(f.state, id("quests"), Set.of(QUEST_SOURCE),
                List.of(providerGrant("quests", bank, source(QUEST_SOURCE, "banking_stage"))));
        assertEquals(EntitlementDisplayState.AVAILABLE_SOURCE_BOUND, f.ui(bank, open).displayState());
    }

    @Test
    void hideSuspensionHidesEvenHeldContent() {
        EntitlementKey bank = app("bank", EntitlementRuleSet.EMPTY, true);
        f.unlock(bank, source(QUEST_SOURCE, "q"));
        f.engine.addSuspension(f.state, suspension(bank, true, Set.of()), f.clock);
        assertEquals(EntitlementDecision.Kind.HIDDEN, f.ui(bank, EntitlementActions.VIEW).kind());
        assertTrue(f.engine.displayView(f.state, f.context(EntitlementQueryContext.Purpose.UI_PREVIEW)).isEmpty());
    }

    @Test
    void heldContentStaysVisibleAndUsableWhenItsPresentationIsHidden() {
        Identifier discovered = f.condition("codex_discovered", DisclosurePolicy.SECRET);
        EntitlementKey technique = f.ability("hidden_technique");
        f.catalog.registerDefinition(EntitlementDefinition.of(technique, "t")
                .withRules(EntitlementRuleSet.EMPTY.withVisibility(condition(discovered))));
        assertEquals(EntitlementDecision.Kind.HIDDEN, f.server(technique, EntitlementActions.USE).kind());
        f.unlock(technique, source(QUEST_SOURCE, "master"));
        assertTrue(f.allowed(technique), "learned but undiscovered content remains usable through its source");
    }

    @Test
    void staleClientIsDeniedWithCurrentReasonButValidRequestsStillPass() {
        EntitlementKey flight = f.ability("flight");
        f.reconcile("items", Set.of(ITEM_SOURCE), List.of(providerGrant("items", flight, source(ITEM_SOURCE, "ring"))));
        long clientRevision = f.state.revision();
        f.reconcile("items", Set.of(ITEM_SOURCE), List.of()); // ring removed after the client rendered "available"

        EntitlementDecision decision = f.engine.checkServerAction(f.state, flight, EntitlementActions.ACTIVATE,
                f.context(EntitlementQueryContext.Purpose.UI_PREVIEW), OptionalLong.of(clientRevision));
        assertFalse(decision.allowed());
        assertEquals(EntitlementReasons.STALE_CLIENT_STATE, decision.primaryReasonCode());

        f.unlock(flight, source(QUEST_SOURCE, "q"));
        assertTrue(f.engine.checkServerAction(f.state, flight, EntitlementActions.ACTIVATE,
                f.context(EntitlementQueryContext.Purpose.UI_PREVIEW), OptionalLong.of(clientRevision)).allowed(),
                "a revision mismatch alone never rejects a still-valid request");
    }

    @Test
    void cachedDecisionsAreInvalidatedByTheirDependencyOnly() {
        Identifier gate = f.condition("gate", DisclosurePolicy.PUBLIC);
        Identifier other = f.condition("other", DisclosurePolicy.PUBLIC);
        EntitlementKey gated = f.ability("gated");
        EntitlementKey unrelated = f.ability("unrelated");
        f.catalog.registerDefinition(EntitlementDefinition.of(gated, "g")
                .withRules(EntitlementRuleSet.EMPTY.withAction(EntitlementActions.USE, condition(gate))));
        f.catalog.registerDefinition(EntitlementDefinition.of(unrelated, "u")
                .withRules(EntitlementRuleSet.EMPTY.withAction(EntitlementActions.USE, condition(other))));
        f.unlock(gated, source(QUEST_SOURCE, "q"));
        f.unlock(unrelated, source(QUEST_SOURCE, "q"));

        assertFalse(f.ui(gated, EntitlementActions.USE).allowed());
        assertFalse(f.ui(unrelated, EntitlementActions.USE).allowed());
        f.flags.add("gate");
        f.flags.add("other");
        assertFalse(f.ui(gated, EntitlementActions.USE).allowed(), "UI decisions are served from cache until invalidated");
        assertTrue(f.server(gated, EntitlementActions.USE).allowed(), "server enforcement is always evaluated fresh");

        assertTrue(f.state.invalidate(flagDependency("gate")));
        assertTrue(f.ui(gated, EntitlementActions.USE).allowed());
        assertFalse(f.ui(unrelated, EntitlementActions.USE).allowed(), "only decisions reading the changed dependency were dropped");
    }

    @Test
    void accessibleSetIsCachedUntilRevisionChanges() {
        EntitlementKey flight = f.ability("flight");
        EntitlementQueryContext ctx = f.context(EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT);
        assertTrue(f.engine.accessible(f.state, ABILITY, EntitlementActions.USE, ctx).isEmpty());
        f.reconcile("items", Set.of(ITEM_SOURCE), List.of(providerGrant("items", flight, source(ITEM_SOURCE, "ring"))));
        Set<EntitlementKey> first = f.engine.accessible(f.state, ABILITY, EntitlementActions.USE, ctx);
        assertEquals(Set.of(flight), first);
        assertSame(first, f.engine.accessible(f.state, ABILITY, EntitlementActions.USE, ctx), "served from cache");
    }

    @Test
    void displayViewNeverLeaksHiddenContentOrProvenance() {
        Identifier quest = f.condition("secret_quest", DisclosurePolicy.SECRET);
        Identifier tier = f.condition("phone_tier_3", DisclosurePolicy.REDACTED);
        app("dimensional_transit", EntitlementRuleSet.EMPTY.withVisibility(condition(quest)), true);
        EntitlementKey bank = app("bank", EntitlementRuleSet.EMPTY, true);
        EntitlementKey vault = app("vault", EntitlementRuleSet.EMPTY.withAction(EntitlementActions.OPEN_SERVICE, condition(tier)), true);
        f.unlock(vault, source(QUEST_SOURCE, "secret_heist"));

        List<EntitlementDisplaySnapshot> view = f.engine.displayView(f.state, f.context(EntitlementQueryContext.Purpose.UI_PREVIEW));
        assertEquals(Set.of(bank, vault), view.stream().map(EntitlementDisplaySnapshot::key).collect(Collectors.toSet()));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        EntitlementDisplaySnapshot.writeView(buf, f.state.revision(), view);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(0, bytes);
        String wire = new String(bytes, StandardCharsets.ISO_8859_1);
        assertFalse(wire.contains("dimensional_transit"), "hidden content id never reaches the client");
        assertFalse(wire.contains("secret_quest"));
        assertFalse(wire.contains("phone_tier_3"), "redacted condition id never reaches the client");
        assertFalse(wire.contains("secret_heist"), "grant provenance is not part of the view");

        assertEquals(f.state.revision(), buf.readVarLong());
        List<EntitlementDisplaySnapshot> decoded = EntitlementDisplaySnapshot.readViewEntries(buf);
        assertEquals(view, decoded);
    }

    @Test
    void unregisteredContentIsHiddenFromUiAndDeniedOnServer() {
        EntitlementKey ghost = EntitlementKey.of(ABILITY, id("not_registered"));
        assertEquals(EntitlementDecision.Kind.HIDDEN, f.ui(ghost, EntitlementActions.USE).kind());
        EntitlementDecision server = f.server(ghost, EntitlementActions.USE);
        assertEquals(EntitlementDecision.Kind.DENIED, server.kind());
        assertEquals(EntitlementReasons.UNREGISTERED_CONTENT, server.primaryReasonCode());
        EntitlementKey flight = f.ability("flight");
        assertEquals(EntitlementReasons.SERVER_DENIED, f.server(flight, EntitlementActions.OPEN_SERVICE).primaryReasonCode(),
                "unsupported actions are denied");
    }

    @Test
    void lockedDecisionExplainsAcquisitionSubjectToDisclosure() {
        Identifier publicReq = f.condition("banker_dialogue", DisclosurePolicy.PUBLIC);
        Identifier secretReq = f.condition("hidden_faction", DisclosurePolicy.SECRET);
        EntitlementKey bank = app("bank", EntitlementRuleSet.EMPTY.withAcquisition(
                EntitlementRequirement.allOf(condition(publicReq), condition(secretReq))), true);
        EntitlementDecision locked = f.ui(bank, EntitlementActions.OPEN_SERVICE);
        assertEquals(EntitlementReasons.LOCKED, locked.primaryReasonCode());
        EntitlementDisplaySnapshot display = EntitlementDisplaySnapshot.from(locked).orElseThrow();
        assertEquals(1, display.requirements().size());
        assertEquals(Optional.of(publicReq), display.requirements().get(0).conditionId());
    }
}
