package zcylas.totality.api.magic.grimoire.context;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.magic.grimoire.formula.ArcaneFormula;
import zcylas.totality.api.magic.grimoire.rune.AbstractRune;

import java.util.List;

/**
 * Runtime state of a formula being cast.
 * Tracks the current position in the rune sequence.
 */
public class FormulaContext {

    private final ArcaneFormula formula;
    private final Level level;
    private final LivingEntity caster;
    private final ItemStack casterStack;
    private int currentIndex = 0;
    private boolean canceled = false;

    public FormulaContext(Level level, ArcaneFormula formula,
                          LivingEntity caster, ItemStack casterStack) {
        this.level = level;
        this.formula = formula;
        this.caster = caster;
        this.casterStack = casterStack.copy();
    }

    public ArcaneFormula getFormula() { return formula; }
    public Level getLevel() { return level; }
    public LivingEntity getCaster() { return caster; }
    public ItemStack getCasterStack() { return casterStack; }
    public int getCurrentIndex() { return currentIndex; }
    public boolean isCanceled() { return canceled; }
    public void cancel() { this.canceled = true; }

    public boolean hasNextRune() {
        return !canceled && currentIndex < formula.size();
    }

    @Nullable
    public AbstractRune nextRune() {
        if (!hasNextRune()) return null;
        return formula.getRunes().get(currentIndex++);
    }

    public void reset() {
        currentIndex = 0;
        canceled = false;
    }

    /**
     * Creates a new context starting from the given index.
     * Equivalent to AN's makeChildContext() — the new context's formula
     * only contains runes from startIndex onwards.
     */
    private boolean hasForm = true;

    public FormulaContext makeChildContext(int startIndex) {
        List<AbstractRune> remaining = new java.util.ArrayList<>(
                formula.getRunes().subList(
                        Math.min(startIndex, formula.getRunes().size()),
                        formula.getRunes().size()));
        ArcaneFormula childFormula = new ArcaneFormula(remaining);
        FormulaContext child = new FormulaContext(level, childFormula, caster, casterStack);
        child.hasForm = false; // child contexts start from index 0
        return child;
    }

    public boolean hasForm() { return hasForm; }
}