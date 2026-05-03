package zcylas.totality.api.alchemy;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Represents a single alchemy effect definition.
 *
 * Each effect has:
 *   - A unique identifier
 *   - A display name
 *   - A base magnitude and duration (used by brewed potions)
 *   - A type (BENEFICIAL / HARMFUL / NEUTRAL)
 *   - onConsume: weak brief effect applied when the raw ingredient is eaten
 *   - onDrink:   full effect applied when the brewed potion is consumed
 *
 * Both callbacks receive the LivingEntity consuming the item.
 * Values inside should be calculated from entity.getMaxHealth() for future-proofing.
 */
public final class AlchemyEffect {

    private static final Map<Identifier, AlchemyEffect> REGISTRY = new HashMap<>();

    private final Identifier id;
    private final String displayName;
    private final float baseMagnitude;
    private final int baseDurationTicks;
    private final AlchemyEffectType type;

    // Optional callbacks — no-op by default
    private Consumer<LivingEntity> onConsume = entity -> {};
    private Consumer<LivingEntity> onDrink   = entity -> {};

    private AlchemyEffect(
            Identifier id,
            String displayName,
            float baseMagnitude,
            int baseDurationTicks,
            AlchemyEffectType type
    ) {
        this.id = id;
        this.displayName = displayName;
        this.baseMagnitude = baseMagnitude;
        this.baseDurationTicks = baseDurationTicks;
        this.type = type;
    }

    public static AlchemyEffect register(
            Identifier id,
            String displayName,
            float baseMagnitude,
            int baseDurationTicks,
            AlchemyEffectType type
    ) {
        if (REGISTRY.containsKey(id)) {
            throw new IllegalStateException("Alchemy effect already registered: " + id);
        }
        AlchemyEffect effect = new AlchemyEffect(id, displayName, baseMagnitude, baseDurationTicks, type);
        REGISTRY.put(id, effect);
        return effect;
    }

    public static AlchemyEffect get(Identifier id) {
        AlchemyEffect effect = REGISTRY.get(id);
        if (effect == null) throw new IllegalStateException("Unknown alchemy effect: " + id);
        return effect;
    }

    // -------------------------------------------------------------------------
    // Callback builders — call these after register() to set behavior
    // -------------------------------------------------------------------------

    /**
     * Sets the effect applied when the raw ingredient is eaten.
     * Should be a weak, brief version of the full effect.
     * Use entity.getMaxHealth() for percentage-based calculations.
     */
    public AlchemyEffect withConsumeEffect(Consumer<LivingEntity> onConsume) {
        this.onConsume = onConsume;
        return this;
    }

    /**
     * Sets the effect applied when the brewed potion is drunk.
     * Should be the full-strength version of the effect.
     * Use entity.getMaxHealth() for percentage-based calculations.
     */
    public AlchemyEffect withDrinkEffect(Consumer<LivingEntity> onDrink) {
        this.onDrink = onDrink;
        return this;
    }

    // -------------------------------------------------------------------------
    // Application
    // -------------------------------------------------------------------------

    /** Called when the raw ingredient is eaten. */
    public void applyConsumeEffect(LivingEntity entity) {
        onConsume.accept(entity);
    }

    /** Called when the brewed potion is drunk. */
    public void applyDrinkEffect(LivingEntity entity) {
        onDrink.accept(entity);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Identifier getId()              { return id; }
    public String getDisplayName()         { return displayName; }
    public float getBaseMagnitude()        { return baseMagnitude; }
    public int getBaseDurationTicks()      { return baseDurationTicks; }
    public AlchemyEffectType getType()     { return type; }
    public boolean isBeneficial()          { return type == AlchemyEffectType.BENEFICIAL; }
    public boolean isHarmful()             { return type == AlchemyEffectType.HARMFUL; }

    @Override
    public String toString() {
        return "AlchemyEffect[" + id + "]";
    }
}