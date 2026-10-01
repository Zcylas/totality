package zcylas.totality.init.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.combat.damage.DamageResistanceRecalculator;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.dialogue.DialogueComponents;
import zcylas.totality.api.economy.currency.CurrencyComponents;
import zcylas.totality.api.entitlement.integration.TotalityEntitlements;
import zcylas.totality.api.equipment.EquipmentComponents;
import zcylas.totality.api.magic.grimoire.rune.RuneComponents;
import zcylas.totality.api.magic.spell.SpellSlotComponents;
import zcylas.totality.api.quest.QuestManager;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.networking.stamina.StaminaServerTick;
import zcylas.totality.api.rpg.classes.ChargeComponents;
import zcylas.totality.api.rpg.classes.ClassChangeReconciler;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.PlayerClassComponent;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.api.rpg.classes.feature.ClassFeatureRegistry;
import zcylas.totality.api.rpg.combat.CastingRestrictionRegistry;
import zcylas.totality.api.rpg.combat.DamageBonusRegistry;
import zcylas.totality.api.rpg.combat.PowerAttackManager;
import zcylas.totality.api.rpg.combat.RollModifierRegistry;
import zcylas.totality.api.rpg.combat.bow.BowStaminaHandler;
import zcylas.totality.api.rpg.combat.stamina.depletion.StaminaDepletionManager;
import zcylas.totality.api.rpg.rest.RestEventBus;
import zcylas.totality.api.rpg.resources.integration.StandardSpellSlotResources;
import zcylas.totality.api.rpg.skills.alchemy.AlchemyComponents;
import zcylas.totality.api.rpg.skills.core.MasteriesComponents;
import zcylas.totality.api.rpg.skills.core.SkillsComponents;
import zcylas.totality.api.rpg.stats.StatsComponents;
import zcylas.totality.networking.ability.veinminer.VeinminerKeyHandler;
import zcylas.totality.networking.ancestry.OpenAncestrySelectionPayload;
import zcylas.totality.networking.classes.OpenClassSelectionPayload;

public class PlayerConnectionEvents {

