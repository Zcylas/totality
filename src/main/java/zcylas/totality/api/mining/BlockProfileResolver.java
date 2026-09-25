package zcylas.totality.api.mining;

import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.mining.BlockProfile.Form;
import zcylas.totality.api.mining.BlockProfile.Layer;
import zcylas.totality.api.mining.BlockProfile.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Layered Block/Material Profile resolution, generic over the state type {@code S} and the block key {@code K} so
 * the precedence rules are unit-testable without a Minecraft bootstrap ({@link BlockProfiles} binds it to
 * {@code BlockState}/{@code Block}).
 *
 * <p>Each field resolves independently to the first non-null value, in this order:
 * <ol>
 *   <li>exact block/state override (first registered matching predicate for that block);</li>
 *   <li>exact block profile;</li>
 *   <li>authored material + form profile;</li>
 *   <li>material-family default;</li>
 *   <li>the compatibility fallback, which must supply every field.</li>
 * </ol>
 * A block's material and form come from the override, then the exact profile, then its exact material assignment,
 * then the first matching group (tag) assignment. Exact entries therefore always beat broad tags and families.
 */
public final class BlockProfileResolver<S, K> {

    private record StateOverride<S>(Predicate<S> when, BlockProfile profile) {}
    private record Assignment(Material material, Form form) {}
    private record GroupAssignment<S>(Predicate<S> members, Assignment assignment) {}
    private record MaterialForm(Material material, Form form) {}

    private final Function<S, K> keyOf;
    private final BiFunction<S, Float, BlockProfile> fallback;
    private final Map<K, List<StateOverride<S>>> overrides = new HashMap<>();
    private final Map<K, BlockProfile> blocks = new HashMap<>();
    private final Map<K, Assignment> exactAssignments = new HashMap<>();
    private final List<GroupAssignment<S>> groupAssignments = new ArrayList<>();
    private final Map<MaterialForm, BlockProfile> materialForms = new HashMap<>();
    private final Map<Material, BlockProfile> materials = new HashMap<>();
    private final Map<K, Set<K>> transformations = new HashMap<>();

    /**
     * @param keyOf    the block key of a state
     * @param fallback compatibility profile for a state and its hardness; must populate every field except
     *                 material and form
     */
    public BlockProfileResolver(Function<S, K> keyOf, BiFunction<S, Float, BlockProfile> fallback) {
        this.keyOf = keyOf;
        this.fallback = fallback;
    }

    public void stateOverride(K block, Predicate<S> when, BlockProfile profile) {
        overrides.computeIfAbsent(block, k -> new ArrayList<>()).add(new StateOverride<>(when, profile));
    }

    public void block(K block, BlockProfile profile) { blocks.put(block, profile); }

    public void removeBlock(K block) { blocks.remove(block); }

    public void assign(K block, Material material, Form form) { exactAssignments.put(block, new Assignment(material, form)); }

    /** Group (e.g. tag) membership; the first registered matching group wins. */
    public void assign(Predicate<S> members, Material material, Form form) {
        groupAssignments.add(new GroupAssignment<>(members, new Assignment(material, form)));
    }

    public void materialForm(Material material, Form form, BlockProfile profile) {
        materialForms.put(new MaterialForm(material, form), profile);
    }

    public void material(Material material, BlockProfile profile) { materials.put(material, profile); }

    /** An authored physical transformation: a damaged {@code from} keeps its Integrity percentage as {@code to}. */
    public void transformation(K from, K to) { transformations.computeIfAbsent(from, k -> new HashSet<>()).add(to); }

    public void removeTransformation(K from, K to) {
        Set<K> targets = transformations.get(from);
        if (targets != null && targets.remove(to) && targets.isEmpty()) transformations.remove(from);
    }

    public boolean transformsTo(K from, K to) { return transformations.getOrDefault(from, Set.of()).contains(to); }

    public BlockProfile.Resolved resolve(S state, float hardness) {
        K key = keyOf.apply(state);
        BlockProfile override = BlockProfile.EMPTY;
        for (StateOverride<S> o : overrides.getOrDefault(key, List.of())) {
            if (o.when().test(state)) { override = o.profile(); break; }
        }
        BlockProfile exact = blocks.getOrDefault(key, BlockProfile.EMPTY);

        Assignment assigned = exactAssignments.get(key);
        if (assigned == null) {
            for (GroupAssignment<S> g : groupAssignments) {
                if (g.members().test(state)) { assigned = g.assignment(); break; }
            }
        }
        Material material = first(override.material(), exact.material(), assigned == null ? null : assigned.material());
        Form form = first(override.form(), exact.form(), assigned == null ? null : assigned.form());

        BlockProfile materialForm = material != null && form != null
                ? materialForms.getOrDefault(new MaterialForm(material, form), BlockProfile.EMPTY) : BlockProfile.EMPTY;
        BlockProfile materialDefault = material != null ? materials.getOrDefault(material, BlockProfile.EMPTY) : BlockProfile.EMPTY;
        BlockProfile base = fallback.apply(state, hardness);

        BlockProfile[] layers = {override, exact, materialForm, materialDefault, base};
        return new BlockProfile.Resolved(material, form,
                first(override.classification(), exact.classification(), materialForm.classification(), materialDefault.classification(), base.classification()),
                first(override.maxDurability(), exact.maxDurability(), materialForm.maxDurability(), materialDefault.maxDurability(), base.maxDurability()),
                first(override.tools(), exact.tools(), materialForm.tools(), materialDefault.tools(), base.tools()),
                first(override.requiredTier(), exact.requiredTier(), materialForm.requiredTier(), materialDefault.requiredTier(), base.requiredTier()),
                first(override.ownership(), exact.ownership(), materialForm.ownership(), materialDefault.ownership(), base.ownership()),
                layerOf(layers, BlockProfile::maxDurability), layerOf(layers, BlockProfile::classification), layerOf(layers, BlockProfile::tools));
    }

    /** First layer (highest precedence) that authors the field; FALLBACK when only the compatibility fallback does. */
    private static Layer layerOf(BlockProfile[] layers, Function<BlockProfile, ?> field) {
        for (int i = 0; i < layers.length; i++) if (field.apply(layers[i]) != null) return Layer.values()[i];
        return Layer.FALLBACK;
    }

    @SafeVarargs
    private static <T> @Nullable T first(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
    }
}
