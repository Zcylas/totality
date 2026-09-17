package zcylas.totality.api.rpg.resources.food;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.ResourceMaximum;
import zcylas.totality.api.rpg.resources.ResourceMaximumResolver;
import zcylas.totality.api.rpg.resources.ResourceResolutionContext;

/**
 * Resolves {@code totality:food}'s maximum. Deliberately registered instead of an
 * {@code authoredBaseMaximum} literal on the definition — canonical §10.2's central resolution path
 * lets a definition-authored base win outright over any registered resolver for {@code SCALAR}
 * resources (see {@link zcylas.totality.api.rpg.resources.PlayerResourceService#resolveMaximum}), so
 * keeping a hardcoded {@code 100} on the definition would silently short-circuit this resolver the
 * same way an authored base would have collapsed {@code ManaMaximumResolver}/
 * {@code StaminaMaximumResolver}/{@code RageMaximumResolver} — the exact same precedent those three
 * resolvers already established.
 *
 * <p>Today this only ever returns the flat baseline (100) — no Origin/Species/effect system exists
 * yet that would ever call for anything else, and none is added by this pass. The point of this
 * class existing at all is architectural: a normal player's Food maximum is a <b>baseline</b>, not a
 * hardcoded ceiling — a future exceptional Origin/Species/transformation/effect can extend this
 * resolver (the same way {@code RageMaximumResolver} reads class level, or a future
 * {@code MaximumModifier} pipeline could apply on top) to legitimately resolve above 100, without
 * touching this resource's definition, authority, or any other Food code. See
 * {@code FoodResourceDefinitionTest}'s variable-maximum tests for a concrete proof (a test-local
 * resolver instance, never registered against the real production registry) that the resolution/
 * clamping/mirror pipeline already correctly handles a resolved maximum above 100 end to end.
 *
 * <p>Per the locked canonical Food model: exceptional high metabolism should primarily be
 * represented by the future Metabolic Reserve/Metabolism direction, not by an enormous Food maximum
 * — a modest exceptional maximum (120, 150) is plausible; a Speedster-style 500 is not what this
 * resolver is for.
 */
public final class FoodMaximumResolver implements ResourceMaximumResolver {

    public static final long BASELINE_MAXIMUM = 100;

    public static final FoodMaximumResolver INSTANCE = new FoodMaximumResolver();

    private FoodMaximumResolver() {}

    @Override
    public ResourceMaximum resolve(ServerPlayer player, PlayerResourceDefinition definition, ResourceResolutionContext context) {
        return ResourceMaximum.Scalar.of(BASELINE_MAXIMUM);
    }
}