    public static void register() {
        // ── Ancestry selection on first join ──────────────────────────────────
        // Delayed abilities sync — fires after client is fully connected
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();

            // Entitlements first: rebuild every source-bound grant and run the legacy migrations, so every
            // sync below (abilities included) already reflects the reconciled access state.
            TotalityEntitlements.onJoin(player);

            // Sync all components that have client-side managers
            AbilityComponents.ABILITIES.sync((ComponentProvider) player);
            RuneComponents.KNOWLEDGE.sync((ComponentProvider) player);
            CurrencyComponents.WALLET.sync((ComponentProvider) player);
            EquipmentComponents.EQUIPMENT.sync((ComponentProvider) player);
            DialogueComponents.FLAGS.sync((ComponentProvider) player);
            AlchemyComponents.KNOWLEDGE.sync((ComponentProvider) player);
            SkillsComponents.PLAYER_SKILLS.sync((ComponentProvider) player);
            MasteriesComponents.PLAYER_MASTERIES.sync((ComponentProvider) player);
            StatsComponents.PLAYER_STATS.sync((ComponentProvider) player);
            SpellSlotComponents.SPELL_SLOTS.sync((ComponentProvider) player);
            // Unlike every component above, this one was never synced on JOIN — only on
            // AFTER_RESPAWN (Barbarian-gated, below) and on individual pool mutations. A player
            // reconnecting with an already-granted charge pool (e.g. Rage) therefore kept a client
            // mirror stuck at its just-created empty state until the next mutation/respawn, even
            // though the authoritative server-side pool was already correctly loaded from NBT.
            ChargeComponents.PLAYER_CHARGES.sync((ComponentProvider) player);
            QuestManager.onPlayerJoin(player);
            ClassComponents.PLAYER_CLASS.sync((ComponentProvider) player);
            // Sync stamina so the client HUD shows the correct value immediately
            // rather than defaulting to 100 until the first drain/regen event.
            StaminaServerTick.syncStamina(player);
            // Seed the Stamina depletion state now that authoritative Stamina is loaded (proven
            // by the getStamina() read inside syncStamina() above) — without this, a player
            // rejoining at zero/low Stamina gets a spurious transition notification on the next tick.
            StaminaDepletionManager.onPlayerJoin(player);
            // Barbarian's Unarmored Defense is no longer patched in here: BarbarianClass grants it through
            // the Entitlement API for as long as the character holds the class.

            // Phase 5 Rage migration (2026-09-15): the legacy ChargeComponents.PLAYER_CHARGES
            // .onRest registration that used to live here was removed — totality:rage is now
            // authoritative, and keeping both registered would let the legacy mirror restore Rage
            // independently of the Generic value (a real dual-authority hazard). See
            // BarbarianRageAbility.onShortRest/onLongRest below for the replacement. Phase 6 Standard
            // Spell Slot migration (2026-09-16): the legacy SpellSlotComponent.onRest registration
            // that used to live here was removed for the exact same reason — see
            // StandardSpellSlotResources.onLongRest below for the replacement.
            RestEventBus.register(player, (p, type) ->
                    AbilityComponents.ABILITIES.get((ComponentProvider) p).onRest(p, type));
            // Phase 4 migration: Stamina fully restores on Long Rest only (task §14 / canonical
            // §24.7) — Mana deliberately gets no Rest listener, matching its own characterized
            // absence of any current Rest behavior.
            RestEventBus.register(player, (p, type) -> {
                if (type == zcylas.totality.api.rpg.rest.RestType.LONG) {
                    zcylas.totality.api.rpg.stamina.PlayerStaminaManager.onLongRest(p);
                }
            });
            // Phase 5 migration: Rage restores exactly 1 charge on Short Rest (clamped at maximum),
            // fully restores on Long Rest (task Rest integration / canonical §25.6).
            RestEventBus.register(player, (p, type) -> {
                if (type == zcylas.totality.api.rpg.rest.RestType.SHORT) {
                    BarbarianRageAbility.onShortRest(p);
                } else if (type == zcylas.totality.api.rpg.rest.RestType.LONG) {
                    BarbarianRageAbility.onLongRest(p);
                }
            });
            // Phase 6 migration: Standard Spell Slots fully restore on Long Rest only — canonical
            // Phase 6 scope explicitly excludes Short Rest restoration for the ordinary pool (Pact
            // Magic's own Short Rest restoration is Phase 7 scope).
            RestEventBus.register(player, (p, type) -> {
                if (type == zcylas.totality.api.rpg.rest.RestType.LONG) {
                    StandardSpellSlotResources.onLongRest(p);
                }
            });
            // Phase 7A Health Recovery Dice (2026-09-16; renamed from the working name "Hit Dice"
            // before commit — NOT the future Hit Die API, a separate Character Creation/Progression
            // system): canonical §25.10/§28.9 — Health Recovery Dice fully restore on a valid Long
            // Rest only. There is deliberately no Short Rest listener here — they are SPENT during a
            // Short Rest (a player choice owned by the not-yet-built Rest/Health integration layer),
            // never restored by one.
            RestEventBus.register(player, (p, type) -> {
                if (type == zcylas.totality.api.rpg.rest.RestType.LONG) {
                    zcylas.totality.api.rpg.resources.integration.HealthRecoveryDiceResources.onLongRest(p);
                }
            });

            var classComp = ClassComponents.get(player);
            Identifier primaryClass = classComp.getPrimaryClassId();
            if (primaryClass != null) {
                int playerLevel = StatsComponents.getStats(player).getLevel();
                int available   = PlayerClassComponent.toClassLevel(playerLevel);
                int stored      = classComp.getClassLevel(primaryClass);
                if (stored > available) {
                    // Correction pass (2026-09-16, Finding 1): this mutates PlayerClassComponent —
                    // e.g. after /totality resetlevel/resetstats/resetall lowered the player's
                    // overall level without touching class levels, so a later join finds the
                    // primary class's stored level no longer supportable — but ran AFTER
                    // BaselineResourceLifecycleEvents' own JOIN handler (registered earlier in
                    // ModEvents.register()) already reconciled every class-owned Generic Resource
                    // grant at the STALE, still-high stored level. Without reconciling again here,
                    // a class-owned resource (Rage if primaryClass is Barbarian, totality:spell_slots
                    // if it's a caster class) keeps its old high current against a freshly resolved
                    // lower maximum on the very next query — the same current > maximum shape
                    // ClassChangeReconciler exists to prevent. Universal, not Spell-Slot-specific:
                    // reconcile() re-evaluates every registered grant provider and defensively clamps
                    // every GENERIC_COMPONENT resource (scalar or partitioned) above its resolved
                    // maximum, so Rage/future Ki/Pact Magic/etc. benefit automatically.
                    classComp.setClassLevel(primaryClass, available);
                    ClassChangeReconciler.reconcile(player);
                }
                // Restore all class features for current class level
                int classLevel = classComp.getClassLevel(primaryClass);
                if (classLevel > 0) {
                    ClassFeatureRegistry.onPlayerJoin(player, primaryClass, classLevel);
                }
                // Phase 6 migration: the explicit SpellSlotRecalculator.recalculate(player) call that
                // used to live here was removed — totality:spell_slots' grant (StandardSpellSlotResources)
                // is already reconciled moments earlier by BaselineResourceLifecycleEvents' own JOIN
                // handler (registered before this class in ModEvents.register()), and its maximum is
                // resolved live on every query, so there is nothing left to eagerly recompute here.
            }


            server.execute(() -> {
                for (net.minecraft.resources.Identifier id :
                        AbilityComponents.ABILITIES.get((ComponentProvider) player).getAccessibleAbilities()) {
                    zcylas.totality.api.ability.Ability ability =
                            zcylas.totality.api.ability.AbilityRegistry.get(id);
                    if (ability != null && ability.getType() ==
                            zcylas.totality.api.ability.Ability.Type.PASSIVE) {
                        ability.onPassiveTick(player);
                    }
                }
            });
            // Open ancestry selection if not yet chosen
            if (!AncestryComponents.get(player).hasAncestry()) {
                ServerPlayNetworking.send(player, new OpenAncestrySelectionPayload());
            } else {
                AncestryComponents.get(player).sync();
                player.refreshDimensions();
                // Auto-open disabled for now — class selection is planned to become a quest
                // trigger instead of automatic, re-enable/replace once that's built.
                // if (!ClassComponents.get(player).hasAnyClass()) {
                //     ServerPlayNetworking.send(player, new OpenClassSelectionPayload());
                // }
            }
        });


        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            TotalityEntitlements.onRespawn(newPlayer);
            DamageResistanceRecalculator.recalculate(newPlayer);
            RestEventBus.clearPlayer(newPlayer.getUUID()); // ← clear first
            // Phase 5 Rage / Phase 6 Standard Spell Slot migrations: the legacy ChargeComponents
            // .PLAYER_CHARGES.onRest / SpellSlotComponent.onRest registrations were removed here
            // too — see the JOIN handler above for why.
            RestEventBus.register(newPlayer, (p, type) ->
                    AbilityComponents.ABILITIES.get((ComponentProvider) p).onRest(p, type));
            RestEventBus.register(newPlayer, (p, type) -> {
                if (type == zcylas.totality.api.rpg.rest.RestType.LONG) {
                    zcylas.totality.api.rpg.stamina.PlayerStaminaManager.onLongRest(p);
                }
            });
            RestEventBus.register(newPlayer, (p, type) -> {
                if (type == zcylas.totality.api.rpg.rest.RestType.SHORT) {
                    BarbarianRageAbility.onShortRest(p);
                } else if (type == zcylas.totality.api.rpg.rest.RestType.LONG) {
                    BarbarianRageAbility.onLongRest(p);
                }
            });
            RestEventBus.register(newPlayer, (p, type) -> {
                if (type == zcylas.totality.api.rpg.rest.RestType.LONG) {
                    StandardSpellSlotResources.onLongRest(p);
                }
            });
            RestEventBus.register(newPlayer, (p, type) -> {
                if (type == zcylas.totality.api.rpg.rest.RestType.LONG) {
                    zcylas.totality.api.rpg.resources.integration.HealthRecoveryDiceResources.onLongRest(p);
                }
            });
            if (ClassComponents.get(newPlayer).hasClass(TotalityClasses.BARBARIAN_ID)) {
                BarbarianRageAbility.registerChargePool(newPlayer);
                ChargeComponents.PLAYER_CHARGES.sync((ComponentProvider) newPlayer);
            }
            var classComp = ClassComponents.get(newPlayer);
            Identifier primaryClass = classComp.getPrimaryClassId();
            if (primaryClass != null) {
                ClassFeatureRegistry.onPlayerJoin(newPlayer, primaryClass,
                        classComp.getClassLevel(primaryClass));
            }
        });

        // ── Cleanup on disconnect ─────────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RollModifierRegistry.clearPlayer(handler.player.getUUID());
            DamageBonusRegistry.clearPlayer(handler.player.getUUID());
            RestEventBus.clearPlayer(handler.player.getUUID());
            zcylas.totality.api.rpg.rest.RestSessionManager.clearPlayer(handler.player);
            CastingRestrictionRegistry.clearPlayer(handler.player.getUUID()); // ← add
            StaminaDepletionManager.onPlayerLeave(handler.player);
            BowStaminaHandler.onPlayerLeave(handler.player);
            VeinminerKeyHandler.onPlayerLeave(handler.player);
            PowerAttackManager.onPlayerLeave(handler.player);
            // Release any Dialogue/Trading interaction lock so the NPC doesn't stay frozen
            // staring at empty air after the player is gone.
            if (zcylas.totality.api.dialogue.DialogueSessionManager.isInDialogue(handler.player)) {
                zcylas.totality.api.dialogue.DialogueSessionManager.endDialogue(handler.player);
            }
            if (zcylas.totality.api.shop.TradeSessionManager.isTrading(handler.player)) {
                zcylas.totality.api.shop.TradeSessionManager.endTrade(handler.player);
            }
        });
    }

    private PlayerConnectionEvents() {}
}