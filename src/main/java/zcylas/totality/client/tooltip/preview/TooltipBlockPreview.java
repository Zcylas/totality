package zcylas.totality.client.tooltip.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code BLOCK_MODEL} source: the hovered {@link BlockItem}'s placed block state, resolved into block-model
 * render states through the game's own {@link BlockModelResolver} — the path vanilla uses for block display
 * entities, primed TNT and carried blocks. This draws the block-state model (blockstate variants/multipart,
 * plus 26.2's built-in block models such as chests), not the item's GUI model.
 *
 * <p>The placed state is the block's default state with the stack's {@code block_state} component applied.
 * A two-block-tall block (door, tall flower) is shown whole: its lower half with its upper half above.
 * Resolved per frame and never retained, like the item preview.
 */
public final class TooltipBlockPreview {

    /** Totality's own display context for the preview (block models receive it, none currently branch on it). */
    private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();

    /** One block-model piece of the preview, stacked {@code yOffset} blocks above the base block. */
    public record Part(BlockModelRenderState model, int yOffset) {}

    /**
     * A resolved block preview.
     *
     * @param parts  the non-empty block-model pieces
     * @param bounds union of the pieces' outline shapes, in block units — used to fit and centre the subject
     */
    public record Resolved(List<Part> parts, AABB bounds) {}

    /** The placed state for this stack, or {@code null} when the item does not place a block. */
    public static @Nullable BlockState placedState(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        BlockState state = blockItem.getBlock().defaultBlockState();
        BlockItemStateProperties properties = stack.get(DataComponents.BLOCK_STATE);
        return properties != null ? properties.apply(state) : state;
    }

    /** Resolves the block-state model preview, or {@code null} when the item has no drawable block model. */
    public static @Nullable Resolved resolve(ItemStack stack) {
        BlockState placed = placedState(stack);
        if (placed == null) return null;
        BlockModelResolver resolver = new BlockModelResolver(Minecraft.getInstance().getModelManager());
        List<Part> parts = new ArrayList<>();
        AABB bounds = null;
        List<BlockState> pieces = pieces(placed);
        for (int y = 0; y < pieces.size(); y++) {
            BlockModelRenderState model = new BlockModelRenderState();
            resolver.update(model, pieces.get(y), DISPLAY_CONTEXT);
            if (model.isEmpty()) continue;
            parts.add(new Part(model, y));
            AABB box = shapeBounds(pieces.get(y)).move(0, y, 0);
            bounds = bounds == null ? box : bounds.minmax(box);
        }
        return parts.isEmpty() ? null : new Resolved(List.copyOf(parts), bounds);
    }

    /** The block states that make up the placed block, bottom first. */
    static List<BlockState> pieces(BlockState placed) {
        if (!placed.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) return List.of(placed);
        return List.of(placed.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                placed.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    /** The piece's outline shape bounds in its own block space; a full block when it has no outline. */
    private static AABB shapeBounds(BlockState state) {
        VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
    }

    private TooltipBlockPreview() {}
}
