package zcylas.totality.init.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.combat.damage.DamageResistanceRecalculator;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.dialogue.DialogueComponents;
import zcylas.totality.api.economy.currency.CurrencyComponents;
import zcylas.totality.api.equipment.EquipmentComponents;
import zcylas.totality.api.magic.grimoire.rune.RuneComponents;
import zcylas.totality.api.magic.spell.SpellSlotComponents;
import zcylas.totality.api.magic.spell.SpellSlotRecalculator;
import zcylas.totality.api.quest.QuestManager;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.networking.stamina.StaminaServerTick;
import zcylas.totality.api.rpg.classes.ChargeComponents;
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
            QuestManager.onPlayerJoin(player);
            ClassComponents.PLAYER_CLASS.sync((ComponentProvider) player);
            // Sync stamina so the client HUD shows the correct value immediately
            // rather than defaulting to 100 until the first drain/regen event.
            StaminaServerTick.syncStamina(player);
            // Seed the Stamina depletion state now that authoritative Stamina is loaded (proven
            // by the getStamina() read inside syncStamina() above) — without this, a player
            // rejoining at zero/low Stamina gets a spurious transition notification on the next tick.
            StaminaDepletionManager.onPlayerJoin(player);
            if (ClassComponents.get(player).hasClass(TotalityClasses.BARBARIAN_ID)) {
                var abilities = AbilityComponents.ABILITIES.get((ComponentProvider) player);
                if (!abilities.getUnlocked().contains(
                        AbilityRegistry.BARBARIAN_UNARMORED_DEFENSE.getId())) {
                    abilities.unlock(AbilityRegistry.BARBARIAN_UNARMORED_DEFENSE.getId());
                    AbilityComponents.ABILITIES.sync((ComponentProvider) player);
                }
            }

// Always register charge component as rest listener on join
            RestEventBus.register(player, (p, type) ->
                    ChargeComponents.PLAYER_CHARGES.get((ComponentProvider) p).onRest(p, type));
            RestEventBus.register(player, (p, type) ->
                    AbilityComponents.ABILITIES.get((ComponentProvider) p).onRest(p, type));
            RestEventBus.register(player, (p, type) ->
                    SpellSlotComponents.get(p).onRest(p, type));

            var classComp = ClassComponents.get(player);
            Identifier primaryClass = classComp.getPrimaryClassId();
            if (primaryClass != null) {
                int playerLevel = StatsComponents.getStats(player).getLevel();
                int available   = PlayerClassComponent.toClassLevel(playerLevel);
                int stored      = classComp.getClassLevel(primaryClass);
                if (stored > available) {
                    classComp.setClassLevel(primaryClass, available);
                }
                // Restore all class features for current class level
                int classLevel = classComp.getClassLevel(primaryClass);
                if (classLevel > 0) {
                    ClassFeatureRegistry.onPlayerJoin(player, primaryClass, classLevel);
                }
                SpellSlotRecalculator.recalculate(player);
            }


            server.execute(() -> {
                for (net.minecraft.resources.Identifier id :
                        AbilityComponents.ABILITIES.get((ComponentProvider) player).getUnlocked()) {
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
            DamageResistanceRecalculator.recalculate(newPlayer);
            RestEventBus.clearPlayer(newPlayer.getUUID()); // ← clear first
            RestEventBus.register(newPlayer, (p, type) ->
                    ChargeComponents.PLAYER_CHARGES.get((ComponentProvider) p).onRest(p, type));
            RestEventBus.register(newPlayer, (p, type) ->
                    AbilityComponents.ABILITIES.get((ComponentProvider) p).onRest(p, type));
            RestEventBus.register(newPlayer, (p, type) ->
                    SpellSlotComponents.get(p).onRest(p, type));
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