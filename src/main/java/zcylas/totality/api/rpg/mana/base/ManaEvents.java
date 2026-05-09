package zcylas.totality.api.rpg.mana.base;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ManaEvents {
    private static final List<Consumer<MaxManaCalcEvent>> MAX_MANA_LISTENERS
            = new ArrayList<>();
    private static final List<Consumer<ManaRegenCalcEvent>> MANA_REGEN_LISTENERS
            = new ArrayList<>();

    public static void onMaxMana(Consumer<MaxManaCalcEvent> listener) {
        MAX_MANA_LISTENERS.add(listener);
    }

    public static void onManaRegen(Consumer<ManaRegenCalcEvent> listener) {
        MANA_REGEN_LISTENERS.add(listener);
    }

    public static void postMaxMana(MaxManaCalcEvent event) {
        for (Consumer<MaxManaCalcEvent> l : MAX_MANA_LISTENERS) l.accept(event);
    }

    public static void postManaRegen(ManaRegenCalcEvent event) {
        for (Consumer<ManaRegenCalcEvent> l : MANA_REGEN_LISTENERS) l.accept(event);
    }
}
