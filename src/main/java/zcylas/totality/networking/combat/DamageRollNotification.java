package zcylas.totality.networking.combat;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.core.rpgutils.RpgDisplayUtils;
import zcylas.totality.api.rpg.combat.DamageBonus;
import zcylas.totality.api.rpg.combat.DamageRollResult;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.networking.notification.SendNotificationPayload;

import java.util.List;

/**
 * Sends a damage roll result to the caster as a HUD notification
 * via the existing NotificationManager system.
 *
 * e.g. "Fireball — 8d6 → [4,2,6,1,5,3,2,1] = 24"
 */
public final class DamageRollNotification {

    private DamageRollNotification() {}

    public static void send(ServerPlayer caster, String label,
                            DamageRollResult result,
                            @Nullable AbilityScore abilityScore,
                            List<DamageBonus> extraBonuses) {

        int raw     = result.total();
        int display = Math.round(raw * RpgDisplayUtils.HP_DISPLAY_MULTIPLIER);

        // Line 1: weapon name + dice
        String line1 = label + " — " + result.rolls();

        // Line 2: modifiers + total
        StringBuilder line2 = new StringBuilder();

        if (abilityScore != null) {
            int base = result.modifier() - extraBonuses.stream().mapToInt(DamageBonus::amount).sum();
            if (base != 0)
                line2.append(base > 0 ? "+" : "").append(base)
                        .append(" (").append(abilityScore.getDisplayName()).append(")");
        }

        for (DamageBonus bonus : extraBonuses) {
            if (!line2.isEmpty()) line2.append("  ");
            line2.append(bonus.amount() > 0 ? "+" : "").append(bonus.amount())
                    .append(" (").append(bonus.label()).append(")");
        }

        if (!line2.isEmpty()) line2.append("  =  ");
        line2.append(raw).append(" (").append(display).append(")");

        SendNotificationPayload.send(caster, line1 + "\n" + line2, SendNotificationPayload.GOLD);
    }
}