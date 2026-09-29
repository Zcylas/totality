package zcylas.totality.client.renderer.entity.skateboard;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;

/**
 * The rider's stance on a skateboard, applied after vanilla's standing pose: side-on to the board (the entity turns the
 * body {@code SkateboardEntity.STANCE_YAW} from the heading), so spreading the legs sideways puts the feet along the
 * deck, towards the trucks; the arms are held a little out for balance. Tricks will pose the rider separately from the
 * board.
 */
public final class SkateboardRiderPose {

    /** Set on a player's render state while they ride a skateboard. */
    public static final RenderStateDataKey<Boolean> RIDING = RenderStateDataKey.create(() -> "totality:skateboard_rider");
    /** The head's turn from the body (degrees): over the leading (left) shoulder, a little the other way. */
    public static final float HEAD_TURN_MIN = -100.0F;
    public static final float HEAD_TURN_MAX = 45.0F;
    /** Leg spread (feet about 4.3 px either side of the centre, raised 0.2 px) and arm lift, in radians. */
    private static final float LEG_SPREAD = 0.2F;
    private static final float ARM_LIFT = 0.22F;

    private SkateboardRiderPose() {}

    public static void apply(HumanoidModel<?> model) {
        model.rightLeg.xRot = 0.0F;
        model.leftLeg.xRot = 0.0F;
        model.rightLeg.zRot = LEG_SPREAD;
        model.leftLeg.zRot = -LEG_SPREAD;
        model.rightArm.zRot += ARM_LIFT;
        model.leftArm.zRot -= ARM_LIFT;
    }
}
