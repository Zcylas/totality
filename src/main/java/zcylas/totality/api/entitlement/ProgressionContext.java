package zcylas.totality.api.entitlement;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TraceableEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The progression-context contract (canonical §4.6, §10.10): gameplay performed only through debug access must
 * not award ordinary progression.
 *
 * <p><b>Producers</b> (code that executes an entitled action) run the action inside
 * {@link #run(ServerPlayer, boolean, Runnable)} with {@code nonProgression = true} whenever the action was
 * authorized only by debug paths ({@link EntitlementSnapshot#debugOnly()}). Entities the player spawns and owns
 * during the scope (projectiles, bolts, clouds — anything {@link TraceableEntity}) inherit it. Because Totality
 * attributes spell damage to the caster rather than to the projectile, code where a spawned entity later acts on
 * its owner's behalf (a bolt or fireball hitting) re-enters the scope with {@link #runForEntity}; vanilla-style
 * projectile damage that keeps the projectile as its direct source is also caught by {@link #suppresses}.
 *
 * <p><b>Consumers</b> (code that awards progression — skill XP, character XP, quest objectives, and in future
 * Codex discovery, achievements, research, first-use records) must check {@link #inNonProgressionScope} for
 * synchronous awards and {@link #suppresses(ServerPlayer, DamageSource)} for awards attributed through damage,
 * and award nothing when either is true.
 *
 * <p>Limits: markers on spawned entities are runtime-only (not saved), and vanilla systems that Totality does
 * not own (vanilla advancements, vanilla experience orbs from kills) are not covered by this contract.
 * Server thread only.
 */
public final class ProgressionContext {

    private static final Deque<UUID> SCOPES = new ArrayDeque<>();
    private static final Set<Entity> MARKED = Collections.newSetFromMap(new WeakHashMap<>());

    private ProgressionContext() {}

    /** Runs {@code action}; inside a non-progression scope for {@code player} when {@code nonProgression}. */
    public static void run(ServerPlayer player, boolean nonProgression, Runnable action) {
        if (nonProgression) {
            runNonProgression(player.getUUID(), action);
        } else {
            action.run();
        }
    }

    public static void runNonProgression(UUID playerId, Runnable action) {
        SCOPES.push(playerId);
        try {
            action.run();
        } finally {
            SCOPES.pop();
        }
    }

    public static boolean inNonProgressionScope(UUID playerId) {
        return SCOPES.contains(playerId);
    }

    public static boolean inNonProgressionScope(ServerPlayer player) {
        return inNonProgressionScope(player.getUUID());
    }

    /** Entity-load hook: an entity owned by a player who is inside a non-progression scope inherits it. */
    public static void onEntityLoad(Entity entity) {
        if (SCOPES.isEmpty() || !(entity instanceof TraceableEntity traceable)) return;
        Entity owner = traceable.getOwner();
        if (owner != null && SCOPES.contains(owner.getUUID())) MARKED.add(entity);
    }

    /** Runs an effect a spawned entity performs later on its owner's behalf (a bolt or fireball hitting),
     *  re-entering the owner's non-progression scope when the entity inherited it. Spell damage is attributed to
     *  the caster, not to the projectile, so the projectile's own hit code must establish the scope. */
    public static void runForEntity(Entity actor, Runnable action) {
        Entity owner = actor instanceof TraceableEntity traceable ? traceable.getOwner() : null;
        if (owner != null && MARKED.contains(actor)) {
            runNonProgression(owner.getUUID(), action);
        } else {
            action.run();
        }
    }

    public static boolean isNonProgression(Entity entity) {
        return MARKED.contains(entity);
    }

    /** Whether progression {@code player} would earn from this damage must be suppressed. */
    public static boolean suppresses(ServerPlayer player, @Nullable DamageSource source) {
        if (inNonProgressionScope(player)) return true;
        if (source == null) return false;
        Entity direct = source.getDirectEntity();
        return direct != null && direct != player && MARKED.contains(direct);
    }
}
