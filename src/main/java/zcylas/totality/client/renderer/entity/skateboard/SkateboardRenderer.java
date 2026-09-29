package zcylas.totality.client.renderer.entity.skateboard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import zcylas.totality.Totality;
import zcylas.totality.entity.vehicle.SkateboardEntity;

/**
 * Draws the default skateboard: its four regions (deck, grip, trucks, wheels) each with its own texture under
 * textures/entity/skateboard/, so a future deck material, grip design, truck finish or wheel skin replaces one texture
 * alone. Back faces are culled: the grip planes are one-sided, and nothing is drawn twice at one depth.
 * <p>The board's transform (heading, hurt wobble, lean) is applied here only; the rider is positioned by the entity.
 */
public class SkateboardRenderer extends EntityRenderer<SkateboardEntity, SkateboardRenderState> {

    private static final RenderType DECK = texture("deck");
    private static final RenderType GRIP = texture("grip");
    private static final RenderType TRUCKS = texture("trucks");
    private static final RenderType WHEELS = texture("wheels");

    private final SkateboardModel model;

    public SkateboardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SkateboardModel(SkateboardModel.createLayer().bakeRoot());
        this.shadowRadius = 0.45F;
    }

    private static RenderType texture(String name) {
        return RenderTypes.entityCutoutCull(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "textures/entity/skateboard/" + name + ".png"));
    }

    @Override
    public SkateboardRenderState createRenderState() {
        return new SkateboardRenderState();
    }

    @Override
    public void extractRenderState(SkateboardEntity entity, SkateboardRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.lean = entity.lean(partialTicks);
        state.wheelSpin = entity.wheelSpin(partialTicks);
        state.hurtTime = entity.getHurtTime() - partialTicks;
        state.hurtDir = entity.getHurtDir();
        state.damageTime = Math.max(entity.getDamage() - partialTicks, 0.0F);
    }

    @Override
    public void submit(SkateboardRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
        if (state.hurtTime > 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(state.hurtTime) * state.hurtTime * state.damageTime / 10.0F * state.hurtDir));
        }
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        this.model.setupAnim(state.lean, state.wheelSpin);
        // Wheels and hangers stand on the ground; the deck, grip and truck mounts ride on the leaning frame.
        this.submitPart(this.model.wheels, WHEELS, state, poseStack, collector);
        this.submitPart(this.model.hangers, TRUCKS, state, poseStack, collector);
        poseStack.pushPose();
        this.model.deckFrame.translateAndRotate(poseStack);
        this.submitPart(this.model.deck, DECK, state, poseStack, collector);
        this.submitPart(this.model.grip, GRIP, state, poseStack, collector);
        this.submitPart(this.model.truckMounts, TRUCKS, state, poseStack, collector);
        poseStack.popPose();
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private void submitPart(ModelPart part, RenderType type, SkateboardRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        collector.submitModelPart(part, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, null, state.outlineColor);
    }
}
