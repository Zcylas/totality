package zcylas.totality.api.entitlement.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the Entitlement integration points that need a running server to exercise end to end: protected
 * actions revalidate on the server, no direct flat-unlock writes remain, the join order reconciles before
 * syncing, and development spell access is no longer a default.
 */
class EntitlementWiringSourceRegressionTest {

    private static final Path MAIN = Path.of("src/main/java/zcylas/totality");

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative)).replace("\r\n", "\n");
    }

    @Test
    void protectedAbilityActionsRevalidateThroughEntitlement() throws IOException {
        assertTrue(read("networking/ability/ActivateAbilityHandler.java")
                .contains("AbilityEntitlements.check(player, payload.abilityId(), EntitlementActions.ACTIVATE);"));
        assertTrue(read("networking/ability/EquipAbilityHandler.java")
                .contains("AbilityEntitlements.canUse(player, payload.abilityId(), EntitlementActions.EQUIP)"));
        assertTrue(read("networking/ability/SelectSpellHandler.java")
                .contains("AbilityEntitlements.canUse(player, payload.spellId(), EntitlementActions.SELECT)"));
        assertTrue(read("networking/ability/ToggleAbilityHandler.java")
                .contains("payload.active() && !AbilityEntitlements.canUse(player, payload.abilityId(), EntitlementActions.ACTIVATE)"),
                "starting a channel was previously not ownership-checked at all");
        String component = read("api/ability/AbilityComponent.java");
        assertTrue(component.contains("return player != null && AbilityEntitlements.canUse(player, id, EntitlementActions.USE);"),
                "hasAbility is a compatibility adapter over the Entitlement query");
    }

    @Test
    void noProductionCodeWritesTheFlatUnlockSetAnymore() throws IOException {
        Pattern directWrite = Pattern.compile("\\.(unlock|forget)\\((abilityId|id|BarbarianRageAbility|AbilityRegistry|SpellRegistry|NoEffect|AlwaysSucceeds|OrdinaryTestSpell)");
        try (Stream<Path> files = Files.walk(MAIN)) {
            List<String> offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return directWrite.matcher(Files.readString(p)).find();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(Path::toString).toList();
            assertEquals(List.of(), offenders);
        }
        String component = read("api/ability/AbilityComponent.java");
        assertFalse(component.contains("ensureDefaultAbilitiesUnlocked"), "every spell is no longer default-unlocked");
        assertFalse(component.contains("public void unlock("));
        assertTrue(component.contains("for (Identifier id : legacyUnlocked) list.add(id.toString());"),
                "the legacy set is written back unchanged for one transition release");
    }

    @Test
    void ancestryClassAndMasteryChangesReconcileTheirOwnSourceOnly() throws IOException {
        String ancestry = read("networking/ancestry/SelectAncestryHandler.java");
        assertTrue(ancestry.contains("TotalityEntitlements.onAncestryChanged(player);"));
        assertFalse(ancestry.contains("getStartingAbilities"), "no ad hoc origin ability bookkeeping remains");
        assertFalse(read("networking/classes/SelectClassHandler.java").contains("AbilityComponents"));
        assertTrue(read("api/rpg/classes/ClassChangeReconciler.java").contains("TotalityEntitlements.onClassChanged(player);"));
        assertTrue(read("networking/skills/UnlockMasteryHandler.java").contains("TotalityEntitlements.onMasteriesChanged(player);"));
        assertTrue(read("api/rpg/classes/barbarian/BarbarianClass.java").contains("ClassEntitlementGrantProvider.registerClassRule(TotalityClasses.BARBARIAN_ID"));
    }

    @Test
    void joinReconcilesEntitlementsBeforeSyncingAbilities() throws IOException {
        String source = read("init/events/PlayerConnectionEvents.java");
        int join = source.indexOf("ServerPlayConnectionEvents.JOIN.register(");
        int onJoin = source.indexOf("TotalityEntitlements.onJoin(player);", join);
        int abilitySync = source.indexOf("AbilityComponents.ABILITIES.sync((ComponentProvider) player);", join);
        assertTrue(join >= 0 && onJoin > join && abilitySync > onJoin);
        int respawn = source.indexOf("ServerPlayerEvents.AFTER_RESPAWN.register(");
        assertTrue(source.indexOf("TotalityEntitlements.onRespawn(newPlayer);", respawn) > respawn);
        assertFalse(source.contains("abilities.unlock("));
    }

    @Test
    void bankAppUsesThePermanentEntitlementInsteadOfTheAccessFlag() throws IOException {
        String quest = read("api/quest/QuestManager.java");
        assertFalse(quest.contains("setFlag(\"bank_app_unlocked\""));
        assertTrue(quest.contains("PhoneAppEntitlements.unlockBankApp(player, MOBILE_BANKING);"));
        assertTrue(quest.contains("PhoneAppEntitlements.onQuestFullReset(player, questId, template.resetFlags());"));
        String grid = read("screen/phone/PhoneAppGridScreen.java");
        assertFalse(grid.contains("hasFlag(\"bank_app_unlocked\")"));
        assertTrue(grid.contains("ClientEntitlementView.isSelectable(PhoneAppEntitlements.BANK_APP)"));
    }

    @Test
    void clientTabsNoLongerTreatDefaultFlagAsAccess() throws IOException {
        assertFalse(read("screen/character/tabs/SpellsTab.java").contains("s.isDefault() ||"));
        assertFalse(read("screen/character/tabs/AbilitiesTab.java").contains("a.isDefault() ||"));
    }

    @Test
    void debugOnlyExecutionRunsInANonProgressionScope() throws IOException {
        assertTrue(read("networking/ability/ActivateAbilityHandler.java")
                .contains("ProgressionContext.run(player, access.snapshot().debugOnly(), () -> ability.onActivate(player, castContext));"));
        assertTrue(read("networking/ability/ToggleAbilityHandler.java")
                .contains("ProgressionContext.run(player, AbilityEntitlements.isDebugOnly(player, payload.abilityId()),"));
        String tick = read("api/ability/AbilityServerTick.java");
        assertTrue(tick.contains("() -> ability.onPassiveTick(player));"));
        assertTrue(tick.contains("() -> ability.onToggleTick(player));"));
        assertTrue(tick.contains("() -> channeled.onChannel(player, null));"));
        assertTrue(read("Totality.java").contains("() -> ability.onPassiveTick(player));"), "the second passive ticker too");
        assertTrue(read("api/ability/impl/VeinminerAbility.java").contains("() -> awardMiningXp(serverPlayer, state));"));
        assertTrue(read("api/entitlement/integration/TotalityEntitlements.java")
                .contains("ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> ProgressionContext.onEntityLoad(entity));"));
        assertTrue(read("entity/magic/SpellBoltEntity.java").contains("ProgressionContext.runForEntity(this, () -> {"),
                "bolt hits (bolt spells, Crown of Stars motes) inherit the caster's scope");
        assertTrue(read("entity/magic/FireballProjectileEntity.java").contains("ProgressionContext.runForEntity(this, () -> {"),
                "the Fireball blast inherits the caster's scope");
    }

    @Test
    void existingProgressionSinksHonourTheScope() throws IOException {
        assertTrue(read("api/rpg/skills/core/PlayerSkillsComponent.java")
                .contains("if (player != null && ProgressionContext.inNonProgressionScope(player)) return false;"));
        assertTrue(read("api/rpg/skills/core/OneHandedSkillHandler.java")
                .contains("if (ProgressionContext.suppresses(serverPlayer, source)) return;"));
        assertTrue(read("api/quest/QuestManager.java").contains("if (ProgressionContext.inNonProgressionScope(player)) {"));
    }

    @Test
    void invalidationAlwaysRecomputesAvailabilityAndClientsForgetOldSessions() throws IOException {
        assertTrue(read("api/entitlement/EntitlementService.java")
                .contains("state(player).invalidate(changed);\n        afterChange(player);"),
                "a change is reported even when nothing about it was cached");
        String client = read("TotalityClient.java");
        assertTrue(client.contains("ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientEntitlementView.clear());"));
        assertTrue(client.contains("ClientEntitlementView.clear();\n            ClientAbilityManager.clear();"));
    }

    @Test
    void losingTheLastFlightSourceEndsBiologicalFlightThroughTheMovementComponent() throws IOException {
        String passive = read("api/ability/impl/PhysiologyPassive.java");
        assertTrue(passive.contains("MovementComponents.MOVEMENT.get((ComponentProvider) player).endBiologicalFlight();"),
                "biological flight state is cleared with its last source in every game mode");
        assertTrue(read("api/core/movement/PlayerMovementComponent.java").contains(
                "if (player.isCreative() || player.isSpectator()) {\n            this.activelyFlying = false;"),
                "Creative/Spectator keep their own flight permissions");
        assertFalse(passive.contains("getAbilities().mayfly  = false"), "no flag-only grounding that leaves movement state stale");
        String component = read("api/core/movement/PlayerMovementComponent.java");
        assertTrue(component.contains("this.activelyFlying = flying;\n        player.getAbilities().flying = flying;\n"
                + "        player.getAbilities().mayfly = flying;"), "setActivelyFlying keeps state and flags consistent");
        // Flight stamina drain and its regen block are gated only on the movement state the fix clears.
        String stamina = read("networking/stamina/StaminaServerTick.java");
        assertTrue(stamina.contains("if (drainsForBiologicalFlight(player, movement)) {"));
        assertTrue(stamina.contains("|| blocksRegenForBiologicalFlight(player, movement);"));
        assertTrue(stamina.contains("return movement.isActivelyFlying() && !player.isCreative() && !player.isSpectator();"),
                "Spectator never drains for biological flight");
    }
}
