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

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(OffhandAttackPayload.TYPE,
                (payload, ctx) -> ctx.server().execute(() -> handle(ctx.player(), payload)));
    }

    private static void handle(ServerPlayer player, OffhandAttackPayload payload) {
        if (!isDualWielding(player)) return;
        if (!DualWieldCooldownTracker.canOffhandAttack(player)) return;

        // Shared target-validity predicate (correction pass, Part C) — the same reach/liveness/
        // attackability rule Power Attack uses server-side (PowerAttackManager.MELEE_TARGET_RANGE
        // is this exact reach value; the offhand path was its original source, preserved unchanged
        // by sharing it rather than duplicating a second copy that could drift).
        Entity targetEntity = player.level().getEntity(payload.targetEntityId());
        if (!PowerAttackManager.isValidTarget(player, targetEntity)) return;
        LivingEntity target = (LivingEntity) targetEntity;

        ItemStack offWeapon = player.getOffhandItem();

        // Offhand's own hold-to-charge power attack — rolls with advantage, costs extra stamina.
        // Gracefully downgrades to a normal roll if Stamina is insufficient OR the attacker is not
        // currently legally permitted to attack this target at all (PvP-disabled, team
        // friendly-fire, invulnerability — correction pass, Part D: mainhand and offhand share the
        // identical PowerAttackManager.isAttackerLegal gate, checked BEFORE any Power-Attack-
        // specific Stamina is committed below) — the swing always happens, only the advantage
        // bonus and its extra Stamina cost are denied.
        boolean usePower = payload.powerAttack()
                && PowerAttackManager.isAttackerLegal(player, target)
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
