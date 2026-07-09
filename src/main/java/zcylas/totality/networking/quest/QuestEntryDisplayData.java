package zcylas.totality.networking.quest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import zcylas.totality.api.quest.QuestType;

import java.util.ArrayList;
import java.util.List;

public record QuestEntryDisplayData(
        String id,
        String name,
        String description,
        QuestType type,
        List<String> objectives,
        boolean[] objectivesDone,
        boolean tracked,
        boolean ready,
        boolean completed,
        long rewardCredits,
        int rewardXp
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestEntryDisplayData> STREAM_CODEC =
            StreamCodec.of(
                    (buf, d) -> {
                        buf.writeUtf(d.id());
                        buf.writeUtf(d.name());
                        buf.writeUtf(d.description());
                        buf.writeEnum(d.type());
                        buf.writeInt(d.objectives().size());
                        for (String o : d.objectives()) buf.writeUtf(o);
                        for (boolean b : d.objectivesDone()) buf.writeBoolean(b);
                        buf.writeBoolean(d.tracked());
                        buf.writeBoolean(d.ready());
                        buf.writeBoolean(d.completed());
                        buf.writeVarLong(d.rewardCredits());
                        buf.writeInt(d.rewardXp());
                    },
                    buf -> {
                        String id = buf.readUtf();
                        String name = buf.readUtf();
                        String description = buf.readUtf();
                        QuestType type = buf.readEnum(QuestType.class);
                        int n = buf.readInt();
                        List<String> objectives = new ArrayList<>(n);
                        for (int i = 0; i < n; i++) objectives.add(buf.readUtf());
                        boolean[] done = new boolean[n];
                        for (int i = 0; i < n; i++) done[i] = buf.readBoolean();
                        boolean tracked = buf.readBoolean();
                        boolean ready = buf.readBoolean();
                        boolean completed = buf.readBoolean();
                        long rewardCredits = buf.readVarLong();
                        int rewardXp = buf.readInt();
                        return new QuestEntryDisplayData(id, name, description, type,
                                objectives, done, tracked, ready, completed, rewardCredits, rewardXp);
                    }
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, List<QuestEntryDisplayData>> LIST_STREAM_CODEC =
            StreamCodec.of(
                    (buf, list) -> {
                        buf.writeInt(list.size());
                        for (QuestEntryDisplayData d : list) STREAM_CODEC.encode(buf, d);
                    },
                    buf -> {
                        int size = buf.readInt();
                        List<QuestEntryDisplayData> list = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) list.add(STREAM_CODEC.decode(buf));
                        return list;
                    }
            );
}
