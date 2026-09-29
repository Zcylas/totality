package zcylas.totality.client.entity.forestboar;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import zcylas.totality.entity.animal.ForestBoarEntity;

/** What the Forest Boar model needs each frame: the behaviour, the gait phase and every layer's weight and time. */
public class ForestBoarRenderState extends LivingEntityRenderState {
    public ForestBoarEntity.Behavior behavior = ForestBoarEntity.Behavior.WANDER;
    /** Gait cycles, 0..1 (both gaits are 1 s long, so this is also their time in seconds). */
    public float gaitPhase;
    public float walkWeight;
    public float runWeight;
    public float idleWeight;
    public float grazeWeight;
    public float sniffWeight;
    public float alertWeight;
    public float windupWeight;
    public float chargeWeight;
    public float lookWeight = 1.0F;
    public long sniffMillis;
    public long alertMillis;
    public long windupMillis;
    /** Time into the hurt flinch, or -1 when not flinching. */
    public long hurtMillis = -1;
}
