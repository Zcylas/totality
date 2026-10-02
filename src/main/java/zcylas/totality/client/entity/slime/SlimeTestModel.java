package zcylas.totality.client.entity.slime;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * DEVELOPMENT TEST model for the V1 Small Slime: the unmodified exported geometry ({@link SlimeBaseGeometry}) with
 * its {@code RENDER_SCALE} applied on the "root" part, whose pivot is the ground, so the slime stays standing on it.
 * The full model draws body and eyes from one generated texture; in tint mode one instance shows only the body and
 * another only the eyes, so the body can be tinted on its own. No animation.
 */
public class SlimeTestModel extends EntityModel<LivingEntityRenderState> {

    private SlimeTestModel(ModelPart modelRoot, boolean body, boolean eyes) {
        super(modelRoot);
        ModelPart root = modelRoot.getChild("root");
        root.setInitialPose(root.getInitialPose().withScale(SlimeBaseGeometry.RENDER_SCALE));
        root.resetPose();
        root.getChild("body").visible = body;
        root.getChild("eyes").visible = eyes;
    }

    /** Body and eyes, drawn in one pass from one texture (palette modes and grayscale). */
    public static SlimeTestModel full() {
        return new SlimeTestModel(SlimeBaseGeometry.createRoot(), true, true);
    }

    /** The body only (tint mode: the body is tinted, the eyes are drawn untinted by a layer). */
    public static SlimeTestModel body() {
        return new SlimeTestModel(SlimeBaseGeometry.createRoot(), true, false);
    }

    public static SlimeTestModel eyes() {
        return new SlimeTestModel(SlimeBaseGeometry.createRoot(), false, true);
    }
}
