package zcylas.totality.client.renderer.entity.basicweapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.client.renderer.entity.state.basicweapon.ThrownShurikenRenderState;
import zcylas.totality.entity.base_weapon.ThrownShurikenEntity;
import zcylas.totality.init.items.BasicWeaponItems;

public class ThrownShurikenRenderer extends EntityRenderer<ThrownShurikenEntity, ThrownShurikenRenderState> {

    private final ItemModelResolver itemModelResolver;

    public ThrownShurikenRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public ThrownShurikenRenderState createRenderState() {
        return new ThrownShurikenRenderState();
    }

    @Override
    public void extractRenderState(ThrownShurikenEntity entity, ThrownShurikenRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.inGround = entity.isGrounded();
        state.spin = state.inGround ? 0.0f : (entity.tickCount + partialTicks) * 30.0f;

        ItemStack renderItem = entity.getShurikenItem();
        if (renderItem.isEmpty()) return; // don't wipe the render state if data hasn't synced yet

        this.itemModelResolver.updateForNonLiving(
                state.item,
                renderItem,
                ItemDisplayContext.FIXED,
                entity
        );
    }

    @Override
    public void submit(ThrownShurikenRenderState state, PoseStack poseStack,
                       SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(camera.orientation);

        if (state.inGround) {
            // Show face-on when stuck so it's visible
            // no extra rotation needed, just flat facing camera
        } else {
            // Edge-on and spinning when in flight
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(state.spin));
        }

        state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}