package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.entitlement.requirement.DisclosurePolicy;
import zcylas.totality.api.entitlement.requirement.EntitlementConditionDefinition;
import zcylas.totality.api.entitlement.requirement.EntitlementRequirement;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.api.entitlement.EntitlementTestFixture.*;

class EntitlementIdentityAndCatalogTest {

    @Test
    void sameContentIdUnderDifferentTypesNeverCollides() {
        EntitlementTestFixture f = new EntitlementTestFixture();
        EntitlementKey ability = f.ability("flight");
        EntitlementKey spell = f.spell("flight");
        assertNotEquals(ability, spell);
        assertEquals(ability.contentId(), spell.contentId());

        f.reconcile("class_provider", Set.of(CLASS_SOURCE), List.of(providerGrant("class_provider", ability, source(CLASS_SOURCE, "wizard"))));
        assertTrue(f.allowed(ability));
        assertFalse(f.allowed(spell), "a grant for the ability type must not authorize the spell with the same content id");
        assertNotEquals(EntitlementGrant.deterministicId(id("p"), source(CLASS_SOURCE, "x"), ability),
                EntitlementGrant.deterministicId(id("p"), source(CLASS_SOURCE, "x"), spell));
    }

    @Test
    void newTypesActionsAndSourcesRegisterWithoutTouchingCore() {
        EntitlementTestFixture f = new EntitlementTestFixture();
        Identifier newAction = id("attune");
        Identifier newType = id("technique");
        Identifier newSource = id("research_institute");
        f.catalog.registerAction(newAction);
        f.catalog.registerSourceType(newSource);
        f.catalog.registerType(EntitlementTypeDefinition.builder(newType, id("research"))
                .actions(newAction).policy(EntitlementPolicies.KNOWN_OR_GRANT)
                .content(c -> c.getPath().equals("kata")).build());
        EntitlementKey kata = EntitlementKey.of(newType, id("kata"));

        assertTrue(f.catalog.isRegisteredContent(kata));
        assertFalse(f.catalog.isRegisteredContent(EntitlementKey.of(newType, id("unknown"))));
        assertEquals(EntitlementReasons.NOT_KNOWN, f.server(kata, newAction).primaryReasonCode());
        f.reconcile("institute", Set.of(newSource), List.of(providerGrant("institute", kata, source(newSource, "academy"))));
        assertTrue(f.server(kata, newAction).allowed());
        assertTrue(f.catalog.validate().isEmpty(), f.catalog.validate().toString());
    }

    @Test
    void registrationRejectsDuplicatesAndUnregisteredActions() {
        EntitlementTestFixture f = new EntitlementTestFixture();
        assertThrows(IllegalStateException.class, () -> f.catalog.registerType(
                EntitlementTypeDefinition.builder(ABILITY, id("dup")).build()));
        assertThrows(IllegalStateException.class, () -> f.catalog.registerType(
                EntitlementTypeDefinition.builder(id("bad"), id("x")).actions(id("never_registered")).build()));
        assertThrows(IllegalStateException.class, () -> f.catalog.registerDefinition(
                EntitlementDefinition.of(EntitlementKey.of(id("no_type"), id("x")), "k")));
    }

    @Test
    void invalidConditionDefinitionIsNotRegisteredAndItsReferenceFailsClosed() {
        EntitlementTestFixture f = new EntitlementTestFixture();
        Optional<String> problem = f.catalog.registerCondition(new EntitlementConditionDefinition<>(id("blank"), f.flagType,
                new EntitlementTestFixture.FlagConfig(" "), "msg", DisclosurePolicy.PUBLIC));
        assertTrue(problem.isPresent());
        assertTrue(f.catalog.condition(id("blank")).isEmpty());

        EntitlementKey key = f.ability("needs_blank");
        f.catalog.registerDefinition(EntitlementDefinition.of(key, "k").withRules(EntitlementRuleSet.EMPTY
                .withAction(EntitlementActions.USE, EntitlementRequirement.condition(id("blank")))));
        f.unlock(key, source(QUEST_SOURCE, "q"));
        assertFalse(f.server(key, EntitlementActions.USE).allowed());
        assertTrue(f.catalog.validate().stream().anyMatch(p -> p.contains("unregistered condition")));
    }

    @Test
    void validationReportsStructuralProblems() {
        EntitlementTestFixture f = new EntitlementTestFixture();
        EntitlementKey key = f.ability("broken");
        f.catalog.registerDefinition(EntitlementDefinition.of(key, "k")
                .withPolicy(id("no_such_policy"))
                .withRules(EntitlementRuleSet.EMPTY
                        .withVisibility(new EntitlementRequirement.AnyOf(List.of()))
                        .withAction(EntitlementActions.OPEN_SERVICE, EntitlementRequirement.allOf())));
        List<String> problems = f.catalog.validate();
        assertTrue(problems.stream().anyMatch(p -> p.contains("no_such_policy")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("empty any_of")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("unsupported action")), problems.toString());
        // An unregistered policy denies instead of falling back to something permissive.
        f.unlock(key, source(QUEST_SOURCE, "q"));
        assertEquals(EntitlementReasons.SERVER_DENIED, f.server(key, EntitlementActions.USE).primaryReasonCode());
    }

    @Test
    void providersMustDeclareRegisteredSourceTypes() {
        EntitlementTestFixture f = new EntitlementTestFixture();
        assertThrows(IllegalStateException.class, () -> f.catalog.registerProvider(new EntitlementGrantProvider() {
            @Override public Identifier providerId() { return id("p"); }
            @Override public Set<Identifier> sourceTypeIds() { return Set.of(id("undeclared")); }
            @Override public void collectGrants(ServerPlayer player, Collector collector) {}
        }));
    }
}
