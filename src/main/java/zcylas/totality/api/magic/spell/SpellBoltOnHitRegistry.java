package zcylas.totality.api.magic.spell;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Registry for on-hit effects applied by {@link zcylas.totality.entity.magic.SpellBoltEntity}.
 *
 * Effects are registered at mod init and looked up by ID when a bolt hits a target.
 * A null ID means no extra effect (covers most simple damage spells).
 *
 * Example registration (in your spell's init or registry class):
 * <pre>
 *   SpellBoltOnHitRegistry.register(
 *       Identifier.of("totality", "ray_of_frost"),
 *       target -> ConditionRegistry.apply(target, Conditions.SLOW, 2 * 20));
 * </pre>
 */
public final class SpellBoltOnHitRegistry {

    private static final Map<Identifier, Consumer<LivingEntity>> EFFECTS = new HashMap<>();

    private SpellBoltOnHitRegistry() {}

    /** Register an on-hit effect under the given id. */
    public static void register(Identifier id, Consumer<LivingEntity> effect) {
        if (EFFECTS.containsKey(id))
            throw new IllegalStateException("SpellBoltOnHitEffect already registered: " + id);
        EFFECTS.put(id, effect);
    }

    /** Returns the effect for the given id, or null if none registered. */
    @Nullable
    public static Consumer<LivingEntity> get(Identifier id) {
        return EFFECTS.get(id);
    }
}