package zcylas.totality.api.ability.impl;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.movement.MovementComponents;
import zcylas.totality.api.core.movement.MovementStaminaCosts;
import zcylas.totality.api.core.movement.PlayerMovementComponent;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.networking.stamina.StaminaServerTick;

public class GroundSlamAbility extends Ability {

    public GroundSlamAbility() {
        super(
                Identifier.fromNamespaceAndPath("totality", "ground_slam"),
                "Ground Slam",
                "While airborne, crash into the ground, damaging nearby enemies and breaking terrain. Damage and crater size scale with fall height and Strength. Constitution reduces self-damage.",
                Type.ACTIVE,
                MovementStaminaCosts.GROUND_SLAM_COOLDOWN_TICKS,
                Identifier.fromNamespaceAndPath("totality", "textures/ability/ground_slam.png"),
                Source.DEFAULT,
                "Default Ability",
                "The ground remembers the weight of heroes."
        );
    }

    @Override
    public boolean isDefault() {
        return true;
    }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext context) {
        if (player.isCreative()) return true;
        if (player.onGround()) return false;

        PlayerMovementComponent movement = MovementComponents.MOVEMENT.get(
                (ComponentProvider) player
        );

        if (movement.isGroundSlamming()) return false;

        return PlayerStaminaManager.hasStamina(
                player,
                MovementStaminaCosts.GROUND_SLAM_COST
        );
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        PlayerMovementComponent movement = MovementComponents.MOVEMENT.get(
                (ComponentProvider) player
        );

        if (!player.isCreative()) {
            PlayerStaminaManager.removeStamina(
                    player,
                    MovementStaminaCosts.GROUND_SLAM_COST
            );
            StaminaServerTick.syncStamina(player);
        }

        movement.setPowerSprinting(false);
        movement.setActivelyFlying(false);
        movement.startGroundSlam(player.getY());

        player.fallDistance = 0.0F;

        player.setDeltaMovement(
                player.getDeltaMovement().x * 0.2D,
                MovementStaminaCosts.GROUND_SLAM_DOWNWARD_VELOCITY,
                player.getDeltaMovement().z * 0.2D
        );

        player.hurtMarked = true;
    }
}