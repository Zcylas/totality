package zcylas.totality.client.vfx.glow;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The emissive layer's minimal global brightness management (VFX Experiment 3, Fireball V2 B4; decision D5): per-effect
 * budgets plus one final global cap, resolved once per frame before the sources emit.
 *
 * <ol>
 *   <li>Every source states its <em>demand</em> ({@link EmissiveSource#emissiveDemand()}: roughly how many "full
 *       effects" of light it is about to add, e.g. one Fireball explosion at its brightest = 1) and optionally a
 *       <em>budget group</em> ({@link EmissiveSource#budgetGroup()}).</li>
 *   <li>A group with a limit ({@link #setGroupLimit}) is scaled down when its summed demand exceeds the limit, so twenty
 *       overlapping Fireballs give the light of a few, not twenty. Groups below their limit, and sources without a
 *       group, are untouched: one or two effects keep their normal glow.</li>
 *   <li>The capped demands of all groups (plus ungrouped sources) are summed; only if that total exceeds the global
 *       limit ({@code glow.globalLimit}) is everything scaled by {@code limit / total}. With the default limit this
 *       never triggers for today's effects (Fireball's group limit plus Heat Vision stay below it), so Fireball can
 *       never dim Heat Vision; the global cap is the safety net for future effects.</li>
 * </ol>
 *
 * No priorities: the per-group limit already protects other effects from a crowded one. The scale multiplies the
 * colour of every quad the source adds ({@link EmissiveBuffer}); sources do not need to know about it.
 */
public final class EmissiveBudget {

    private final Map<String, Float> groupLimits = new HashMap<>();
    private final Map<EmissiveSource, Float> scales = new IdentityHashMap<>();
    private final Map<String, Float> lastGroupDemand = new HashMap<>();
    private final Map<String, Float> lastGroupScale = new HashMap<>();
    private float lastTotal;
    private float lastGlobalScale = 1.0f;

    /** Caps the summed demand of {@code group} at {@code limit} (a limit of 0 or less removes it). */
    public void setGroupLimit(String group, float limit) {
        if (limit > 0) {
            groupLimits.put(group, limit);
        } else {
            groupLimits.remove(group);
        }
    }

    /** Resolves this frame's scale for every source under the global limit. */
    public void resolve(List<? extends EmissiveSource> sources, float globalLimit) {
        scales.clear();
        lastGroupDemand.clear();
        float ungrouped = 0.0f;
        for (EmissiveSource s : sources) {
            float d = Math.max(0.0f, s.emissiveDemand());
            String g = s.budgetGroup();
            if (g == null) {
                ungrouped += d;
            } else {
                lastGroupDemand.merge(g, d, Float::sum);
            }
        }
        Map<String, Float> groupScale = lastGroupScale;
        groupScale.clear();
        float total = ungrouped;
        for (Map.Entry<String, Float> e : lastGroupDemand.entrySet()) {
            float d = e.getValue();
            Float limit = groupLimits.get(e.getKey());
            float capped = limit == null ? d : Math.min(d, limit);
            groupScale.put(e.getKey(), d <= 0.0f ? 1.0f : capped / d);
            total += capped;
        }
        lastTotal = total;
        lastGlobalScale = globalLimit > 0.0f && total > globalLimit ? globalLimit / total : 1.0f;
        for (EmissiveSource s : sources) {
            String g = s.budgetGroup();
            scales.put(s, (g == null ? 1.0f : groupScale.getOrDefault(g, 1.0f)) * lastGlobalScale);
        }
    }

    /** The scale of {@code source} in the last resolved frame (1 when it was not part of it). */
    public float scale(EmissiveSource source) {
        return scales.getOrDefault(source, 1.0f);
    }

    /** Summed demand of {@code group} in the last resolved frame. */
    public float groupDemand(String group) {
        return lastGroupDemand.getOrDefault(group, 0.0f);
    }

    /** The scale applied to {@code group} in the last resolved frame (its limit's share times the global scale). */
    public float groupScale(String group) {
        return lastGroupScale.getOrDefault(group, 1.0f) * lastGlobalScale;
    }

    /** Total capped demand of the last resolved frame. */
    public float lastTotal() {
        return lastTotal;
    }

    public float lastGlobalScale() {
        return lastGlobalScale;
    }
}
