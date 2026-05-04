package zcylas.totality.api.alchemy;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

/**
 * Represents a single alchemy effect definition.
 *
 * Each effect has:
 *   - A unique identifier
 *   - A display name
 *   - A base magnitude and duration (used by brewed potions)
 *   - A type (BENEFICIAL / HARMFUL / NEUTRAL)
 *   - onConsume:     weak brief effect applied when the raw ingredient is eaten
 *   - onDrink:       magnitude-based effect applied when a brewed/registered potion is drunk
 *   - onDrinkTimed:  duration-based effect applied for timed effects (Waterbreathing etc.)
 */
public final class AlchemyEffect {

    private static final Map<Identifier, AlchemyEffect> REGISTRY = new HashMap<>();

    private final Identifier id;
    private final String displayName;
    private final float baseMagnitude;
    private final int baseDurationTicks;
    private final AlchemyEffectType type;

    // Callbacks
    private Consumer<LivingEntity>             onConsume    = entity -> {};
    private BiConsumer<LivingEntity, Float>    onDrink      = (entity, magnitude) -> {};
    private ObjIntConsumer<LivingEntity>       onDrinkTimed = (entity, ticks) -> {};

    private AlchemyEffect(
            Identifier id,
            String displayName,
            float baseMagnitude,
            int baseDurationTicks,
            AlchemyEffectType type
    ) {
        this.id               = id;
        this.displayName      = displayName;
        this.baseMagnitude    = baseMagnitude;
        this.baseDurationTicks = baseDurationTicks;
        this.type             = type;
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

    // ── Callback builders ─────────────────────────────────────────────────────

    /**
     * Called when the raw ingredient is eaten.
     * Should be a weak, brief version of the full effect.
     */
    public AlchemyEffect withConsumeEffect(Consumer<LivingEntity> onConsume) {
        this.onConsume = onConsume;
        return this;
    }

    /**
     * Called when a magnitude-based potion is drunk (Restore Health, Damage Health etc.)
     * magnitude is a fraction of max value (0.0 – 1.0) from MagnitudeTier.
     */
    public AlchemyEffect withDrinkEffect(BiConsumer<LivingEntity, Float> onDrink) {
        this.onDrink = onDrink;
        return this;
    }

    /**
     * Called when a duration-based potion is drunk (Waterbreathing, Invisibility etc.)
     * durationTicks comes from DurationTier.
     */
    public AlchemyEffect withTimedDrinkEffect(ObjIntConsumer<LivingEntity> onDrinkTimed) {
        this.onDrinkTimed = onDrinkTimed;
        return this;
    }

    // ── Application ───────────────────────────────────────────────────────────

    /** Called when the raw ingredient is eaten. */
    public void applyConsumeEffect(LivingEntity entity) {
        onConsume.accept(entity);
    }

    /**
     * Called when the brewed potion is drunk — uses baseMagnitude.
     * For brewed potions where magnitude is derived from ingredient quality.
     */
    public void applyDrinkEffect(LivingEntity entity) {
        if (baseDurationTicks > 0) {
            onDrinkTimed.accept(entity, baseDurationTicks);
        } else {
            onDrink.accept(entity, baseMagnitude);
        }
    }

    /**
     * Called when a registered potion is drunk with explicit magnitude/duration.
     * magnitude  — fraction of max value (from MagnitudeTier), 0 for timed effects
     * durationTicks — ticks (from DurationTier), 0 for instant effects
     */
    public void applyConsume(LivingEntity entity, float magnitude, int durationTicks) {
        if (durationTicks > 0) {
            onDrinkTimed.accept(entity, durationTicks);
        } else {
            onDrink.accept(entity, magnitude);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

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