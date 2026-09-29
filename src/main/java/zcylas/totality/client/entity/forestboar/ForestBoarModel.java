package zcylas.totality.client.entity.forestboar;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Forest Boar model: Astra's geometry ({@link ForestBoarGeometry}) with the hybrid's vanilla keyframe animations
 * ({@link ForestBoarAnimation}), both generated from Forest_Boar_Hybrid.bbmodel. The layers are added in this order:
 * <ol>
 *   <li><b>Locomotion:</b> walk and run, sampled at the shared gait phase (distance travelled / stride), weighted by
 *       how fast the boar moves and by the walk/run crossfade.</li>
 *   <li><b>Peaceful:</b> idle (only when standing and peaceful), graze and sniff.</li>
 *   <li><b>Reaction:</b> alert, charge wind-up, the charge posture over the run, and the hurt flinch.</li>
 *   <li><b>Head tracking</b> of the look direction, faded out while grazing, sniffing or charging.</li>
 * </ol>
 */
public class ForestBoarModel extends EntityModel<ForestBoarRenderState> {

    private static final float MIN_WEIGHT = 0.002F;

    private final ModelPart head;
    private final KeyframeAnimation walk;
    private final KeyframeAnimation run;
    private final KeyframeAnimation idle;
    private final KeyframeAnimation graze;
    private final KeyframeAnimation sniff;
    private final KeyframeAnimation alert;
    private final KeyframeAnimation windup;
    private final KeyframeAnimation charge;
    private final KeyframeAnimation hurt;

    public ForestBoarModel(ModelPart root) {
        super(root);
        this.head = root.getChild("root").getChild("body").getChild("head");
        this.walk = ForestBoarAnimation.WALK.bake(root);
        this.run = ForestBoarAnimation.RUN.bake(root);
        this.idle = ForestBoarAnimation.IDLE.bake(root);
        this.graze = ForestBoarAnimation.GRAZE.bake(root);
        this.sniff = ForestBoarAnimation.SNIFF.bake(root);
        this.alert = ForestBoarAnimation.ALERT.bake(root);
        this.windup = ForestBoarAnimation.CHARGE_WINDUP.bake(root);
        this.charge = ForestBoarAnimation.CHARGE.bake(root);
        this.hurt = ForestBoarAnimation.HURT.bake(root);
    }

    public static ModelPart createRoot() {
        return ForestBoarGeometry.createRoot();
    }

    private static void layer(KeyframeAnimation animation, long millis, float weight) {
        if (weight > MIN_WEIGHT) animation.apply(millis, Math.min(weight, 1.0F));
    }

    @Override
    public void setupAnim(ForestBoarRenderState state) {
        super.setupAnim(state);
        long gait = (long) (state.gaitPhase * 1000.0F);
        long age = (long) (state.ageInTicks * 50.0F);
        layer(this.walk, gait, state.walkWeight);
        layer(this.run, gait, state.runWeight);
        layer(this.idle, age, state.idleWeight);
        layer(this.graze, age, state.grazeWeight);
        layer(this.sniff, state.sniffMillis, state.sniffWeight);
        layer(this.alert, state.alertMillis, state.alertWeight);
        layer(this.windup, state.windupMillis, state.windupWeight);
        layer(this.charge, age, state.chargeWeight);
        if (state.hurtMillis >= 0) layer(this.hurt, state.hurtMillis, 1.0F);
        this.head.yRot += Mth.clamp(state.yRot, -35.0F, 35.0F) * Mth.DEG_TO_RAD * state.lookWeight;
        this.head.xRot += Mth.clamp(state.xRot, -20.0F, 25.0F) * Mth.DEG_TO_RAD * state.lookWeight;
    }
}
