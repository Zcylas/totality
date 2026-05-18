package zcylas.totality.networking.ability;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ClientAbilityManager {

    private static Set<Identifier> unlocked = new HashSet<>();
    private static Map<Identifier, Integer> cooldowns = new HashMap<>();
    private static @Nullable Identifier equippedAbility = null;

    private ClientAbilityManager() {}

    public static void sync(Set<Identifier> newUnlocked, Map<Identifier, Integer> newCooldowns,
                            @Nullable Identifier newEquipped) {
        unlocked = new HashSet<>(newUnlocked);
        cooldowns = new HashMap<>(newCooldowns);
        equippedAbility = newEquipped;
    }

    public static boolean hasAbility(Identifier id) {
        return unlocked.contains(id);
    }

    public static boolean isOnCooldown(Identifier id) {
        return cooldowns.getOrDefault(id, 0) > 0;
    }

    public static int getCooldown(Identifier id) {
        return cooldowns.getOrDefault(id, 0);
    }

    public static Set<Identifier> getUnlocked() {
        return Collections.unmodifiableSet(unlocked);
    }
    public static @Nullable Identifier getEquippedAbility() {
        return equippedAbility;
    }
}