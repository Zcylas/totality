package zcylas.totality.api.magic.grimoire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import zcylas.totality.api.magic.grimoire.formula.ArcaneFormula;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record GrimoireCaster(
        Map<Integer, ArcaneFormula> formulas,
        Map<Integer, String> names,
        int currentSlot
) {
    public static final GrimoireCaster EMPTY = new GrimoireCaster(Map.of(), Map.of(), 0);

    // Sparse map codec — only stores non-empty slots
    private static final Codec<Map<Integer, ArcaneFormula>> FORMULA_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, ArcaneFormula.CODEC)
                    .xmap(
                            m -> { Map<Integer, ArcaneFormula> r = new HashMap<>();
                                m.forEach((k, v) -> r.put(Integer.parseInt(k), v));
                                return r; },
                            m -> { Map<String, ArcaneFormula> r = new HashMap<>();
                                m.forEach((k, v) -> r.put(k.toString(), v));
                                return r; }
                    );

    private static final Codec<Map<Integer, String>> NAME_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .xmap(
                            m -> { Map<Integer, String> r = new HashMap<>();
                                m.forEach((k, v) -> r.put(Integer.parseInt(k), v));
                                return r; },
                            m -> { Map<String, String> r = new HashMap<>();
                                m.forEach((k, v) -> r.put(k.toString(), v));
                                return r; }
                    );

    public static final Codec<GrimoireCaster> CODEC = RecordCodecBuilder.create(i -> i.group(
            FORMULA_MAP_CODEC.optionalFieldOf("formulas", Map.of()).forGetter(GrimoireCaster::formulas),
            NAME_MAP_CODEC.optionalFieldOf("names", Map.of()).forGetter(GrimoireCaster::names),
            Codec.INT.optionalFieldOf("slot", 0).forGetter(GrimoireCaster::currentSlot)
    ).apply(i, GrimoireCaster::new));

    public static final StreamCodec<FriendlyByteBuf, GrimoireCaster> STREAM_CODEC = StreamCodec.of(
            (buf, c) -> {
                // Formulas
                buf.writeInt(c.formulas().size());
                c.formulas().forEach((slot, f) -> {
                    buf.writeInt(slot);
                    ArcaneFormula.STREAM_CODEC.encode(buf, f);
                });
                // Names
                buf.writeInt(c.names().size());
                c.names().forEach((slot, name) -> {
                    buf.writeInt(slot);
                    buf.writeUtf(name);
                });
                buf.writeInt(c.currentSlot());
            },
            buf -> {
                int fSize = buf.readInt();
                Map<Integer, ArcaneFormula> formulas = new HashMap<>();
                for (int i = 0; i < fSize; i++)
                    formulas.put(buf.readInt(), ArcaneFormula.STREAM_CODEC.decode(buf));
                int nSize = buf.readInt();
                Map<Integer, String> names = new HashMap<>();
                for (int i = 0; i < nSize; i++)
                    names.put(buf.readInt(), buf.readUtf());
                return new GrimoireCaster(formulas, names, buf.readInt());
            }
    );

    // Get a specific slot's formula
    public ArcaneFormula getFormula(int slot) {
        return formulas.getOrDefault(slot, ArcaneFormula.EMPTY);
    }

    // Get a specific slot's name
    public String getSpellName(int slot) {
        return names.getOrDefault(slot, "");
    }

    // Current slot's formula — used for casting
    public ArcaneFormula formula() {
        return getFormula(currentSlot);
    }

    // Current slot's name — used for HUD
    public String spellName() {
        return getSpellName(currentSlot);
    }

    // Return new caster with one slot updated
    public GrimoireCaster withSlot(int slot, ArcaneFormula formula, String name) {
        Map<Integer, ArcaneFormula> newFormulas = new HashMap<>(formulas);
        Map<Integer, String> newNames            = new HashMap<>(names);
        newFormulas.put(slot, formula);
        newNames.put(slot, name);
        return new GrimoireCaster(newFormulas, newNames, currentSlot);
    }

    // Return new caster with current slot changed
    public GrimoireCaster withCurrentSlot(int slot) {
        return new GrimoireCaster(formulas, names, slot);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GrimoireCaster that)) return false;
        return currentSlot == that.currentSlot
                && Objects.equals(formulas, that.formulas)
                && Objects.equals(names, that.names);
    }

    @Override
    public int hashCode() {
        return Objects.hash(formulas, names, currentSlot);
    }
}