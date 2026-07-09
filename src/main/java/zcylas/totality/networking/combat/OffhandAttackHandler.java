package zcylas.totality.networking.combat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.combat.CombatResolver;
import zcylas.totality.api.rpg.combat.DualWieldCooldownTracker;
import zcylas.totality.api.rpg.combat.PowerAttackManager;
import zcylas.totality.api.rpg.combat.weapon.TotalityMeleeWeaponItem;
import zcylas.totality.api.rpg.combat.weapon.VanillaWeaponTypes;
import zcylas.totality.api.rpg.combat.weapon.WeaponDataResolver;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.networking.stamina.StaminaServerTick;

/** Skyrim-style independent offhand attack — triggered by RMB while dual-wielding, not auto-mirrored from LMB. */
public final class OffhandAttackHandler {

    private static final double MAX_ATTACK_RANGE_SQ = 4.5 * 4.5;

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(OffhandAttackPayload.TYPE,
                (payload, ctx) -> ctx.server().execute(() -> handle(ctx.player(), payload)));
    }

    private static void handle(ServerPlayer player, OffhandAttackPayload payload) {
        if (!isDualWielding(player)) return;
        if (!DualWieldCooldownTracker.canOffhandAttack(player)) return;

        Entity targetEntity = player.level().getEntity(payload.targetEntityId());
        if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) return;
        if (player.distanceToSqr(target) > MAX_ATTACK_RANGE_SQ) return;

        ItemStack offWeapon = player.getOffhandItem();

        // Offhand's own hold-to-charge power attack — rolls with advantage, costs extra stamina.
        // Gracefully downgrades to a normal roll if stamina is insufficient (mirrors the
        // mainhand's PowerAttackManager.onPowerAttackReceived behavior: the swing always
        // happens, only the advantage bonus is denied).
        boolean usePower = payload.powerAttack()
                && (player.isCreative() || PlayerStaminaManager.hasStamina(player, PowerAttackManager.getOffhandStaminaCost(player)));
        RollType rollType = usePower ? RollType.ADVANTAGE : RollType.NORMAL;

        WeaponDataResolver.Resolved offData = WeaponDataResolver.resolve(player, target, offWeapon, rollType);

        CombatResolver.resolveAttack(player, target, offData.ability(), offData.proficient(), offData.rollType(),
                offData.diceCount(), offData.damageDie(), offData.damageType(), offWeapon.getHoverName().getString());

        if (!offWeapon.isEmpty()) {
            offWeapon.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
        }

        if (!player.isCreative()) {
            int cost = usePower ? PowerAttackManager.getOffhandStaminaCost(player) : VanillaWeaponTypes.getAttackCost(offWeapon);
            PlayerStaminaManager.removeStamina(player, cost);
            StaminaServerTick.syncStamina(player);
        }

        DualWieldCooldownTracker.markOffhandAttack(player);
    }

    private static boolean isDualWielding(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean mainIsMelee = main.is(ItemTags.SWORDS) || main.getItem() instanceof TotalityMeleeWeaponItem;
        boolean offIsMelee = off.is(ItemTags.SWORDS) || off.getItem() instanceof TotalityMeleeWeaponItem;
        return mainIsMelee && offIsMelee;
    }

    private OffhandAttackHandler() {}
}
