package zcylas.totality.init.events;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.economy.currency.CurrencyComponents;
import zcylas.totality.api.magic.grimoire.rune.RuneComponents;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.api.rpg.combat.PowerAttackManager;
import zcylas.totality.api.rpg.combat.bow.BowStaminaHandler;
import zcylas.totality.api.rpg.combat.exhaustion.ExhaustionManager;
import zcylas.totality.api.rpg.skills.alchemy.AlchemyComponents;
import zcylas.totality.api.rpg.skills.core.MasteriesComponents;
import zcylas.totality.api.rpg.skills.core.SkillsComponents;
import zcylas.totality.api.rpg.stats.StatsComponents;
import zcylas.totality.networking.ability.veinminer.VeinminerKeyHandler;
import zcylas.totality.networking.ancestry.OpenAncestrySelectionPayload;

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
            }
        });

        // ── Cleanup on disconnect ─────────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ExhaustionManager.onPlayerLeave(handler.player);
            BowStaminaHandler.onPlayerLeave(handler.player);
            VeinminerKeyHandler.onPlayerLeave(handler.player);
            PowerAttackManager.onPlayerLeave(handler.player);
        });
    }

    private PlayerConnectionEvents() {}
}