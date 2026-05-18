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
import zcylas.totality.api.ritual.RitualState;
import zcylas.totality.blockentity.ritual.RitualAltarBlockEntity;

public class RitualAltarRenderer implements BlockEntityRenderer<RitualAltarBlockEntity, RitualAltarRenderer.AltarRenderState> {

    private final ItemModelResolver itemModelResolver;

    public RitualAltarRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    public static class AltarRenderState extends BlockEntityRenderState {
        public ItemStack heldItem = ItemStack.EMPTY;
        public long gameTime = 0;
        public RitualState ritualState = RitualState.IDLE;
        public final ItemStackRenderState itemRenderState = new ItemStackRenderState();
        public float rotation = 0f;
        public RitualState cancelledFromState = RitualState.IDLE;
        public int cancelledAtAnimTick = 0;
        public int animTick = 0;
    }

    @Override
    public AltarRenderState createRenderState() {
        return new AltarRenderState();
    }

    @Override
    public void extractRenderState(RitualAltarBlockEntity blockEntity, AltarRenderState state,
                                   float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {

        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        state.heldItem = blockEntity.getHeldItem().copy();
        state.ritualState = blockEntity.getRitualState();
        state.rotation = blockEntity.rotation;
        state.animTick = blockEntity.getAnimTick();
        state.cancelledFromState = blockEntity.getCancelledFromState();
        state.cancelledAtAnimTick = blockEntity.getCancelledAtAnimTick();
        state.gameTime = blockEntity.getLevel() != null
                ? blockEntity.getLevel().getGameTime()
                : 0;
    }

    @Override
    public void submit(AltarRenderState state, PoseStack poseStack,
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

        float itemY;

        switch (state.ritualState) {
            case ALTAR_TRANSFORM -> itemY = 1.1f + (1.4f - 1.1f) * Math.min(state.animTick / 40f, 1.0f);
            case COMPLETING -> itemY = 1.1f + (1.4f - 1.1f) * Math.max(0f, 1.0f - state.animTick / 40f);
            case CANCELLING -> {
                if (state.cancelledFromState == RitualState.ALTAR_TRANSFORM) {
                    float fromT = Math.min(state.cancelledAtAnimTick / 40f, 1.0f);
                    float cancelT = Math.max(0f, 1.0f - state.animTick / 40f);
                    itemY = 1.1f + (1.4f - 1.1f) * fromT * cancelT;
                } else {
                    itemY = 1.1f + (float) Math.sin(state.gameTime / 20f * Math.PI) * 0.05f;
                }
            }
            default -> itemY = 1.1f + (float) Math.sin(state.gameTime / 20f * Math.PI) * 0.05f;
        }

        poseStack.pushPose();
        poseStack.translate(0.5, itemY, 0.5);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(state.rotation));
        poseStack.scale(0.6f, 0.6f, 0.6f);

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