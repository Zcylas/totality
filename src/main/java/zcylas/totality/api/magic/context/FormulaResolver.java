package zcylas.totality.api.magic.context;


import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import zcylas.totality.api.magic.formula.ArcaneFormula;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.rune.AbstractEffectRune;
import zcylas.totality.api.magic.rune.AbstractFormRune;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.api.mana.PlayerManaManager;

import java.util.List;

/**
 * Executes an ArcaneFormula — checks mana, fires the form,
 * and resolves effects at the hit point.
 */
public class FormulaResolver {

    private final FormulaContext context;
    private HitResult hitResult;

    public FormulaResolver(FormulaContext context) {
        this.context = context;
    }

    /**
     * Attempt to cast — called from GrimoireItem.use().
     * Checks mana, then fires the Form rune.
     */
    public boolean tryCast(ItemStack stack, AbstractFormRune.CastResult castResult) {
        ArcaneFormula formula = context.getFormula();
        LivingEntity caster = context.getCaster();

        if (!formula.isValid()) return false;

        int cost = formula.getCost();
        if (!(caster instanceof net.minecraft.world.entity.player.Player player)) return false;
        if (!PlayerManaManager.hasMana(player, cost)) return false;

        PlayerManaManager.removeMana(player, cost);
        return true;
    }

    /**
     * Called by the Form rune when it hits something.
     * Walks the remaining runes and resolves all effects.
     */
    public void onResolveEffect(net.minecraft.world.level.Level level, HitResult hit) {
        if (level.isClientSide()) return;
        this.hitResult = hit;
        resolveEffects(level);
    }

    private void resolveEffects(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;

        ArcaneFormula formula = context.getFormula();
        List<AbstractRune> runes = formula.getRunes();
        LivingEntity caster = context.getCaster();

        // Start from index 1 — index 0 is always the Form
        for (int i = 1; i < runes.size(); i++) {
            AbstractRune rune = runes.get(i);

            // Skip augments — they are consumed by effects
            if (rune instanceof AbstractAugmentRune) continue;

            if (rune instanceof AbstractEffectRune effect) {
                // Collect augments that immediately follow this effect
                List<AbstractAugmentRune> augments = formula.getAugments(i);

                FormulaStats stats = new FormulaStats.Builder()
                        .setAugments(augments, effect)
                        .build();

                effect.onResolve(hitResult, level, caster, stats, context, this);
            }
        }
    }
}