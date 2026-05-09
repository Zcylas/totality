package zcylas.totality.api.rpg.stamina.base;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StaminaEvents {
    private static final List<Consumer<MaxStaminaCalcEvent>> MAX_STAMINA_LISTENERS
            = new ArrayList<>();
    private static final List<Consumer<StaminaRegenCalcEvent>> STAMINA_REGEN_LISTENERS
            = new ArrayList<>();

    public static void onMaxStamina(Consumer<MaxStaminaCalcEvent> listener) {
        MAX_STAMINA_LISTENERS.add(listener);
    }

    public static void onStaminaRegen(Consumer<StaminaRegenCalcEvent> listener) {
        STAMINA_REGEN_LISTENERS.add(listener);
    }

    public static void postMaxStamina(MaxStaminaCalcEvent event) {
        for (Consumer<MaxStaminaCalcEvent> l : MAX_STAMINA_LISTENERS) l.accept(event);
    }

    public static void postStaminaRegen(StaminaRegenCalcEvent event) {
        for (Consumer<StaminaRegenCalcEvent> l : STAMINA_REGEN_LISTENERS) l.accept(event);
    }
}