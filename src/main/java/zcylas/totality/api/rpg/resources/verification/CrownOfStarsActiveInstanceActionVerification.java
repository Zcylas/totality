package zcylas.totality.api.rpg.resources.verification;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellRegistry;
import zcylas.totality.api.magic.spell.SpellSchool;
import zcylas.totality.api.magic.spell.destruction.CrownOfStarsSpell;
import zcylas.totality.api.rpg.classes.ClassChangeReconciler;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.api.rpg.resources.PartitionSelectionPolicy;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.integration.StandardSpellSlotResources;
import zcylas.totality.networking.ability.ActivateAbilityHandler;
import zcylas.totality.networking.ability.ActivateAbilityPayload;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.UUID;

/**
 * Dev-environment-gated self-test for the Crown of Stars active-instance-action fix
 * (post-Phase-6, 2026-09-16) — proves, through the real production {@code ActivateAbilityHandler
 * .handle} entry point and the real {@code totality:spell_slots} Generic Resource, that firing an
 * already-summoned mote is treated as a follow-up action on the existing crown instance rather
 * than a brand-new 7th-level cast: it requires no slot, spends no slot, and still works even when
 * the initial cast used the player's LAST 7th-level slot — while a genuine second summon (no
 * motes remaining) still requires and consumes one. Mirrors {@code
 * StandardSpellSlotMigrationVerification}'s established pattern (real {@code ServerPlayer} via
 * {@code TotalityFakePlayer}, real {@code ActivateAbilityHandler.handle} calls, real {@code
 * PlayerResourceService} queries) exactly, since this bug lives at the same
 * cast-handler/Generic-Resource boundary.
 *
 * <p>Registration is gated on {@link VerificationReporter#isDevEnvironment()} — a complete no-op
 * in a production build.
 */
public final class CrownOfStarsActiveInstanceActionVerification {

    private static final int SUITE_DELAY_TICKS = 5;

    private CrownOfStarsActiveInstanceActionVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return; // opt-in: runs against the live world
        // Never in a static initializer — see StandardSpellSlotMigrationVerification's own
        // established precedent for why: a static block would run unconditionally as soon as this
        // class is loaded, regardless of isDevEnvironment().
        AbilityRegistry.add(new OrdinaryTestSpell());
        ServerLifecycleEvents.SERVER_STARTED.register(CrownOfStarsActiveInstanceActionVerification::scheduleDelayed);
    }

    private static void scheduleDelayed(MinecraftServer server) {
        ServerScheduler.getInstance().queue(CrownOfStarsActiveInstanceActionVerification::runSelfTest, SUITE_DELAY_TICKS);
    }

    private static void runSelfTest(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "CrownOfStarsActiveInstanceActionVerification");
        ServerLevel level = server.overworld();

        ServerPlayer wizard = TotalityFakePlayer.create(level, "[CrownOfStarsActiveInstanceActionVerification-wizard]");
        int boltsRemoved = 0;
        try {
            UUID uuid = wizard.getUUID();
            // Class level 13: SpellSlotTable.FULL_CASTER row 13 tier 7 = 1 — exactly one 7th-level
            // slot, the "last available slot" scenario the historical bug report described.
            ClassComponents.get(wizard).selectClass(TotalityClasses.WIZARD_ID, 13);
            ClassChangeReconciler.reconcile(wizard); // fresh grant — AtMaximum
            AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) wizard);
            abilities.unlock(SpellRegistry.CROWN_OF_STARS.getId());
            abilities.unlock(OrdinaryTestSpell.ID);

            safe(r, "setup: a level-13 Wizard has exactly one 7th-level Standard Spell Slot before "
                    + "casting Crown of Stars", () -> {
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(7).orElseThrow().currentUnits() == 1
                        && success.snapshot().partition(7).orElseThrow().maximumUnits() == 1;
                return result(pass, "query=" + query);
            });

            safe(r, "1. initial Crown of Stars cast (NEW_CAST — no active motes) consumes exactly one "
                    + "7th-level Standard Spell Slot through the real ActivateAbilityHandler.handle entry "
                    + "point", () -> {
                ActivateAbilityHandler.handle(wizard, new ActivateAbilityPayload(SpellRegistry.CROWN_OF_STARS.getId(), null));
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(7).orElseThrow().currentUnits() == 0;
                return result(pass, "query=" + query);
            });

            safe(r, "2. the initial cast creates the intended 7 motes", () -> {
                boolean pass = CrownOfStarsSpell.hasMotes(uuid) && CrownOfStarsSpell.getCharges(uuid) == CrownOfStarsSpell.TOTAL_MOTES;
                return result(pass, "hasMotes=" + CrownOfStarsSpell.hasMotes(uuid) + ", charges=" + CrownOfStarsSpell.getCharges(uuid));
            });

            safe(r, "3. + 4. firing a mote (ACTIVE_INSTANCE_ACTION — motes remain) does NOT consume "
                    + "another 7th-level slot, and this still works even though the initial cast already "
                    + "used the player's LAST 7th-level slot (currently 0/1)", () -> {
                ActivateAbilityHandler.handle(wizard, new ActivateAbilityPayload(SpellRegistry.CROWN_OF_STARS.getId(), null));
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                boolean stillZero = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(7).orElseThrow().currentUnits() == 0;
                return result(stillZero, "query=" + query);
            });

            safe(r, "5. firing consumed exactly one mote (7 -> 6)", () -> {
                boolean pass = CrownOfStarsSpell.getCharges(uuid) == CrownOfStarsSpell.TOTAL_MOTES - 1;
                return result(pass, "charges=" + CrownOfStarsSpell.getCharges(uuid));
            });

            safe(r, "firing the remaining 5 motes leaves exactly one, all without touching the "
                    + "still-0 7th-level slot", () -> {
                for (int i = 0; i < 5; i++) {
                    ActivateAbilityHandler.handle(wizard, new ActivateAbilityPayload(SpellRegistry.CROWN_OF_STARS.getId(), null));
                }
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = CrownOfStarsSpell.getCharges(uuid) == 1
                        && query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(7).orElseThrow().currentUnits() == 0;
                return result(pass, "charges=" + CrownOfStarsSpell.getCharges(uuid) + ", query=" + query);
            });

            safe(r, "firing the final (7th) mote clears the active crown instance entirely", () -> {
                ActivateAbilityHandler.handle(wizard, new ActivateAbilityPayload(SpellRegistry.CROWN_OF_STARS.getId(), null));
                boolean pass = !CrownOfStarsSpell.hasMotes(uuid) && CrownOfStarsSpell.getCharges(uuid) == 0;
                return result(pass, "hasMotes=" + CrownOfStarsSpell.hasMotes(uuid) + ", charges=" + CrownOfStarsSpell.getCharges(uuid));
            });

            safe(r, "with no active motes and no 7th-level slot available, activating Crown again is a "
                    + "genuine NEW_CAST and is correctly REJECTED (no free re-summon, no negative slot "
                    + "state)", () -> {
                ActivateAbilityHandler.handle(wizard, new ActivateAbilityPayload(SpellRegistry.CROWN_OF_STARS.getId(), null));
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = !CrownOfStarsSpell.hasMotes(uuid)
                        && query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(7).orElseThrow().currentUnits() == 0;
                return result(pass, "hasMotes=" + CrownOfStarsSpell.hasMotes(uuid) + ", query=" + query);
            });

            safe(r, "6. after restoring the 7th-level slot (Long Rest), a genuine second NEW_CAST of "
                    + "Crown still requires and consumes another 7th-level slot, and creates a fresh set "
                    + "of 7 motes", () -> {
                StandardSpellSlotResources.onLongRest(wizard); // tier7: 0/1 -> 1/1
                ActivateAbilityHandler.handle(wizard, new ActivateAbilityPayload(SpellRegistry.CROWN_OF_STARS.getId(), null));
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(7).orElseThrow().currentUnits() == 0
                        && CrownOfStarsSpell.hasMotes(uuid) && CrownOfStarsSpell.getCharges(uuid) == CrownOfStarsSpell.TOTAL_MOTES;
                return result(pass, "query=" + query + ", charges=" + CrownOfStarsSpell.getCharges(uuid));
            });

            safe(r, "7. no other spell's slot-spending behavior changed: an ordinary level-1 spell "
                    + "(which does not override Spell#isActiveInstanceAction, so it inherits the default "
                    + "false — every activation is an ordinary new cast) still consumes exactly one "
                    + "1st-level slot per cast", () -> {
                ResourceQueryResult before = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                long beforeTier1 = before instanceof ResourceQueryResult.PartitionedSuccess s
                        ? s.snapshot().partition(1).orElseThrow().currentUnits() : -1;

                ActivateAbilityHandler.handle(wizard, new ActivateAbilityPayload(OrdinaryTestSpell.ID, null));

                ResourceQueryResult after = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                long afterTier1 = after instanceof ResourceQueryResult.PartitionedSuccess s
                        ? s.snapshot().partition(1).orElseThrow().currentUnits() : -1;
                boolean pass = beforeTier1 > 0 && afterTier1 == beforeTier1 - 1;
                return result(pass, "before=" + before + ", after=" + after);
            });

            safe(r, "repeated activation of the ordinary spell above still requires and consumes a "
                    + "fresh 1st-level slot on every cast (it never became an active-instance action)", () -> {
                ResourceQueryResult before = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                long beforeTier1 = before instanceof ResourceQueryResult.PartitionedSuccess s
                        ? s.snapshot().partition(1).orElseThrow().currentUnits() : -1;

                ActivateAbilityHandler.handle(wizard, new ActivateAbilityPayload(OrdinaryTestSpell.ID, null));

                ResourceQueryResult after = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                long afterTier1 = after instanceof ResourceQueryResult.PartitionedSuccess s
                        ? s.snapshot().partition(1).orElseThrow().currentUnits() : -1;
                boolean pass = beforeTier1 > 0 && afterTier1 == beforeTier1 - 1;
                return result(pass, "before=" + before + ", after=" + after);
            });
        } finally {
            // Firing a mote adds a real SpellBoltEntity to the world. In this suite's non-entity-ticking chunk the
            // bolts never tick (so never expire) and were saved into the world. Remove exactly the bolts this
            // suite's own wizard owns — before the wizard itself is discarded — and nothing else.
            for (var e : com.google.common.collect.Lists.newArrayList(level.getAllEntities())) {   // copy: discard() while iterating
                if (e instanceof zcylas.totality.entity.magic.SpellBoltEntity bolt && !bolt.isRemoved() && bolt.getOwner() == wizard) {
                    bolt.discard();
                    boltsRemoved++;
                }
            }
            wizard.discard();
        }
        r.check("cleanup: every spell bolt fired by this suite's wizard (" + CrownOfStarsSpell.TOTAL_MOTES
                        + " motes) was found and removed", boltsRemoved == CrownOfStarsSpell.TOTAL_MOTES,
                "removed=" + boltsRemoved);

        r.summarize();
    }

    @FunctionalInterface
    private interface CheckBody {
        CheckResult run() throws Exception;
    }

    private record CheckResult(boolean pass, String detail) {}

    private static CheckResult result(boolean pass, String detail) {
        return new CheckResult(pass, detail);
    }

    private static void safe(VerificationReporter r, String label, CheckBody body) {
        try {
            CheckResult outcome = body.run();
            r.check(label, outcome.pass(), outcome.detail());
        } catch (Exception e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** An ordinary level-1 spell that always succeeds and never overrides {@code
     *  isActiveInstanceAction} — a control proving Crown of Stars' fix did not change any other
     *  spell's default (every-activation-is-a-new-cast) slot-spending behavior. */
    private static final class OrdinaryTestSpell extends Spell {
        static final net.minecraft.resources.Identifier ID =
                net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "selftest_crown_control_spell");

        OrdinaryTestSpell() {
            super(ID, "[Self-Test] Crown Control Spell", "Dev-only synthetic spell for the Crown of Stars regression.",
                    Ability.Type.ACTIVE, 1, SpellSchool.DESTRUCTION, false, false, 0,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "textures/ability/selftest.png"), "");
        }

        @Override
        public void onActivate(ServerPlayer player, @org.jspecify.annotations.Nullable AbilityContext context) {
            // Deliberately succeeds every time (never calls markNoEffect()) — an ordinary spell
            // with no active-instance-action override.
        }
    }
}
