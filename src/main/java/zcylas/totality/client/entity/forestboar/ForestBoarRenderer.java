package zcylas.totality.client.entity.forestboar;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.entity.animal.ForestBoarEntity;

import java.util.Map;
import java.util.WeakHashMap;

/** Client-only renderer for the Forest Boar: Astra's 128x128 texture, unchanged (textures/entity/forest_boar/forest_boar.png). */
public class ForestBoarRenderer extends MobRenderer<ForestBoarEntity, ForestBoarRenderState, ForestBoarModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "textures/entity/forest_boar/forest_boar.png");

    /** Each boar's gait and blend state (client side, forgotten with the entity). */
    private final Map<ForestBoarEntity, ForestBoarMotion> motion = new WeakHashMap<>();
    private ForestBoarRenderState lastExtracted;

    public ForestBoarRenderer(EntityRendererProvider.Context context) {
        super(context, new ForestBoarModel(ForestBoarModel.createRoot()), 0.55F);
    }

    @Override
    public ForestBoarRenderState createRenderState() {
        return new ForestBoarRenderState();
    }

    @Override
    public void extractRenderState(ForestBoarEntity entity, ForestBoarRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        this.lastExtracted = state;
        ForestBoarMotion m = this.motion.computeIfAbsent(entity,
                e -> new ForestBoarMotion(ForestBoarAnimation.WALK_STRIDE_BLOCKS, ForestBoarAnimation.RUN_STRIDE_BLOCKS));
        m.update(state.x, state.z, state.bodyRot, state.ageInTicks, entity.getBehavior());
        state.behavior = m.behavior;
        state.gaitPhase = m.phase;
        state.walkWeight = m.walkWeight();
        state.runWeight = m.runWeight();
        state.idleWeight = m.idleWeight();
        state.grazeWeight = m.graze;
        state.sniffWeight = m.sniff;
        state.alertWeight = m.alert;
        state.windupWeight = m.windup;
        state.chargeWeight = m.charge;
        state.lookWeight = m.lookWeight();
        state.sniffMillis = ForestBoarMotion.millisSince(m.sniffSince, state.ageInTicks);
        state.alertMillis = ForestBoarMotion.millisSince(m.alertSince, state.ageInTicks);
        state.windupMillis = ForestBoarMotion.millisSince(m.windupSince, state.ageInTicks);
        state.hurtMillis = entity.hurtTime > 0 ? (long) ((entity.hurtDuration - entity.hurtTime + partialTicks) * 50.0F) : -1;
    }

    /** The most recently extracted state (development capture: re-poses the model exactly as last drawn). */
    public ForestBoarRenderState lastExtracted() {
        return this.lastExtracted;
    }

    @Override
    public Identifier getTextureLocation(ForestBoarRenderState state) {
        return TEXTURE;
    }
}
