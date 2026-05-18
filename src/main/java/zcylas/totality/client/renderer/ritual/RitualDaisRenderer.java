package zcylas.totality.client.renderer.ritual;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.blockentity.ritual.RitualDaisBlockEntity;

public class RitualDaisRenderer implements BlockEntityRenderer<RitualDaisBlockEntity, RitualDaisRenderer.DaisRenderState> {

    private final ItemModelResolver itemModelResolver;

    public RitualDaisRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    public static class DaisRenderState extends BlockEntityRenderState {
        public ItemStack heldItem = ItemStack.EMPTY;
        public float animTime = 0f;
        public int ritualActiveTick = 0;
        public float partialTicks = 0f;
        public float rotation = 0f;
        public boolean ritualActive = false;
        public final ItemStackRenderState itemRenderState = new ItemStackRenderState();
    }

    @Override
    public DaisRenderState createRenderState() {
        return new DaisRenderState();
    }

    @Override
    public void extractRenderState(RitualDaisBlockEntity blockEntity, DaisRenderState state,
                                   float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        state.ritualActive = blockEntity.isRitualActive();
        state.heldItem = blockEntity.getHeldItem().copy();
        state.partialTicks = partialTicks;
        state.rotation = blockEntity.rotation;
        state.ritualActiveTick = blockEntity.getRitualActiveTick();
        state.animTime = (float)Math.floorMod(blockEntity.tickCount, 72000) + partialTicks;
    }

    @Override
    public void submit(DaisRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.heldItem.isEmpty()) return;
        itemModelResolver.updateForTopItem(
                state.itemRenderState,
                state.heldItem,
                ItemDisplayContext.FIXED,
                null,
                null,
                0
        );

        float t = Math.min(state.ritualActiveTick / 40f, 1.0f);

        float angle = state.rotation;

        float idleBob = (float) Math.sin(state.animTime * 0.1f) * 0.04f;
        float itemY = 1.25f + (1.6f - 1.25f) * t + idleBob * (1.0f - t);

        poseStack.pushPose();
        poseStack.translate(0.5, itemY, 0.5);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
        poseStack.scale(0.5f, 0.5f, 0.5f);

        state.itemRenderState.submit(
                poseStack,
                collector,
                0xF000F0,
                OverlayTexture.NO_OVERLAY,
                0
        );

        poseStack.popPose();
    }
}