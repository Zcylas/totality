package zcylas.totality.api.rpg.combat;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import zcylas.totality.api.rpg.stats.AbilityScore;
import org.jspecify.annotations.Nullable;

import java.util.*;

public final class DamageBonusRegistry {

    @FunctionalInterface
    public interface BonusProvider {
        /** Return a bonus to add, or null if not applicable. */
        @Nullable DamageBonus provide(ServerPlayer player,
                                      @Nullable AbilityScore usedAbility,
                                      boolean isMagical);
    }

    private static final Map<UUID, Map<Identifier, BonusProvider>> PROVIDERS = new HashMap<>();

    public static void register(ServerPlayer player, Identifier id, BonusProvider provider) {
        PROVIDERS.computeIfAbsent(player.getUUID(), k -> new LinkedHashMap<>()).put(id, provider);
    }

    public static void remove(ServerPlayer player, Identifier id) {
        Map<Identifier, BonusProvider> map = PROVIDERS.get(player.getUUID());
        if (map != null) map.remove(id);
    }

    /** Returns all active bonuses for this attack. Empty if attacker is not a player. */
    public static List<DamageBonus> resolve(LivingEntity attacker,
                                            @Nullable AbilityScore usedAbility,
                                            boolean isMagical) {
        if (!(attacker instanceof ServerPlayer sp)) return List.of();
        Map<Identifier, BonusProvider> map = PROVIDERS.get(sp.getUUID());
        if (map == null || map.isEmpty()) return List.of();

        List<DamageBonus> result = new ArrayList<>();
        for (BonusProvider provider : map.values()) {
            DamageBonus bonus = provider.provide(sp, usedAbility, isMagical);
            if (bonus != null && bonus.amount() != 0) result.add(bonus);
        }
        return result;
    }

    public static void clearPlayer(UUID playerId) { PROVIDERS.remove(playerId); }

    private DamageBonusRegistry() {}
}