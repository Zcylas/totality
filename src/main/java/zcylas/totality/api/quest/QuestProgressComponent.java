package zcylas.totality.api.quest;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.TotalityComponent;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player quest progress. Server-only persistent state, no client sync — the client
 * gets a resolved display snapshot on demand via {@code ShowQuestStatePayload} instead
 * (mirrors how {@code TradeSessionManager} pushes shop state rather than syncing a component).
 */
public class QuestProgressComponent implements TotalityComponent, CopyableComponent<QuestProgressComponent> {

    public enum Status { ACTIVE, READY, COMPLETED }

    public static final class QuestState {
        public Status status = Status.ACTIVE;
        public boolean[] objectives = new boolean[0];
        public boolean tracked = false;
    }

    private final Map<Identifier, QuestState> quests = new HashMap<>();

    public boolean has(Identifier questId) {
        return quests.containsKey(questId);
    }

    public QuestState get(Identifier questId) {
        return quests.get(questId);
    }

    public Map<Identifier, QuestState> all() {
        return quests;
    }

    public void grant(Identifier questId, int objectiveCount) {
        if (quests.containsKey(questId)) return;
        QuestState state = new QuestState();
        state.objectives = new boolean[objectiveCount];
        quests.put(questId, state);
    }

    /** Returns true if this call caused every objective to become done (quest is now READY
     *  to turn in) — does NOT grant rewards or mark COMPLETED itself, see {@link #finish}. */
    public boolean setObjective(Identifier questId, int index, boolean done) {
        QuestState state = quests.get(questId);
        if (state == null || state.status != Status.ACTIVE) return false;
        if (index < 0 || index >= state.objectives.length) return false;
        state.objectives[index] = done;

        boolean allDone = true;
        for (boolean b : state.objectives) if (!b) { allDone = false; break; }
        if (allDone) {
            state.status = Status.READY;
            return true;
        }
        return false;
    }

    /** Turns in a READY quest, granting rewards is the caller's job. Returns false if the
     *  quest isn't in the READY state (not done yet, or already turned in). */
    public boolean finish(Identifier questId) {
        QuestState state = quests.get(questId);
        if (state == null || state.status != Status.READY) return false;
        state.status = Status.COMPLETED;
        state.tracked = false;
        return true;
    }

    public void setTracked(Identifier questId, boolean tracked) {
        QuestState state = quests.get(questId);
        if (state != null) state.tracked = tracked;
    }

    /**
     * Safety net for when a quest template's objective count changes after a player already
     * has it in progress (e.g. a new objective added during dev iteration) — resizes without
     * crashing so {@code completeObjective} on a new index doesn't silently no-op from an
     * out-of-bounds check. This does NOT remap old objective indices to new meanings; it's
     * crash prevention, not a real migration. {@code QuestManager} calling this is a stopgap —
     * during active dev, prefer clearing progress via {@code remove}/a reset command instead.
     */
    public void resizeObjectives(Identifier questId, int expectedSize) {
        QuestState state = quests.get(questId);
        if (state == null || state.objectives.length == expectedSize) return;
        boolean[] resized = new boolean[expectedSize];
        System.arraycopy(state.objectives, 0, resized, 0, Math.min(state.objectives.length, expectedSize));
        state.objectives = resized;
    }

    public void remove(Identifier questId) {
        quests.remove(questId);
    }

    public void clear() {
        quests.clear();
    }

    @Override
    public void writeData(ValueOutput output) {
        output.putInt("q_count", quests.size());
        int i = 0;
        for (var entry : quests.entrySet()) {
            output.putString("q_id" + i, entry.getKey().toString());
            output.putString("q_status" + i, entry.getValue().status.name());
            output.putBoolean("q_tracked" + i, entry.getValue().tracked);
            boolean[] obj = entry.getValue().objectives;
            output.putInt("q_objn" + i, obj.length);
            for (int j = 0; j < obj.length; j++) output.putBoolean("q_obj" + i + "_" + j, obj[j]);
            i++;
        }
    }

    @Override
    public void readData(ValueInput input) {
        quests.clear();
        int count = input.getIntOr("q_count", 0);
        for (int i = 0; i < count; i++) {
            String idStr = input.getStringOr("q_id" + i, "");
            Identifier id = Identifier.tryParse(idStr);
            if (id == null) continue;

            QuestState state = new QuestState();
            try {
                state.status = Status.valueOf(input.getStringOr("q_status" + i, "ACTIVE"));
            } catch (IllegalArgumentException e) {
                state.status = Status.ACTIVE;
            }
            state.tracked = input.getBooleanOr("q_tracked" + i, false);
            int objN = input.getIntOr("q_objn" + i, 0);
            state.objectives = new boolean[objN];
            for (int j = 0; j < objN; j++) {
                state.objectives[j] = input.getBooleanOr("q_obj" + i + "_" + j, false);
            }
            quests.put(id, state);
        }
    }

    @Override
    public void copyFrom(QuestProgressComponent other, HolderLookup.Provider registries) {
        quests.clear();
        for (var entry : other.quests.entrySet()) {
            QuestState copy = new QuestState();
            copy.status = entry.getValue().status;
            copy.tracked = entry.getValue().tracked;
            copy.objectives = entry.getValue().objectives.clone();
            quests.put(entry.getKey(), copy);
        }
    }
}
