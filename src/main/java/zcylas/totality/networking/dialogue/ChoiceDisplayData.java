package zcylas.totality.networking.dialogue;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public record ChoiceDisplayData(
        Component text,
        boolean locked,
        String lockReason
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ChoiceDisplayData> STREAM_CODEC =
            StreamCodec.of(
                    (buf, d) -> {
                        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, d.text());
                        buf.writeBoolean(d.locked());
                        buf.writeUtf(d.lockReason());
                    },
                    buf -> {
                        Component text = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf);
                        boolean locked = buf.readBoolean();
                        String lockReason = buf.readUtf();
                        return new ChoiceDisplayData(text, locked, lockReason);
                    }
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, List<ChoiceDisplayData>> LIST_STREAM_CODEC =
            StreamCodec.of(
                    (buf, list) -> {
                        buf.writeInt(list.size());
                        for (ChoiceDisplayData d : list) STREAM_CODEC.encode(buf, d);
                    },
                    buf -> {
                        int size = buf.readInt();
                        List<ChoiceDisplayData> list = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) list.add(STREAM_CODEC.decode(buf));
                        return list;
                    }
            );
}
