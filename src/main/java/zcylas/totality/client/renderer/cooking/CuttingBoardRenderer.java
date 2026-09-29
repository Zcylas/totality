package zcylas.totality.client.renderer.cooking;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import zcylas.totality.block.cooking.CuttingBoardBlock;
import zcylas.totality.blockentity.cooking.CuttingBoardBlockEntity;

/**
 * Draws the stored ingredient lying on the board's cutting surface: one item laid flat for a single ingredient,
 * up to three (slightly turned, layered) for a larger stack, turned with the board's facing.
 */
public class CuttingBoardRenderer implements BlockEntityRenderer<CuttingBoardBlockEntity, CuttingBoardRenderer.State> {

    /** Rest spots on the 10 x 8 px surface, relative to its centre, in model pixels: x, z, yaw (degrees). */
    private static final float[][] SPOTS = {
            {0.0F, 0.0F, 15.0F},
            {-2.5F, -1.2F, -30.0F},
            {2.4F, 1.3F, 55.0F},
    };
    private static final float SURFACE_CENTRE_X = 6.0F / 16.0F;
    private static final float SURFACE_Y = 2.0F / 16.0F;
    private static final float ITEM_SCALE = 0.7F;

    private final ItemModelResolver itemModelResolver;

    public CuttingBoardRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public int shown;
        public final ItemStackRenderState item = new ItemStackRenderState();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CuttingBoardBlockEntity board, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(board, state, breakProgress);
        ItemStack ingredient = board.getIngredient();
        state.facing = board.getBlockState().getValue(CuttingBoardBlock.FACING);
        state.shown = Math.min(ingredient.getCount(), SPOTS.length);
        itemModelResolver.updateForTopItem(state.item, ingredient, ItemDisplayContext.FIXED, board.getLevel(), null,
                (int) board.getBlockPos().asLong());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.shown == 0 || state.item.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.facing.toYRot()));   // model authored facing north
        poseStack.translate(SURFACE_CENTRE_X - 0.5F, SURFACE_Y, 0.0F);
        for (int i = 0; i < state.shown; i++) {
            float[] spot = SPOTS[state.shown == 1 ? 0 : i];
            poseStack.pushPose();
            // each layer a hair higher, so overlapping ingredients never z-fight
            poseStack.translate(spot[0] / 16.0F, ITEM_SCALE / 32.0F + i * 0.004F, spot[1] / 16.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(spot[2]));
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
