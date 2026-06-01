package zcylas.totality.init.events;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.economy.currency.CurrencyComponents;
import zcylas.totality.api.magic.grimoire.rune.RuneComponents;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.api.rpg.classes.ChargeComponents;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.PlayerClassComponent;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.api.rpg.classes.feature.ClassFeatureRegistry;
import zcylas.totality.api.rpg.combat.DamageBonusRegistry;
import zcylas.totality.api.rpg.combat.PowerAttackManager;
import zcylas.totality.api.rpg.combat.RollModifierRegistry;
import zcylas.totality.api.rpg.combat.bow.BowStaminaHandler;
import zcylas.totality.api.rpg.combat.exhaustion.ExhaustionManager;
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
            AlchemyComponents.KNOWLEDGE.sync((ComponentProvider) player);
            SkillsComponents.PLAYER_SKILLS.sync((ComponentProvider) player);
            MasteriesComponents.PLAYER_MASTERIES.sync((ComponentProvider) player);
            StatsComponents.PLAYER_STATS.sync((ComponentProvider) player);
            ClassComponents.PLAYER_CLASS.sync((ComponentProvider) player);
            if (ClassComponents.get(player).hasClass(TotalityClasses.BARBARIAN_ID)) {
                BarbarianRageAbility.registerChargePool(player);
                ChargeComponents.PLAYER_CHARGES.sync((ComponentProvider) player);
            }

// Always register charge component as rest listener on join
            RestEventBus.register(player, (p, type) ->
                    ChargeComponents.PLAYER_CHARGES.get((ComponentProvider) p).onRest(p, type));

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
                // Has ancestry but no class yet → open class selection
                if (!ClassComponents.get(player).hasAnyClass()) {
                    ServerPlayNetworking.send(player, new OpenClassSelectionPayload());
                }
            }
        });

        // ── Cleanup on disconnect ─────────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RollModifierRegistry.clearPlayer(handler.player.getUUID());
            DamageBonusRegistry.clearPlayer(handler.player.getUUID());
            RestEventBus.clearPlayer(handler.player.getUUID());
            ExhaustionManager.onPlayerLeave(handler.player);
            BowStaminaHandler.onPlayerLeave(handler.player);
            VeinminerKeyHandler.onPlayerLeave(handler.player);
            PowerAttackManager.onPlayerLeave(handler.player);
        });
    }

    private PlayerConnectionEvents() {}
}