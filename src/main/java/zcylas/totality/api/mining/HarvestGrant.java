package zcylas.totality.api.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Harvest eligibility for ONE exact Totality terminal break.
 *
 * <p>Vanilla {@code ServerPlayerGameMode.destroyBlock(BlockPos)} runs the harvest/drop path only when
 * {@code ServerPlayer.hasCorrectToolForDrops(adjustedState)} is true, and an empty hand is never the
 * "correct tool" for a tool-requiring block. That method takes only a {@code BlockState}, so it cannot prove
 * WHICH block is being broken. This grant is therefore NOT consulted there. It is consulted by
 * {@code ServerPlayerGameModeMixin} at the one place the position is known: it wraps the
 * {@code hasCorrectToolForDrops} call <i>inside</i> {@code destroyBlock} and passes {@code destroyBlock}'s own
 * {@code pos}. {@code Player.hasCorrectToolForDrops} itself is never altered, so no other caller can be affected.
 *
 * <p>A query is granted only if ALL hold: a grant is open on this thread (the synchronous {@code destroyBlock}
 * issued by {@code BlockBreaking}); the player is the granted player; the break position equals the granted
 * position; the state is the granted block type; and this grant has not already answered (single use).
 * Grants live in a per-thread stack: the client thread never sees the server's, nested breaks each keep their
 * own grant, {@link #close} removes exactly its own entry, and try-with-resources cleans up after exceptions.
 */
public final class HarvestGrant implements AutoCloseable {

    private static final ThreadLocal<Deque<HarvestGrant>> OPEN = ThreadLocal.withInitial(ArrayDeque::new);
    private static int served;   // test observable: number of eligibility answers granted

    private final ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos pos;
    private final Block block;
    private boolean used;

    private HarvestGrant(ServerPlayer player, ServerLevel level, BlockPos pos, Block block) {
        this.player = player;
        this.level = level;
        this.pos = pos.immutable();
        this.block = block;
    }

    public static HarvestGrant open(ServerPlayer player, ServerLevel level, BlockPos pos, Block block) {
        HarvestGrant g = new HarvestGrant(player, level, pos, block);
        OPEN.get().push(g);
        return g;
    }

    @Override
    public void close() {
        Deque<HarvestGrant> stack = OPEN.get();
        stack.remove(this);            // removes THIS grant wherever it is (a nested grant never pops another's)
        if (stack.isEmpty()) OPEN.remove();
    }

    /** Called by the mixin with {@code destroyBlock}'s own break position. */
    public static boolean grants(ServerPlayer who, BlockPos breakPos, BlockState state) {
        Deque<HarvestGrant> stack = OPEN.get();
        if (stack.isEmpty()) return false;
        for (HarvestGrant g : stack) {
            if (!g.used && g.player == who && g.level == who.level() && g.pos.equals(breakPos) && state.is(g.block)) {
                g.used = true;
                served++;
                return true;
            }
        }
        return false;
    }

    /** Test hooks. */
    static boolean isOpen() { return !OPEN.get().isEmpty(); }
    static int openCount() { return OPEN.get().size(); }
    static int served() { return served; }
}
