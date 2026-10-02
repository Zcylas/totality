package zcylas.totality.client.entity.slime;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.entity.dev.SlimeTestEntity;
import zcylas.totality.entity.dev.SlimeTestEyeStyle;
import zcylas.totality.entity.dev.SlimeTestRenderMode;
import zcylas.totality.entity.dev.SlimeTestVariant;

/**
 * DEVELOPMENT TEST renderer for {@code totality:slime_test}: the original 128x128 grayscale texture, unchanged. In
 * the palette modes body and eyes are drawn in one pass from the variant's generated texture
 * ({@link SlimePaletteTexture}: palette, element motif unless "plain", element eye colours if selected); the
 * grayscale variant draws the source texture the same way. In tint mode the body is the grayscale texture multiplied
 * by the variant's uniform tint and the eyes are drawn untinted from the grayscale texture by {@link EyesLayer}.
 */
public class SlimeTestRenderer extends MobRenderer<SlimeTestEntity, SlimeTestRenderState, SlimeTestModel> {

    static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "textures/entity/slime_test/slime_base.png");

    private final SlimeTestModel fullModel;
    private final SlimeTestModel bodyModel = SlimeTestModel.body();

    public SlimeTestRenderer(EntityRendererProvider.Context context) {
        super(context, SlimeTestModel.full(), 0.4F);
        this.fullModel = this.model;
        this.addLayer(new EyesLayer(this));
    }

    @Override
    public SlimeTestRenderState createRenderState() {
        return new SlimeTestRenderState();
    }

    @Override
    public void extractRenderState(SlimeTestEntity entity, SlimeTestRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        SlimeTestVariant variant = entity.getVariant();
        SlimeTestRenderMode mode = entity.getRenderMode();
        SlimePalette palette = SlimePalettes.forVariant(variant);
        state.singlePass = palette == null || mode != SlimeTestRenderMode.TINT;
        if (palette == null) {
            state.bodyTexture = TEXTURE;
            state.bodyTint = -1;
        } else if (mode == SlimeTestRenderMode.TINT) {
            state.bodyTexture = TEXTURE;
            state.bodyTint = variant.tint;
        } else {
            SlimeMotif motif = mode == SlimeTestRenderMode.PLAIN ? null : SlimePalettes.motifFor(variant);
            SlimeEyeColours eyes = entity.getEyeStyle() == SlimeTestEyeStyle.ELEMENT ? SlimePalettes.eyesFor(variant) : null;
            state.bodyTexture = SlimePaletteTexture.idFor(palette, motif, eyes);
            state.bodyTint = -1;
        }
    }

    @Override
    public void submit(SlimeTestRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        this.model = state.singlePass ? this.fullModel : this.bodyModel;
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    @Override
    protected int getModelTint(SlimeTestRenderState state) {
        return state.bodyTint;
    }

    @Override
    public Identifier getTextureLocation(SlimeTestRenderState state) {
        return state.bodyTexture;
    }

    /** Tint mode only: the eye plates, untinted, from the grayscale texture with the same pose as the body. */
    private static class EyesLayer extends RenderLayer<SlimeTestRenderState, SlimeTestModel> {

        private final SlimeTestModel eyes = SlimeTestModel.eyes();

        EyesLayer(RenderLayerParent<SlimeTestRenderState, SlimeTestModel> renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
                           SlimeTestRenderState state, float yRot, float xRot) {
            if (!state.singlePass) {
                coloredCutoutModelCopyLayerRender(this.eyes, TEXTURE, poseStack, submitNodeCollector, lightCoords, state, -1, 0);
            }
        }
    }
}
