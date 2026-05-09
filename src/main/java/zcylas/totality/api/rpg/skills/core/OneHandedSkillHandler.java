package zcylas.totality.api.rpg.skills.core;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.rpg.combat.weapon.VanillaWeaponTypes;
import zcylas.totality.api.rpg.combat.weapon.WeaponType;

/**
 * Awards One-Handed XP when the player deals damage with a one-handed weapon.
 * XP scales with actual damage dealt (after armor, effects etc.) via AFTER_DAMAGE.
 * Formula: max(1, damageTaken * 2)
 */
public class OneHandedSkillHandler {

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(
                (entity, source, baseDamageTaken, damageTaken, killed) -> {
                    if (!(source.getEntity() instanceof ServerPlayer serverPlayer)) return;
                    if (serverPlayer.isCreative()) return;

                    ItemStack mainHand = serverPlayer.getMainHandItem();
                    WeaponType type = VanillaWeaponTypes.getType(mainHand.getItem());

                    if (type == WeaponType.ONE_HANDED) {
                        // Scale XP with actual damage dealt after armor/effects
                        int xp = Math.max(1, (int)(damageTaken * 2));
                        SkillsComponents.get(serverPlayer)
                                .addSkillXp(Skill.ONE_HANDED, xp);
                    }
                }
        );
    }

    private OneHandedSkillHandler() {}
}