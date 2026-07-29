package zcylas.totality.networking.potion;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.rpgutils.RpgDisplayUtils;
import zcylas.totality.item.potion.dnd.HealingRollResult;
import zcylas.totality.networking.notification.SendNotificationPayload;

/**
 * The healing-roll Notification API adapter: presents a {@link HealingRollResult} — the exact
 * retained roll used to heal a player — as a green HUD notification via the existing
 * {@code NotificationManager} system, showing both the rolled mechanical healing and the actual
 * post-clamp restoration (via {@link SendNotificationPayload}). Independent of combat damage
 * notifications ({@code CombatRollNotification}): healing and pre-mitigation damage are distinct
 * semantics with their own formatting, so this adapter neither reuses nor extends that one.
 *
 * <p>Deliberately player-only (unlike {@code HealingAmount}/{@code HealingPotionItem}, which stay
 * {@code LivingEntity}-generic): this is the one presentation branch the task allows to be
 * player-specific. Never called for a non-player {@code LivingEntity}.
 *
 * <p>e.g. dice-based: {@code "Potion of Healing — 2d4 → [2, 4]\n+2 = 8 (40 HP)  •  Restored 30 HP"}
 * <br>e.g. fixed:      {@code "Potion of Healing — 5 (25 HP)\nRestored 25 HP"}
 */
public final class HealingRollNotification {

    private HealingRollNotification() {}

    /**
     * @param player        the healed player
     * @param label         the healing item's displayed name (e.g. {@code stack.getHoverName()})
     * @param roll          the exact retained roll used to heal {@code player} — never re-rolled here
     * @param actualHealing display-space-convertible vanilla HP actually restored (post max-health
     *                      clamping); caller is responsible for suppressing this call when {@code
     *                      actualHealing <= 0}
     */
    public static void send(ServerPlayer player, String label, HealingRollResult roll, float actualHealing) {
        SendNotificationPayload.send(player, formatMessage(label, roll, actualHealing), SendNotificationPayload.GREEN);
    }

    /**
     * Pure formatting — extracted from {@link #send} so the exact notification text can be unit-
     * tested without a live {@code ServerPlayer}/networking stack. Uses the shared
     * {@link RpgDisplayUtils#toDisplayHp(float)} Health conversion for both the rolled mechanical
     * total and the actual restored amount; introduces no independent display multiplier.
     */
    static String formatMessage(String label, HealingRollResult roll, float actualHealing) {
        int rolledDisplay = RpgDisplayUtils.toDisplayHp((float) roll.total());
        int restoredDisplay = RpgDisplayUtils.toDisplayHp(actualHealing);

        String line1;
        StringBuilder line2 = new StringBuilder();

        if (roll.isDiceBased()) {
            line1 = label + " — " + roll.diceExpression() + " → " + roll.rolls();
            if (roll.modifier() != 0) {
                line2.append(roll.modifier() > 0 ? "+" : "").append(roll.modifier()).append(" = ");
            }
            line2.append(roll.total()).append(" (").append(rolledDisplay).append(" HP)");
        } else {
            line1 = label + " — " + roll.total() + " (" + rolledDisplay + " HP)";
        }

        line2.append(line2.isEmpty() ? "" : "  •  ").append("Restored ").append(restoredDisplay).append(" HP");

        return line1 + "\n" + line2;
    }
}
