package zcylas.totality.api.magic.spell;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps a class ID to its {@link CasterProgression}, class-agnostic like
 * {@code UnarmoredDefenseRegistry}/{@code CastingRestrictionRegistry}. A class with no entry
 * here is simply a non-caster and contributes nothing to the shared spell slot pool.
 */
public final class SpellcastingProgressionRegistry {

    private static final Map<Identifier, CasterProgression> PROGRESSIONS = new HashMap<>();

    public static void register(Identifier classId, CasterProgression progression) {
        PROGRESSIONS.put(classId, progression);
    }

    @Nullable
    public static CasterProgression get(Identifier classId) {
        return PROGRESSIONS.get(classId);
    }

    private SpellcastingProgressionRegistry() {}
}
