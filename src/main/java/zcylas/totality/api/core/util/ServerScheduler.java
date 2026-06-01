// api/core/ServerScheduler.java
package zcylas.totality.api.core.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * Tick-based task scheduler for server-side timed effects.
 * Adapted from Origins' Scheduler (public domain).
 *
 * Usage:
 *   // Run once after 60 ticks (3 seconds)
 *   Totality.SCHEDULER.queue(server -> applyEffect(player), 60);
 *
 *   // Repeat every 20 ticks indefinitely
 *   Totality.SCHEDULER.repeating(server -> tick(player), 0, 20);
 *
 *   // Repeat 5 times every 10 ticks
 *   Totality.SCHEDULER.repeatN(server -> pulse(player), 5, 0, 10);
 *
 *   // Repeat while condition is true
 *   Totality.SCHEDULER.repeatWhile(
 *       server -> tick(player),
 *       currentTick -> player.isAlive(),
 *       0, 1
 *   );
 */
public final class ServerScheduler {

    private final Int2ObjectMap<List<Consumer<MinecraftServer>>> taskQueue =
            new Int2ObjectOpenHashMap<>();
    private int currentTick = 0;

    private ServerScheduler() {}

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static final ServerScheduler INSTANCE = new ServerScheduler();

    public static ServerScheduler getInstance() {
        return INSTANCE;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Must be called once in {@code Totality.onInitialize()}.
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            INSTANCE.currentTick = server.getTickCount();
            List<Consumer<MinecraftServer>> tasks =
                    INSTANCE.taskQueue.remove(INSTANCE.currentTick);
            if (tasks == null) return;
            for (int i = 0; i < tasks.size(); i++) {
                Consumer<MinecraftServer> task = tasks.get(i);
                task.accept(server);
                // Reschedule repeating tasks
                if (task instanceof Repeating r && r.shouldContinue(INSTANCE.currentTick)) {
                    INSTANCE.queue(task, r.interval);
                }
            }
        });
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Queue a one-time task to run after {@code delayTicks} ticks.
     * Use 0 to run at the end of the current tick.
     */
    public void queue(Consumer<MinecraftServer> task, int delayTicks) {
        taskQueue.computeIfAbsent(currentTick + delayTicks + 1, t -> new ArrayList<>())
                .add(task);
    }

    /**
     * Repeat a task every {@code intervalTicks} ticks indefinitely.
     *
     * @param task          the action to perform
     * @param delayTicks    how many ticks before the first execution
     * @param intervalTicks ticks between each execution
     */
    public void repeating(Consumer<MinecraftServer> task, int delayTicks, int intervalTicks) {
        repeatWhile(task, null, delayTicks, intervalTicks);
    }

    /**
     * Repeat a task N additional times after the first execution.
     *
     * @param task          the action to perform
     * @param times         number of additional repetitions (total = times + 1)
     * @param delayTicks    how many ticks before the first execution
     * @param intervalTicks ticks between each execution
     */
    public void repeatN(Consumer<MinecraftServer> task, int times, int delayTicks, int intervalTicks) {
        repeatWhile(task, new IntPredicate() {
            private int remaining = times;
            @Override public boolean test(int tick) { return remaining-- > 0; }
        }, delayTicks, intervalTicks);
    }

    /**
     * Repeat a task while {@code condition} returns true.
     *
     * @param task          the action to perform
     * @param condition     predicate receiving the current tick — return false to stop
     * @param delayTicks    how many ticks before the first execution
     * @param intervalTicks ticks between each execution
     */
    public void repeatWhile(Consumer<MinecraftServer> task,
                            IntPredicate condition,
                            int delayTicks,
                            int intervalTicks) {
        queue(new Repeating(task, condition, intervalTicks), delayTicks);
    }

    // ── Repeating wrapper ─────────────────────────────────────────────────────

    private record Repeating(
            Consumer<MinecraftServer> task,
            IntPredicate condition,
            int interval
    ) implements Consumer<MinecraftServer> {

        boolean shouldContinue(int currentTick) {
            return condition == null || condition.test(currentTick);
        }

        @Override
        public void accept(MinecraftServer server) {
            task.accept(server);
        }
    }
}