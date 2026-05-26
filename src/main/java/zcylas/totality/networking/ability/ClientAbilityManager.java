package zcylas.totality.networking.ability;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ClientAbilityManager {

    private static Set<Identifier>         unlocked  = new HashSet<>();
    private static Map<Identifier, Integer> cooldowns = new HashMap<>();
    private static @Nullable Identifier    equippedAbility = null;
    private static List<Identifier>        favorites = new ArrayList<>();

    private ClientAbilityManager() {}

    public static void sync(Set<Identifier> newUnlocked,
                            Map<Identifier, Integer> newCooldowns,
                            @Nullable Identifier newEquipped,
                            List<Identifier> newFavorites) {
        unlocked        = new HashSet<>(newUnlocked);
        cooldowns       = new HashMap<>(newCooldowns);
        equippedAbility = newEquipped;
        favorites       = new ArrayList<>(newFavorites);
    }

    public static boolean hasAbility(Identifier id)    { return unlocked.contains(id); }
    public static boolean isOnCooldown(Identifier id)  { return cooldowns.getOrDefault(id, 0) > 0; }
    public static int getCooldown(Identifier id)        { return cooldowns.getOrDefault(id, 0); }
    public static Set<Identifier> getUnlocked()         { return Collections.unmodifiableSet(unlocked); }
    public static @Nullable Identifier getEquippedAbility() { return equippedAbility; }
    public static List<Identifier> getFavorites()        { return Collections.unmodifiableList(favorites); }
    public static boolean isFavorite(Identifier id)     { return favorites.contains(id); }
}