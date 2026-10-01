package zcylas.totality.api.entitlement.integration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.dialogue.DialogueComponents;
import zcylas.totality.api.entitlement.EntitlementQueryContext;
import zcylas.totality.api.entitlement.requirement.EntitlementConditionType;
import zcylas.totality.api.entitlement.requirement.EntitlementDependencyKey;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.api.rpg.classes.ClassComponents;

import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Condition types reading state that existing Totality systems already own. Each is a thin, read-only adapter;
 * the owning system stays authoritative. A missing player (pure-engine evaluation) never satisfies them.
 *
 * <p>Invalidation: class and ancestry changes are reported through {@link #CLASS_DEPENDENCY} and
 * {@link #ANCESTRY_DEPENDENCY}. Narrative flags have no change signal, so that condition is uncacheable.
 */
public final class TotalityConditionTypes {

    public static final EntitlementDependencyKey CLASS_DEPENDENCY = EntitlementDependencyKey.of(id("class"));
    public static final EntitlementDependencyKey ANCESTRY_DEPENDENCY = EntitlementDependencyKey.of(id("ancestry"));

    public record ClassConfig(Identifier classId) {}

    public record ClassLevelConfig(Identifier classId, int level) {}

    public record AncestryConfig(Identifier id) {}

    public record FlagConfig(String flag, int value) {}

    public static final EntitlementConditionType<ClassConfig> HAS_CLASS = type(id("has_class"),
            Identifier.CODEC.fieldOf("class").xmap(ClassConfig::new, ClassConfig::classId),
            (player, c) -> ClassComponents.get(player).hasClass(c.classId()),
            c -> Set.of(CLASS_DEPENDENCY), true);

    public static final EntitlementConditionType<ClassLevelConfig> CLASS_LEVEL_AT_LEAST = type(id("class_level_at_least"),
            RecordCodecBuilder.mapCodec(i -> i.group(
                    Identifier.CODEC.fieldOf("class").forGetter(ClassLevelConfig::classId),
                    Codec.INT.fieldOf("level").forGetter(ClassLevelConfig::level)
            ).apply(i, ClassLevelConfig::new)),
            (player, c) -> ClassComponents.get(player).getClassLevel(c.classId()) >= c.level(),
            c -> Set.of(CLASS_DEPENDENCY), true);

    public static final EntitlementConditionType<AncestryConfig> SPECIES = type(id("species"),
            Identifier.CODEC.fieldOf("species").xmap(AncestryConfig::new, AncestryConfig::id),
            (player, c) -> c.id().equals(AncestryComponents.get(player).getSpeciesId()),
            c -> Set.of(ANCESTRY_DEPENDENCY), true);

    public static final EntitlementConditionType<AncestryConfig> ORIGIN = type(id("origin"),
            Identifier.CODEC.fieldOf("origin").xmap(AncestryConfig::new, AncestryConfig::id),
            (player, c) -> c.id().equals(AncestryComponents.get(player).getOriginId()),
            c -> Set.of(ANCESTRY_DEPENDENCY), true);

    public static final EntitlementConditionType<FlagConfig> NARRATIVE_FLAG_AT_LEAST = type(id("narrative_flag_at_least"),
            RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.STRING.fieldOf("flag").forGetter(FlagConfig::flag),
                    Codec.INT.fieldOf("value").forGetter(FlagConfig::value)
            ).apply(i, FlagConfig::new)),
            (player, c) -> DialogueComponents.FLAGS.get((ComponentProvider) player).getFlag(c.flag()) >= c.value(),
            c -> Set.of(), false);

    private static <C> EntitlementConditionType<C> type(Identifier id, MapCodec<C> codec,
                                                        BiPredicate<ServerPlayer, C> test,
                                                        Function<C, Set<EntitlementDependencyKey>> dependencies,
                                                        boolean cacheable) {
        return new EntitlementConditionType<>() {
            @Override public Identifier id() { return id; }
            @Override public MapCodec<C> codec() { return codec; }

            @Override
            public boolean test(C configuration, EntitlementQueryContext context) {
                return context.player() != null && test.test(context.player(), configuration);
            }

            @Override public Set<EntitlementDependencyKey> dependencies(C configuration) { return dependencies.apply(configuration); }
            @Override public boolean cacheable() { return cacheable; }

            @Override
            public Optional<String> validate(C configuration) {
                return configuration instanceof ClassLevelConfig level && level.level() < 0
                        ? Optional.of("level must not be negative") : Optional.empty();
            }
        };
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private TotalityConditionTypes() {}
}
