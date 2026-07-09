package zcylas.totality.api.rpg.rest;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.entity.rest.RestSeatEntity;

/**
 * A player's in-progress rest — tracked server-side only by {@link RestSessionManager}.
 */
public final class RestSession {

    private final RestType type;
    private final int totalTicks;
    @Nullable
    private final BlockPos bedPos;
    @Nullable
    private final ShortRestActivity activity;
    /** True only when a Long Rest genuinely engaged vanilla's own bed-sleep (night, valid bed). */
    private final boolean realVanillaSleep;

    /** The invisible mount every non-vanilla-sleep session rides — see RestSessionManager's class doc. */
    @Nullable private RestSeatEntity seatEntity;

    private int elapsedTicks;
    @Nullable
    private Integer graceDeadlineTick;

    public RestSession(RestType type, int totalTicks, @Nullable BlockPos bedPos,
                        @Nullable ShortRestActivity activity, boolean realVanillaSleep) {
        this.type = type;
        this.totalTicks = totalTicks;
        this.bedPos = bedPos;
        this.activity = activity;
        this.realVanillaSleep = realVanillaSleep;
    }

    public RestType getType() { return type; }
    public int getTotalTicks() { return totalTicks; }
    public int getElapsedTicks() { return elapsedTicks; }
    public int getRemainingTicks() { return Math.max(0, totalTicks - elapsedTicks); }
    @Nullable public BlockPos getBedPos() { return bedPos; }
    @Nullable public ShortRestActivity getActivity() { return activity; }
    public boolean isRealVanillaSleep() { return realVanillaSleep; }

    @Nullable public RestSeatEntity getSeatEntity() { return seatEntity; }
    public void setSeatEntity(@Nullable RestSeatEntity seatEntity) { this.seatEntity = seatEntity; }

    public void tick() { elapsedTicks++; }
    public boolean isComplete() { return elapsedTicks >= totalTicks; }

    public boolean isInGrace() { return graceDeadlineTick != null; }
    @Nullable public Integer getGraceDeadlineTick() { return graceDeadlineTick; }
    public void enterGrace(int deadlineTick) { this.graceDeadlineTick = deadlineTick; }
    public void clearGrace() { this.graceDeadlineTick = null; }
}
