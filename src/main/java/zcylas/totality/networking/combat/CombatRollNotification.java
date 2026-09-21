package zcylas.totality.networking.combat;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.dice.DiceBonus;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.combat.AttackRoll;
import zcylas.totality.api.rpg.combat.DamageBonus;
import zcylas.totality.api.rpg.combat.DamageRollResult;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.networking.notification.SendNotificationPayload;

import java.util.List;

/**
 * The combined attack-and-damage roll notification sent to an attacker via the existing
 * {@code NotificationManager} system. Presents the retained {@link AttackRoll.Result} (natural
 * roll(s), ability modifier, proficiency, labeled attack bonuses, total, target AC, outcome) on an
 * {@code ATK} line, and — only when damage was actually rolled, i.e. never on a miss — the optional
 * retained damage-roll breakdown on a {@code DMG} line. Nothing is ever rerolled for presentation;
 * every value shown is read directly from the results {@link #send} was given.
 *
 * <p>At most three explicitly authored semantic lines (label+outcome, ATK, optional DMG); any
 * further rendered-width wrapping of those lines is the central {@code NotificationManager}'s
 * responsibility (Part C), never this class's — no pixel-width guessing or manual line breaks are
 * inserted here.
 *
 * <p>e.g. {@code "Iron Sword — HIT\nATK [12] +3 DEX +2 PROF +3 Bless = 20 vs AC 15\nDMG [6] +3 STR
 * +2 Rage = 11 (55)"}
 */
public final class CombatRollNotification {

    private CombatRollNotification() {}

    /**
     * @param attacker            the attacking player
     * @param label               the weapon/attack label (e.g. a weapon's displayed name, or a
     *                            spell name) — for a thrown Shuriken, this must be the projectile's
     *                            own stored item-stack name, not the attacker's current held item
     * @param attackResult        the retained {@link AttackRoll.Result} — never rerolled here
     * @param damageResult        the retained damage roll, or {@code null} on a miss (no damage was
     *                            rolled)
     * @param damageAbilityScore  the ability score whose modifier contributed to damage, or
     *                            {@code null} when none applies (e.g. a spell with no ability-mod
     *                            damage contribution)
     * @param damageBonuses       labeled damage bonuses (e.g. Rage) — never rerolled here
     */
    public static void send(ServerPlayer attacker,
                            String label,
                            AttackRoll.Result attackResult,
                            @Nullable DamageRollResult damageResult,
                            @Nullable AbilityScore damageAbilityScore,
                            List<DamageBonus> damageBonuses) {
        SendNotificationPayload.send(attacker,
                formatMessage(label, attackResult, damageResult, damageAbilityScore, damageBonuses),
                SendNotificationPayload.GOLD);
    }

    /**
     * Pure formatting — extracted from {@link #send} so the exact notification text can be
     * unit-tested without a live {@code ServerPlayer}/networking stack.
     */
    static String formatMessage(String label,
                                AttackRoll.Result attackResult,
                                @Nullable DamageRollResult damageResult,
                                @Nullable AbilityScore damageAbilityScore,
                                List<DamageBonus> damageBonuses) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(" — ").append(outcomeLabel(attackResult.outcome())).append('\n');
        sb.append(formatAttackLine(attackResult));
        if (damageResult != null) {
            sb.append('\n').append(formatDamageLine(damageResult, damageAbilityScore, damageBonuses));
        }
        return sb.toString();
    }

    private static String outcomeLabel(RollOutcome outcome) {
        return switch (outcome) {
            case CRITICAL_SUCCESS -> "CRITICAL HIT";
            case SUCCESS -> "HIT";
            case FAILURE, CRITICAL_FAILURE -> "MISS";
        };
    }

    private static String formatAttackLine(AttackRoll.Result r) {
        StringBuilder sb = new StringBuilder("ATK [").append(rollNotation(r)).append(']');
        if (r.abilityMod() != 0) {
            sb.append(' ').append(signed(r.abilityMod())).append(' ').append(r.abilityScore().name());
        }
        if (r.proficiencyBonus() != 0) {
            sb.append(' ').append(signed(r.proficiencyBonus())).append(" PROF");
        }
        for (DiceBonus bonus : r.bonuses()) {
            sb.append(' ').append(signed(bonus.value())).append(' ').append(bonus.label());
        }
        sb.append(" = ").append(r.total()).append(" vs AC ").append(r.targetAc());
        return sb.toString();
    }

    /** Compact advantage/disadvantage notation, e.g. {@code "8, 16 → 16"}; just {@code "12"} for a normal roll. */
    private static String rollNotation(AttackRoll.Result r) {
        return r.rollType() == RollType.NORMAL
                ? String.valueOf(r.usedRoll())
                : r.roll1() + ", " + r.roll2() + " → " + r.usedRoll();
    }

    private static String formatDamageLine(DamageRollResult dmg, @Nullable AbilityScore abilityScore, List<DamageBonus> bonuses) {
        StringBuilder sb = new StringBuilder("DMG ").append(dmg.rolls());
        int extraAmount = bonuses.stream().mapToInt(DamageBonus::amount).sum();
        int abilityContribution = dmg.modifier() - extraAmount;
        if (abilityContribution != 0 && abilityScore != null) {
            sb.append(' ').append(signed(abilityContribution)).append(' ').append(abilityScore.name());
        }
        for (DamageBonus bonus : bonuses) {
            sb.append(' ').append(signed(bonus.amount())).append(' ').append(bonus.label());
        }
        int display = Math.round(dmg.total() * zcylas.totality.api.core.rpgutils.RpgDisplayUtils.HP_DISPLAY_MULTIPLIER);
        sb.append(" = ").append(dmg.total()).append(" (").append(display).append(')');
        return sb.toString();
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }
}
