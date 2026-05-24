package zcylas.totality.api.magic.grimoire.context;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import zcylas.totality.api.magic.grimoire.formula.ArcaneFormula;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.grimoire.rune.AbstractEffectRune;
import zcylas.totality.api.magic.grimoire.rune.AbstractFormRune;
import zcylas.totality.api.magic.grimoire.rune.AbstractRune;
import zcylas.totality.api.rpg.mana.PlayerManaManager;

import java.util.List;

public class FormulaResolver {

    private final FormulaContext context;
    private HitResult hitResult;

    public FormulaResolver(FormulaContext context) {
        this.context = context;
    }

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
     * Resets the context and resolves all effects.
     */
    public void onResolveEffect(net.minecraft.world.level.Level level, HitResult hit) {
        if (level.isClientSide()) return;
        this.hitResult = hit;
        context.reset();
        // Start from 1 if there's a form rune, 0 if this is a child context
        resolveEffectsFrom(level, context.hasForm() ? 1 : 0);
    }

    /**
     * Called by effects that want to re-resolve at a new hit point
     * without resetting the index (e.g. chained effects).
     */
    public void onResolveEffect(net.minecraft.world.level.Level level, HitResult hit, int fromIndex) {
        if (level.isClientSide()) return;
        this.hitResult = hit;
        resolveEffectsFrom(level, fromIndex); // use fromIndex directly, no reset
    }

    private void resolveEffects(net.minecraft.world.level.Level level) {
        // Skip the form rune at index 0
        resolveEffectsFrom(level, 1);
    }

    private void resolveEffectsFrom(net.minecraft.world.level.Level level, int startIndex) {
        if (level.isClientSide()) return;
        if (context.isCanceled()) return;

        ArcaneFormula formula = context.getFormula();
        List<AbstractRune> runes = formula.getRunes();
        LivingEntity caster = context.getCaster();
        for (int i = startIndex; i < runes.size(); i++) {
            AbstractRune rune = runes.get(i);
            if (rune instanceof AbstractAugmentRune) continue;
            if (context.isCanceled()) return;

            if (rune instanceof AbstractEffectRune effect) {
                List<AbstractAugmentRune> augments = formula.getAugments(i);
                FormulaStats stats = new FormulaStats.Builder()
                        .setAugments(augments, effect)
                        .build();

                effect.onResolve(hitResult, level, caster, stats, context, this);
                if (context.isCanceled()) return;
            }

        }
    }

    public HitResult getHitResult() { return hitResult; }
    public FormulaContext getContext() { return context; }
}