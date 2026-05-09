package zcylas.totality.api.rpg.stats;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import zcylas.totality.api.core.rpgutils.RpgDisplayUtils;

/**
 * Applies ability score modifiers to vanilla attributes.
 * Called on player join, respawn, level up, and point allocation —
 * NOT every tick, since stats change very rarely.
 *
 * Current mappings:
 *   CON → Attributes.MAX_HEALTH  (each CON modifier point = +2 vanilla HP = +10 display HP)
 *
 * Future mappings (add here when skills are implemented):
 *   STR → Attributes.ATTACK_DAMAGE
 *   DEX → Attributes.ATTACK_SPEED
 */
public final class StatAttributeApplier {

    private static final Identifier CON_HP_ID =
            Identifier.fromNamespaceAndPath("totality", "con_max_health");

    private StatAttributeApplier() {}

    /**
     * Applies all stat-driven attribute modifiers to the player.
     * Safe to call multiple times — addOrUpdateTransientModifier handles duplicates.
     */
    public static void apply(ServerPlayer player) {
        PlayerStats stats = StatsComponents.getStats(player);
        applyConHp(player, stats);
        // Future: applyStrDamage(player, stats);
        // Future: applyDexSpeed(player, stats);
    }

    /**
     * Removes all stat-driven attribute modifiers from the player.
     * Called before reapplying to ensure clean state.
     */
    public static void remove(ServerPlayer player) {
        var hpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null) hpAttr.removeModifier(CON_HP_ID);
    }

    // ── CON → MAX_HEALTH ──────────────────────────────────────────────────────

    private static void applyConHp(ServerPlayer player, PlayerStats stats) {
        var hpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr == null) return;

        double bonus = RpgDisplayUtils.conModifierToVanillaHp(
                stats.getModifier(AbilityScore.CON));

        // At CON 10 (modifier 0) → bonus 0 → max HP stays at vanilla 20 = 100 display
        // At CON 12 (modifier 1) → bonus +2 vanilla → max HP 22 = 110 display
        // At CON 8  (modifier -1) → bonus -2 vanilla → max HP 18 = 90 display
        hpAttr.addOrUpdateTransientModifier(new AttributeModifier(
                CON_HP_ID,
                bonus,
                AttributeModifier.Operation.ADD_VALUE
        ));
    }
}