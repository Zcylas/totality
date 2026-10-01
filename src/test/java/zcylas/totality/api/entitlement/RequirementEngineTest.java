package zcylas.totality.api.entitlement;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.entitlement.requirement.DisclosurePolicy;
import zcylas.totality.api.entitlement.requirement.DisplayRequirementSummary;
import zcylas.totality.api.entitlement.requirement.EntitlementConditionDefinition;
import zcylas.totality.api.entitlement.requirement.EntitlementConditionType;
import zcylas.totality.api.entitlement.requirement.EntitlementRequirement;
import zcylas.totality.api.entitlement.requirement.RequirementEvaluation;
import zcylas.totality.api.entitlement.requirement.RequirementEvaluator;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.api.entitlement.EntitlementTestFixture.id;
import static zcylas.totality.api.entitlement.requirement.EntitlementRequirement.*;

class RequirementEngineTest {

    private final EntitlementTestFixture f = new EntitlementTestFixture();
    private final Identifier a = f.condition("a", DisclosurePolicy.PUBLIC);
    private final Identifier b = f.condition("b", DisclosurePolicy.PUBLIC);

    private RequirementEvaluation eval(EntitlementRequirement requirement) {
        return RequirementEvaluator.evaluate(requirement, f.catalog::condition,
                f.context(EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT));
    }

    @Test
    void compoundRequirementsEvaluateCorrectly() {
        assertTrue(eval(allOf()).satisfied(), "empty all_of is true");
        assertFalse(eval(allOf(condition(a), condition(b))).satisfied());
        assertFalse(eval(anyOf(condition(a), condition(b))).satisfied());
        f.flags.add("a");
        assertFalse(eval(allOf(condition(a), condition(b))).satisfied());
        assertTrue(eval(anyOf(condition(a), condition(b))).satisfied());
        assertFalse(eval(not(condition(a), "msg")).satisfied());
        assertTrue(eval(not(condition(b), "msg")).satisfied());
        f.flags.add("b");
        assertTrue(eval(allOf(condition(a), anyOf(condition(b), not(condition(b), "m")))).satisfied());
    }

    @Test
    void allOfReportsEveryDisplayableFailure() {
        RequirementEvaluation result = eval(allOf(condition(a), condition(b)));
        List<RequirementEvaluation> failures = result.failures();
        assertEquals(2, failures.size());
        assertEquals(Optional.of(a), failures.get(0).conditionId());
        assertEquals(Optional.of("test.requirement.a"), failures.get(0).messageTranslationKey());
    }

    @Test
    void missingConditionFailsClosedEvenUnderNot() {
        Identifier missing = id("never_registered");
        RequirementEvaluation leaf = eval(condition(missing));
        assertEquals(RequirementEvaluation.Outcome.ERROR, leaf.outcome());
        assertEquals(EntitlementReasons.INVALID_CONDITION, leaf.reasonCode());
        assertFalse(eval(not(condition(missing), "m")).satisfied(), "Not must not turn a broken condition into a pass");
        assertFalse(eval(allOf(condition(missing))).satisfied());
        f.flags.add("a");
        // A valid alternative may still authorize; the broken branch itself never does.
        assertTrue(eval(anyOf(condition(missing), condition(a))).satisfied());
        assertFalse(eval(anyOf(condition(missing), condition(b))).satisfied());
        assertFalse(leaf.cacheable(), "errors are never cached");
    }

    @Test
    void throwingConditionAndInvalidCompositesFailClosed() {
        EntitlementConditionType<String> throwing = new EntitlementConditionType<>() {
            @Override public Identifier id() { return EntitlementTestFixture.id("throws"); }
            @Override public MapCodec<String> codec() { return Codec.STRING.fieldOf("v"); }
            @Override public boolean test(String c, EntitlementQueryContext context) { throw new IllegalStateException("boom"); }
        };
        f.catalog.registerConditionType(throwing);
        assertTrue(f.catalog.registerCondition(new EntitlementConditionDefinition<>(id("boom"), throwing,
                "x", "m", DisclosurePolicy.PUBLIC)).isEmpty());
        assertEquals(RequirementEvaluation.Outcome.ERROR, eval(condition(id("boom"))).outcome());
        assertFalse(eval(not(condition(id("boom")), "m")).satisfied());
        assertEquals(RequirementEvaluation.Outcome.ERROR, eval(new AnyOf(List.of())).outcome());
        assertFalse(eval(not(new AnyOf(List.of()), "m")).satisfied());

        EntitlementRequirement deep = allOf();
        for (int i = 0; i < RequirementEvaluator.MAX_DEPTH + 2; i++) deep = allOf(deep);
        assertEquals(RequirementEvaluation.Outcome.ERROR, eval(deep).outcome());
    }

    @Test
    void hiddenRequirementDetailsAreRedactedForClients() {
        Identifier secret = f.condition("secret_story", DisclosurePolicy.SECRET);
        Identifier redacted = f.condition("faction_standing", DisclosurePolicy.REDACTED);
        RequirementEvaluation result = eval(allOf(condition(a), condition(redacted), condition(secret)));
        assertEquals(3, result.failures().size(), "the server keeps the full tree");

        List<DisplayRequirementSummary> display = result.displayableFailures();
        assertEquals(2, display.size(), "secret failures are dropped entirely");
        assertEquals(Optional.of(a), display.get(0).conditionId());
        assertEquals(Optional.empty(), display.get(1).conditionId());
        assertEquals(RequirementEvaluation.REDACTED_MESSAGE, display.get(1).messageKey());
        assertFalse(display.toString().contains("secret_story"));
        assertFalse(display.toString().contains("faction_standing"));
        // Inverting a secret keeps it secret.
        f.flags.add("secret_story");
        assertTrue(eval(not(condition(secret), "test.not")).displayableFailures().isEmpty());
    }

    @Test
    void dependenciesAreReportedForTargetedInvalidation() {
        RequirementEvaluation result = eval(anyOf(condition(a), condition(b)));
        assertTrue(result.dependenciesRead().contains(EntitlementTestFixture.flagDependency("a")));
        assertTrue(result.dependenciesRead().contains(EntitlementTestFixture.flagDependency("b")));
        assertTrue(result.cacheable());
    }

    @Test
    void requirementCodecRoundTripsAndRejectsInvalidShapes() {
        EntitlementRequirement tree = allOf(condition(a), anyOf(condition(b), not(condition(a), "test.not")));
        JsonElement json = EntitlementRequirement.CODEC.encodeStart(JsonOps.INSTANCE, tree).getOrThrow();
        assertEquals(tree, EntitlementRequirement.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());

        JsonElement emptyAny = JsonParser.parseString("{\"type\":\"all_of\",\"children\":[{\"type\":\"any_of\",\"children\":[]}]}");
        assertTrue(EntitlementRequirement.CODEC.parse(JsonOps.INSTANCE, emptyAny).isError(), "nested empty any_of is invalid");
        JsonElement unknown = JsonParser.parseString("{\"type\":\"script\",\"code\":\"x\"}");
        assertTrue(EntitlementRequirement.CODEC.parse(JsonOps.INSTANCE, unknown).isError(), "no arbitrary node types");
        JsonElement blankNot = JsonParser.parseString("{\"type\":\"not\",\"failure\":\"\",\"child\":{\"type\":\"condition\",\"id\":\"test:a\"}}");
        assertTrue(EntitlementRequirement.CODEC.parse(JsonOps.INSTANCE, blankNot).isError());
    }
}
