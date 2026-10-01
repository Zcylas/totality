package zcylas.totality.api.entitlement.integration;

import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.combat.damage.DamageFlags;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.combat.damage.TotalityDamage;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.movement.MovementComponents;
import zcylas.totality.api.core.movement.PlayerMovementComponent;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationMobs;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.dialogue.DialogueComponents;
import zcylas.totality.api.entitlement.EntitlementActions;
import zcylas.totality.api.entitlement.EntitlementCatalog;
import zcylas.totality.api.entitlement.EntitlementComponents;
import zcylas.totality.api.entitlement.EntitlementDecision;
import zcylas.totality.api.entitlement.EntitlementDisplaySnapshot;
import zcylas.totality.api.entitlement.EntitlementDisplayState;
import zcylas.totality.api.entitlement.EntitlementGrant;
import zcylas.totality.api.entitlement.EntitlementKey;
import zcylas.totality.api.entitlement.EntitlementQueryContext;
import zcylas.totality.api.entitlement.EntitlementService;
import zcylas.totality.api.entitlement.GrantSourceRef;
import zcylas.totality.api.entitlement.GrantLifetime;
import zcylas.totality.api.entitlement.GrantSourceTypes;
import zcylas.totality.api.entitlement.PermanentEntitlementFact;
import zcylas.totality.api.entitlement.PlayerEntitlementComponent;
import zcylas.totality.api.entitlement.ProgressionContext;
import zcylas.totality.api.magic.spell.SpellRegistry;
import zcylas.totality.api.quest.QuestManager;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.api.rpg.ancestry.PlayerAncestryComponent;
import zcylas.totality.api.rpg.classes.ClassChangeReconciler;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.api.rpg.combat.weapon.VanillaWeaponTypes;
import zcylas.totality.api.rpg.combat.weapon.WeaponType;
import zcylas.totality.api.rpg.skills.core.Skill;
import zcylas.totality.api.rpg.skills.core.SkillData;
import zcylas.totality.api.rpg.skills.core.SkillsComponents;
import zcylas.totality.networking.ability.ActivateAbilityHandler;
import zcylas.totality.networking.ability.ActivateAbilityPayload;
import zcylas.totality.networking.stamina.StaminaServerTick;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Live-world verification of the Entitlement integration against real {@code ServerPlayer}s, components,
 * NBT and handlers (opt-in, disposable verification server only). Complements the pure JUnit suites.
 */
public final class EntitlementLiveVerification {

    private static final int SUITE_DELAY_TICKS = 5;
    private static final String SUITE = "EntitlementLiveVerification";

    private EntitlementLiveVerification() {}

