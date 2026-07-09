package zcylas.totality.api.rpg.combat;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.combat.weapon.WeaponDataResolver;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DualWieldCooldownTracker {
    private static final Map<UUID, Long> lastOffhandAttack = new ConcurrentHashMap<>();

    public static boolean canOffhandAttack(ServerPlayer player) {
        Long last = lastOffhandAttack.get(player.getUUID());
        if (last == null) return true;
        // The offhand weapon's own attack speed governs its own swing timing — not the
        // player's aggregate ATTACK_SPEED attribute, which vanilla only ever populates from
        // whatever's in the mainhand.
        double attackSpeed = WeaponDataResolver.resolveAttackSpeed(player, player.getOffhandItem());
        long doubledCooldown = (long) Math.ceil(40.0 / attackSpeed);
        return player.level().getGameTime() - last >= doubledCooldown;
    }

    public static void markOffhandAttack(ServerPlayer player) {
        lastOffhandAttack.put(player.getUUID(), player.level().getGameTime());
    }

    private DualWieldCooldownTracker() {}
}
