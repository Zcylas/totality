package zcylas.totality.api.mining;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Who/what produced a {@link MiningImpact}. The actor is optional so machines, spells and explosions fit. */
public record MiningSource(Kind kind, @Nullable Entity actor) {

    public enum Kind { PLAYER_TOOL, BARE_HANDS, MOB, COMPANION, MACHINE, SPELL, IMPACT, EXPLOSION, OTHER }

    public static MiningSource of(Kind kind, @Nullable Entity actor) {
        return new MiningSource(kind, actor);
    }
}
