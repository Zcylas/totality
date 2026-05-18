package zcylas.totality.api.rpg.skills.alchemy;

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
 *   - An xpValue — how much Alchemy XP this effect contributes when brewed
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

    // XP awarded when this effect appears in a successfully brewed potion.
    // Scaled from Skyrim's base cost values (proportional to gold value).
    // Default 5 — override with withXpValue() on each effect registration.
    private int xpValue = 5;

    // Callbacks
    private Consumer<LivingEntity>             onConsume    = entity -> {};
    private BiConsumer<LivingEntity, Float>    onDrink      = (entity, magnitude) -> {};
    private ObjIntConsumer<LivingEntity>       onDrinkTimed = (entity, ticks) -> {};
    private java.util.function.BiConsumer<LivingEntity, float[]> onDrinkFull = null;
    private java.util.function.BiFunction<Float, Integer, String> descriptionBuilder = null;

    private AlchemyEffect(
            Identifier id,
            String displayName,
            float baseMagnitude,
            int baseDurationTicks,
            AlchemyEffectType type
    ) {
        this.id                = id;
        this.displayName       = displayName;
        this.baseMagnitude     = baseMagnitude;
        this.baseDurationTicks = baseDurationTicks;
        this.type              = type;
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

    // ── XP value ──────────────────────────────────────────────────────────────

    /**
     * Sets how much Alchemy XP this effect contributes when it appears in a brewed potion.
     * Based on Skyrim's base cost hierarchy — rarer/more powerful effects give more XP.
     * Call this immediately after register() in AlchemyEffects.
     */
    public AlchemyEffect withXpValue(int xp) {
        this.xpValue = Math.max(1, xp);
        return this;
    }

    public int getXpValue() { return xpValue; }

    // ── Callback builders ─────────────────────────────────────────────────────

    public AlchemyEffect withConsumeEffect(Consumer<LivingEntity> onConsume) {
        this.onConsume = onConsume;
        return this;
    }

    public AlchemyEffect withDrinkEffect(BiConsumer<LivingEntity, Float> onDrink) {
        this.onDrink = onDrink;
        return this;
    }

    public AlchemyEffect withTimedDrinkEffect(ObjIntConsumer<LivingEntity> onDrinkTimed) {
        this.onDrinkTimed = onDrinkTimed;
        return this;
    }

    public AlchemyEffect withFullDrinkEffect(java.util.function.BiConsumer<LivingEntity, float[]> onDrinkFull) {
        this.onDrinkFull = onDrinkFull;
        return this;
    }

    public AlchemyEffect withDescription(java.util.function.BiFunction<Float, Integer, String> builder) {
        this.descriptionBuilder = builder;
        return this;
    }

    public String buildDescription(float magnitude, int durationTicks) {
        if (descriptionBuilder == null) return displayName + ".";
        return descriptionBuilder.apply(magnitude, durationTicks);
    }

    // ── Application ───────────────────────────────────────────────────────────

    public void applyConsumeEffect(LivingEntity entity) {
        onConsume.accept(entity);
    }

    public void applyDrinkEffect(LivingEntity entity) {
        if (baseDurationTicks > 0) {
            onDrinkTimed.accept(entity, baseDurationTicks);
        } else {
            onDrink.accept(entity, baseMagnitude);
        }
    }

    public void applyConsume(LivingEntity entity, float magnitude, int durationTicks) {
        if (magnitude > 0 && durationTicks > 0 && onDrinkFull != null) {
            onDrinkFull.accept(entity, new float[]{magnitude, durationTicks});
        } else if (durationTicks > 0 && magnitude <= 0) {
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