    private static final Identifier PROGRESSION_STRIKE = Identifier.fromNamespaceAndPath("totality", "selftest_entitlement_strike");
    private static final Identifier VERIFICATION = Identifier.fromNamespaceAndPath("totality", "verification");
    private static Zombie strikeTarget;
    private static Snowball lastSnowball;

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return;
        // Registered only on the disposable verification server (CrownOfStars precedent).
        AbilityRegistry.add(new StrikeAbility());
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                ServerScheduler.getInstance().queue(EntitlementLiveVerification::run, SUITE_DELAY_TICKS));
    }

    private static Identifier t(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private static void check(VerificationReporter r, String label, BooleanSupplier body, String detail) {
        try {
            r.check(label, body.getAsBoolean(), detail);
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e);
        }
    }

    private static void run(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, SUITE);
        EntitlementService service = EntitlementService.INSTANCE;
        ServerPlayer p = TotalityFakePlayer.create(server.overworld(), "[" + SUITE + "]");
        try {
            check(r, "catalog: Totality types are registered and the catalog validates",
                    () -> EntitlementCatalog.INSTANCE.type(AbilityEntitlements.ABILITY_TYPE).isPresent()
                            && EntitlementCatalog.INSTANCE.type(AbilityEntitlements.SPELL_TYPE).isPresent()
                            && EntitlementCatalog.INSTANCE.type(PhoneAppEntitlements.PHONE_APP_TYPE).isPresent()
                            && EntitlementCatalog.INSTANCE.validate().isEmpty(), "" + EntitlementCatalog.INSTANCE.validate());

            service.reconcileAll(p);
            service.baselineAvailability(p);
            AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) p);
            Identifier fireball = SpellRegistry.FIREBALL.getId();

            check(r, "baseline: Harvest, Rest and Ground Slam are granted by the character baseline",
                    () -> abilities.getAccessibleAbilities().containsAll(List.of(t("harvest"), t("rest"), t("ground_slam"))),
                    "" + abilities.getAccessibleAbilities());
            check(r, "development spells are NOT available by default",
                    () -> !DebugSpellAccessProvider.isEnabled() && !AbilityEntitlements.canUse(p, fireball, EntitlementActions.ACTIVATE),
                    "" + AbilityEntitlements.check(p, fireball, EntitlementActions.ACTIVATE));

            // ── Debug universal spell access ──
            DebugSpellAccessProvider.setEnabled(server, true);
            service.reconcileProvider(p, DebugSpellAccessProvider.ID); // fake players are not in the player list
            EntitlementDecision debug = AbilityEntitlements.check(p, fireball, EntitlementActions.ACTIVATE);
            check(r, "debug toggle: spells become usable, flagged debug-only and non-progression",
                    () -> debug.allowed() && debug.snapshot().debugOnly()
                            && debug.successfulPaths().stream().noneMatch(path -> path.progressionEligible()), "" + debug);
            check(r, "debug access cannot be converted into permanent knowledge",
                    () -> service.convertGrantToPermanent(p, service.state(p).providerGrants().get(DebugSpellAccessProvider.ID)
                            .keySet().iterator().next(), PermanentEntitlementFact.KNOWN, t("study")).isRejected(), "");
            DebugSpellAccessProvider.setEnabled(server, false);
            service.reconcileProvider(p, DebugSpellAccessProvider.ID);
            check(r, "disabling debug access removes it completely and left no permanent fact",
                    () -> !AbilityEntitlements.canUse(p, fireball, EntitlementActions.ACTIVATE)
                            && service.state(p).ledger().keysWithFacts().isEmpty(), "");

            // ── Class grants through the real class mutation seam ──
            ClassComponents.get(p).selectClass(TotalityClasses.BARBARIAN_ID, 1);
            ClassChangeReconciler.reconcile(p);
            EntitlementDecision rage = AbilityEntitlements.check(p, BarbarianRageAbility.ID, EntitlementActions.ACTIVATE);
            check(r, "Barbarian class grants Rage with class provenance",
                    () -> rage.allowed() && rage.snapshot().sources().stream()
                            .anyMatch(s -> s.source().equals(GrantSourceRef.of(GrantSourceTypes.CLASS, TotalityClasses.BARBARIAN_ID))), "" + rage);
            check(r, "Barbarian class grants Unarmored Defense",
                    () -> abilities.getAccessibleAbilities().contains(AbilityRegistry.BARBARIAN_UNARMORED_DEFENSE.getId()), "");
            ClassComponents.get(p).resetClass();
            ClassChangeReconciler.reconcile(p);
            check(r, "removing the class removes only class grants (baseline untouched)",
                    () -> !AbilityEntitlements.canUse(p, BarbarianRageAbility.ID, EntitlementActions.ACTIVATE)
                            && abilities.getAccessibleAbilities().contains(t("harvest")), "" + abilities.getAccessibleAbilities());

            // ── Origin grants, multi-source and active-ability shutdown ──
            PlayerAncestryComponent ancestry = AncestryComponents.get(p);
            ancestry.clearAncestry();
            ancestry.selectAncestry(t("kryptonian"), t("kryptonian"));
            TotalityEntitlements.onAncestryChanged(p);
            Identifier heatVision = t("heat_vision");
            check(r, "Kryptonian origin grants Heat Vision and Kryptonian Physiology with origin provenance",
                    () -> abilities.getAccessibleAbilities().containsAll(List.of(heatVision, t("kryptonian_physiology")))
                            && AbilityEntitlements.check(p, heatVision, EntitlementActions.USE).snapshot().sources().get(0).source()
                            .equals(GrantSourceRef.of(GrantSourceTypes.ORIGIN, t("kryptonian"))), "" + abilities.getAccessibleAbilities());
            abilities.startChanneling(heatVision);
            service.grantPermanentFact(p, AbilityEntitlements.keyFor(t("kryptonian_physiology")), PermanentEntitlementFact.UNLOCKED,
                    EntitlementCommands.ADMIN_SOURCE, EntitlementCommands.ADMIN_COMMAND, false);

            ancestry.clearAncestry();
            ancestry.selectAncestry(t("viltrumite"), t("pureblood_viltrumite"));
            TotalityEntitlements.onAncestryChanged(p);
            check(r, "changing origin removes the old origin's grants and adds the new one's",
                    () -> !abilities.getAccessibleAbilities().contains(heatVision)
                            && abilities.getAccessibleAbilities().contains(t("viltrumite_physiology")), "" + abilities.getAccessibleAbilities());
            check(r, "losing the last path stops the active channel (Ability system reaction)",
                    () -> !abilities.isChanneling(), "channeling=" + abilities.getChannelingAbility());
            check(r, "an independent permanent path survives the origin change",
                    () -> AbilityEntitlements.canUse(p, t("kryptonian_physiology"), EntitlementActions.USE), "");
            service.revokePermanentFact(p, AbilityEntitlements.keyFor(t("kryptonian_physiology")), PermanentEntitlementFact.UNLOCKED,
                    EntitlementCommands.ADMIN_SOURCE, EntitlementCommands.ADMIN_COMMAND);
            check(r, "audited revocation removes the remaining path",
                    () -> !AbilityEntitlements.canUse(p, t("kryptonian_physiology"), EntitlementActions.USE), "");

            // ── Real NBT persistence of the entitlement component ──
            service.grantPermanentFact(p, PhoneAppEntitlements.BANK_APP, PermanentEntitlementFact.UNLOCKED,
                    GrantSourceRef.of(GrantSourceTypes.QUEST, QuestManager.MOBILE_BANKING), PhoneAppEntitlements.QUEST_REWARD_METHOD, true);
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
            EntitlementComponents.get(p).writeData(out);
            PlayerEntitlementComponent loaded = new PlayerEntitlementComponent(null);
            loaded.readData(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), out.buildResult()));
            check(r, "NBT round trip keeps permanent facts, revision and audit log; provider grants are not saved",
                    () -> loaded.state().ledger().facts(PhoneAppEntitlements.BANK_APP).equals(
                            service.state(p).ledger().facts(PhoneAppEntitlements.BANK_APP))
                            && loaded.state().revision() == service.state(p).revision()
                            && loaded.state().ledger().audit().equals(service.state(p).ledger().audit())
                            && loaded.state().providerGrants().isEmpty(), "");

            // ── Display view and ability sync payload ──
            List<EntitlementDisplaySnapshot> view = service.displayView(p);
            check(r, "display view shows the unlocked Bank app as permanently available",
                    () -> view.stream().anyMatch(s -> s.key().equals(PhoneAppEntitlements.BANK_APP)
                            && s.state() == EntitlementDisplayState.AVAILABLE_PERMANENT), "" + view);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            abilities.writeSyncPacket(buf, p);
            int syncedCount = buf.readInt();
            check(r, "ability sync carries exactly the accessible set",
                    () -> syncedCount == abilities.getAccessibleAbilities().size(), syncedCount + " vs " + abilities.getAccessibleAbilities());
            buf.release();

            // ── Bank flag migration and admin quest reset ──
            service.revokePermanentFact(p, PhoneAppEntitlements.BANK_APP, PermanentEntitlementFact.UNLOCKED,
                    EntitlementCommands.ADMIN_SOURCE, EntitlementCommands.ADMIN_COMMAND);
            DialogueComponents.FLAGS.get((ComponentProvider) p).setFlag(PhoneAppEntitlements.LEGACY_BANK_FLAG, 1);
            PhoneAppEntitlements.migrateLegacyBankFlag(p);
            long revisionAfterMigration = service.state(p).revision();
            PhoneAppEntitlements.migrateLegacyBankFlag(p);
            check(r, "legacy bank_app_unlocked migrates once to a permanent Bank app unlock",
                    () -> service.isAllowed(p, PhoneAppEntitlements.BANK_APP, EntitlementActions.OPEN_SERVICE)
                            && service.state(p).revision() == revisionAfterMigration, "");
            QuestManager.fullReset(p, QuestManager.MOBILE_BANKING);
            EntitlementDecision bankAfterReset = service.query(p, PhoneAppEntitlements.BANK_APP, EntitlementActions.OPEN_SERVICE,
                    EntitlementQueryContext.Purpose.UI_PREVIEW);
            check(r, "admin quest full reset revokes the Bank app; it stays visible but locked",
                    () -> bankAfterReset.displayState() == EntitlementDisplayState.LOCKED, "" + bankAfterReset.displayState());

            // ── Legacy ability migration from a crafted pre-Entitlement save ──
            TagValueOutput legacy = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
            var unlocked = legacy.list("unlocked", Codec.STRING);
            for (String id : List.of("totality:fireball", "totality:harvest", "totality:veinminer", "totality:removed_in_update")) {
                unlocked.add(id);
            }
            var favorites = legacy.list("favorites", Codec.STRING);
            favorites.add("totality:fireball");
            favorites.add("totality:harvest");
            legacy.putString("selectedSpell", "totality:fireball");
            abilities.readData(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), legacy.buildResult()));
            Map<LegacyAbilityMigration.Outcome, List<Identifier>> report = LegacyAbilityMigration.apply(p);
            Map<LegacyAbilityMigration.Outcome, List<Identifier>> second = LegacyAbilityMigration.apply(p);
            check(r, "legacy migration: default spell ignored, baseline explained, stale mastery not permanent, unknown quarantined",
                    () -> report.get(LegacyAbilityMigration.Outcome.IGNORED_DEVELOPMENT_SPELL).contains(fireball)
                            && report.get(LegacyAbilityMigration.Outcome.SOURCE_BOUND_EXPLAINED).contains(t("harvest"))
                            && report.get(LegacyAbilityMigration.Outcome.STALE_SOURCE_BOUND).contains(t("veinminer"))
                            && report.get(LegacyAbilityMigration.Outcome.UNKNOWN_QUARANTINED).contains(t("removed_in_update"))
                            && service.state(p).ledger().keysWithFacts().stream().noneMatch(AbilityEntitlements::isAbilityKey)
                            && service.state(p).ledger().orphaned().containsKey(AbilityEntitlements.keyFor(t("removed_in_update"))), "" + report);
            check(r, "legacy migration is idempotent (only the unresolvable id is re-examined)",
                    () -> second.keySet().equals(Set.of(LegacyAbilityMigration.Outcome.UNKNOWN_QUARANTINED)), "" + second);
            check(r, "favorites and spell selection are preserved, not deleted",
                    () -> abilities.getFavorites().equals(List.of(fireball, t("harvest"))) && fireball.equals(abilities.getSelectedSpell()),
                    abilities.getFavorites() + " / " + abilities.getSelectedSpell());
        } finally {
            DebugSpellAccessProvider.setEnabled(server, false);
            p.discard();
        }
        runProgressionChecks(r, server);
        runFlightChecks(r, server);
        r.summarize();
    }

    /** A self-test ability shaped like the instant-damage spells (Magic Missile, Disintegrate): one
     *  {@code TotalityDamage.hurt} hit attributed to the caster, plus one owned projectile. */
    private static final class StrikeAbility extends Ability {
        StrikeAbility() {
            super(PROGRESSION_STRIKE, "Entitlement self-test strike", "", Type.ACTIVE, 0,
                    Identifier.fromNamespaceAndPath("totality", "textures/ability/selftest.png"), Source.DEFAULT, "", "");
        }

        @Override
        public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
            ServerLevel level = (ServerLevel) player.level();
            strikeTarget.invulnerableTime = 0;
            TotalityDamage.hurt(strikeTarget, player, DamageTypes.FORCE, 1.0f, DamageFlags.MAGICAL);
            lastSnowball = new Snowball(level, player, new ItemStack(Items.SNOWBALL));
            level.addFreshEntity(lastSnowball);
        }
    }

    private static long progress(ServerPlayer player, Skill skill) {
        SkillData data = SkillsComponents.get(player).getSkills().getData(skill);
        return data.getLevel() * 1_000_000L + data.getXp();
    }

    /** Correction 3: debug-only execution earns no progression; the same action through a legitimate grant does. */
    private static void runProgressionChecks(VerificationReporter r, MinecraftServer server) {
        EntitlementService service = EntitlementService.INSTANCE;
        ServerLevel level = server.overworld();
        ServerPlayer q = TotalityFakePlayer.create(level, "[" + SUITE + "-progression]");
        Zombie zombie = VerificationMobs.lootlessZombie(level);
        List<Snowball> snowballs = new ArrayList<>();
        try {
            q.setGameMode(GameType.SURVIVAL);
            Item oneHanded = BuiltInRegistries.ITEM.stream()
                    .filter(i -> VanillaWeaponTypes.getType(i) == WeaponType.ONE_HANDED).findFirst().orElse(null);
            check(r, "setup: a one-handed weapon exists for the One-Handed XP handler", () -> oneHanded != null, "");
            if (oneHanded == null) return;
            q.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(oneHanded));
            zombie.setPos(q.getX() + 1, q.getY(), q.getZ());
            level.addFreshEntity(zombie);
            strikeTarget = zombie;
            EntitlementKey strike = AbilityEntitlements.keyFor(PROGRESSION_STRIKE);
            GrantSourceRef debugSource = GrantSourceRef.of(GrantSourceTypes.DEBUG, VERIFICATION);

            service.addGrant(q, EntitlementGrant.debugSession(strike, VERIFICATION));
            float healthBefore = zombie.getHealth();
            long before = progress(q, Skill.ONE_HANDED);
            ActivateAbilityHandler.handle(q, new ActivateAbilityPayload(PROGRESSION_STRIKE, null));
            snowballs.add(lastSnowball);
            Snowball debugSnowball = lastSnowball;
            check(r, "debug-only activation runs (damage dealt) but awards no One-Handed XP",
                    () -> zombie.getHealth() < healthBefore && progress(q, Skill.ONE_HANDED) == before,
                    "health " + healthBefore + "->" + zombie.getHealth() + ", progress " + before + "->" + progress(q, Skill.ONE_HANDED));
            check(r, "a projectile spawned by the debug-only action inherits the non-progression context",
                    () -> ProgressionContext.isNonProgression(debugSnowball), "snowball=" + debugSnowball);
            // The later hit of a spawned entity (how SpellBoltEntity / FireballProjectileEntity deal damage).
            zombie.invulnerableTime = 0;
            ProgressionContext.runForEntity(debugSnowball,
                    () -> TotalityDamage.hurt(zombie, q, DamageTypes.FORCE, 1.0f, DamageFlags.MAGICAL));
            check(r, "a later hit by that projectile, outside the cast, still awards no progression",
                    () -> progress(q, Skill.ONE_HANDED) == before, "" + progress(q, Skill.ONE_HANDED));
            long mining = progress(q, Skill.MINING);
            ProgressionContext.runNonProgression(q.getUUID(), () -> SkillsComponents.get(q).addSkillXp(Skill.MINING, 50));
            check(r, "skill XP awarded synchronously inside the scope is refused",
                    () -> progress(q, Skill.MINING) == mining, "");

            // Control: the very same action through a legitimate (non-debug) grant does award progression.
            service.removeGrant(q, EntitlementGrant.debugSession(strike, VERIFICATION).grantId(), debugSource);
            service.addGrant(q, new EntitlementGrant(UUID.randomUUID(), strike,
                    GrantSourceRef.of(GrantSourceTypes.QUEST, VERIFICATION), VERIFICATION, Set.of(),
                    GrantLifetime.SESSION, Optional.empty(), 0, false, true));
            ActivateAbilityHandler.handle(q, new ActivateAbilityPayload(PROGRESSION_STRIKE, null));
            snowballs.add(lastSnowball);
            check(r, "control: the same activation through a legitimate grant awards One-Handed XP",
                    () -> progress(q, Skill.ONE_HANDED) > before, before + " -> " + progress(q, Skill.ONE_HANDED));
            check(r, "control: its projectile is not marked non-progression",
                    () -> !ProgressionContext.isNonProgression(lastSnowball), "");
            long afterControl = progress(q, Skill.ONE_HANDED);
            zombie.invulnerableTime = 0;
            Snowball controlSnowball = lastSnowball;
            ProgressionContext.runForEntity(controlSnowball,
                    () -> TotalityDamage.hurt(zombie, q, DamageTypes.FORCE, 1.0f, DamageFlags.MAGICAL));
            check(r, "control: a later hit by a legitimately cast projectile awards XP",
                    () -> progress(q, Skill.ONE_HANDED) > afterControl, afterControl + " -> " + progress(q, Skill.ONE_HANDED));
        } finally {
            snowballs.forEach(Entity::discard);
            zombie.discard();
            q.discard();
        }
    }

    /**
     * Flight state on physiology loss (R2 / R3 / R4 follow-ups): biological flight, its stamina drain and its regen
     * block end with the last flight source in every game mode; Creative/Spectator permissions, another flight
     * source and Elytra gliding are unaffected. Uses the real {@code StaminaServerTick} gates.
     */
    private static void runFlightChecks(VerificationReporter r, MinecraftServer server) {
        ServerPlayer f = TotalityFakePlayer.create(server.overworld(), "[" + SUITE + "-flight]");
        try {
            EntitlementService.INSTANCE.reconcileAll(f);
            EntitlementService.INSTANCE.baselineAvailability(f);
            PlayerAncestryComponent ancestry = AncestryComponents.get(f);
            PlayerMovementComponent movement = MovementComponents.MOVEMENT.get((ComponentProvider) f);

            // ── Normal Survival flight and an alternative source ──
            f.setGameMode(GameType.SURVIVAL);
            setOrigin(f, ancestry, "kryptonian", "kryptonian");
            movement.setActivelyFlying(true);
            check(r, "Survival biological flight drains stamina and blocks regen",
                    () -> StaminaServerTick.drainsForBiologicalFlight(f, movement)
                            && StaminaServerTick.blocksRegenForBiologicalFlight(f, movement), "");
            setOrigin(f, ancestry, "viltrumite", "pureblood_viltrumite");
            check(r, "switching to another flight physiology preserves active flight (movement state, flags, drain)",
                    () -> movement.isActivelyFlying() && f.getAbilities().flying && f.getAbilities().mayfly
                            && StaminaServerTick.drainsForBiologicalFlight(f, movement), flightState(f, movement));
            clearOrigin(f, ancestry);
            check(r, "losing the last flight source in Survival ends biological flight, its drain and its regen block",
                    () -> survivalGrounded(f, movement), flightState(f, movement));

            // ── Spectator and Creative: lose the last source, then return to Survival ──
            for (GameType mode : List.of(GameType.SPECTATOR, GameType.CREATIVE)) {
                f.setGameMode(GameType.SURVIVAL);
                setOrigin(f, ancestry, "kryptonian", "kryptonian");
                movement.setActivelyFlying(true);
                f.setGameMode(mode);
                boolean flyingBefore = f.getAbilities().flying;
                check(r, mode + " with biological flight active: no flight stamina drain",
                        () -> !StaminaServerTick.drainsForBiologicalFlight(f, movement), flightState(f, movement));
                clearOrigin(f, ancestry);
                check(r, mode + ": losing the last flight physiology ends biological flight state (no drain, no regen block)",
                        () -> !movement.isActivelyFlying() && !StaminaServerTick.drainsForBiologicalFlight(f, movement)
                                && !StaminaServerTick.blocksRegenForBiologicalFlight(f, movement), flightState(f, movement));
                check(r, mode + ": game-mode flight permissions are untouched",
                        () -> f.getAbilities().mayfly && f.getAbilities().flying == flyingBefore, flightState(f, movement));
                f.setGameMode(GameType.SURVIVAL);
                check(r, mode + " -> Survival: no unauthorized biological flight, drain or regen block persists",
                        () -> survivalGrounded(f, movement), flightState(f, movement));
            }

            // ── Elytra gliding is not biological flight ──
            f.setGameMode(GameType.SURVIVAL);
            f.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            setOrigin(f, ancestry, "kryptonian", "kryptonian");
            f.startFallFlying();
            clearOrigin(f, ancestry);
            check(r, "Elytra gliding continues when a flight physiology is lost",
                    () -> f.isFallFlying() && !StaminaServerTick.drainsForBiologicalFlight(f, movement), flightState(f, movement));
            f.stopFallFlying();
        } finally {
            f.discard();
        }
    }

    private static void setOrigin(ServerPlayer player, PlayerAncestryComponent ancestry, String species, String origin) {
        ancestry.clearAncestry();
        ancestry.selectAncestry(t(species), t(origin));
        TotalityEntitlements.onAncestryChanged(player);
    }

    private static void clearOrigin(ServerPlayer player, PlayerAncestryComponent ancestry) {
        ancestry.clearAncestry();
        TotalityEntitlements.onAncestryChanged(player);
    }

    private static boolean survivalGrounded(ServerPlayer player, PlayerMovementComponent movement) {
        return !movement.isActivelyFlying() && !player.getAbilities().flying && !player.getAbilities().mayfly
                && !StaminaServerTick.drainsForBiologicalFlight(player, movement)
                && !StaminaServerTick.blocksRegenForBiologicalFlight(player, movement);
    }

    private static String flightState(ServerPlayer player, PlayerMovementComponent movement) {
        return "activelyFlying=" + movement.isActivelyFlying() + " flying=" + player.getAbilities().flying
                + " mayfly=" + player.getAbilities().mayfly + " fallFlying=" + player.isFallFlying()
                + " drains=" + StaminaServerTick.drainsForBiologicalFlight(player, movement)
                + " blocksRegen=" + StaminaServerTick.blocksRegenForBiologicalFlight(player, movement);
    }
}
