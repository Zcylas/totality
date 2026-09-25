package zcylas.totality.client.tooltip.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.AbstractSkullBlock;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The {@code EQUIPPED_PLAYER} companion card: a small, content-sized card beside the main tooltip showing
 * the local player wearing the hovered armor — "what would this look like on me?".
 *
 * <p>How the hovered item is applied without touching gameplay state: every frame the local player's
 * renderer creates a fresh {@link EntityRenderState} snapshot (exactly what vanilla's inventory screen
 * does for its player preview), which already carries the real skin, slim/wide model and every currently
 * worn piece. Only that snapshot's field for the hovered item's slot is replaced with the hovered stack;
 * the other three armor slots keep what the player actually wears. The snapshot is then drawn through
 * vanilla's supported entity picture-in-picture path ({@code graphics.entity}). The player's real
 * equipment, inventory and entity are never modified, no packet is sent, no entity is created, and no
 * reference to the player or level is kept after the draw call.
 */
public final class TooltipCompanionCard {

    /** Card size in GUI units — its own natural size, independent of the main tooltip's height. */
    public static final int WIDTH = 60;
    public static final int HEIGHT = 92;
    /** Inset of the player viewport inside the card frame. */
    public static final int INSET = 5;
    /** Static three-quarter view, degrees away from facing the viewer. Calm, never mouse-following. */
    private static final float THREE_QUARTER_YAW = 25.0F;

    /** Draws the player preview into the card's inner viewport. Nothing is drawn without a local player. */
    public static void drawPlayer(GuiGraphicsExtractor graphics, ItemStack hovered, int cardX, int cardY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        EntityRenderer<? super LocalPlayer, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        EntityRenderState state = renderer.createRenderState(player, 1.0F);
        state.shadowPieces.clear();
        state.outlineColor = 0;

        if (state instanceof HumanoidRenderState humanoid) applyVirtualEquipment(humanoid, hovered, player);
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180.0F + THREE_QUARTER_YAW;
            living.yRot = 0.0F;
            living.xRot = 0.0F;
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1.0F;
        }

        int x0 = cardX + INSET, y0 = cardY + INSET;
        int x1 = cardX + WIDTH - INSET, y1 = cardY + HEIGHT - INSET;
        float size = playerScale(y1 - y0, state.boundingBoxHeight);
        Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F, 0.0F);
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        graphics.entity(state, size, translation, rotation, new Quaternionf(), x0, y0, x1, y1);
    }

    /** Puts the hovered item into the snapshot's matching armor slot; other slots keep what the player wears. */
    static void applyVirtualEquipment(HumanoidRenderState state, ItemStack hovered, LivingEntity wearer) {
        Equippable equippable = hovered.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return;
        switch (equippable.slot()) {
            case HEAD -> applyVirtualHead(state, hovered, wearer);
            case CHEST -> state.chestEquipment = hovered;
            case LEGS -> state.legsEquipment = hovered;
            case FEET -> state.feetEquipment = hovered;
            default -> { }
        }
    }

    /**
     * The head slot is drawn by two layers: armor with an equipment asset via {@code headEquipment}, and
     * worn skulls / carved pumpkins / other head items via {@code wornHeadType} / {@code headItem}. Every
     * field of that slot is replaced exactly the way {@code LivingEntityRenderer} and
     * {@code HumanoidMobRenderer} fill them from a real head item, so the hovered item takes the head
     * slot alone — a worn skull or pumpkin no longer overlaps a previewed helmet, and a previewed skull
     * or pumpkin shows as it would when worn.
     */
    private static void applyVirtualHead(HumanoidRenderState state, ItemStack hovered, LivingEntity wearer) {
        state.headEquipment = HumanoidArmorLayer.shouldRender(hovered, EquipmentSlot.HEAD) ? hovered : ItemStack.EMPTY;
        if (hovered.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof AbstractSkullBlock skull) {
            state.wornHeadType = skull.getType();
            state.wornHeadProfile = hovered.get(DataComponents.PROFILE);
            state.headItem.clear();
        } else {
            state.wornHeadType = null;
            state.wornHeadProfile = null;
            if (!HumanoidArmorLayer.shouldRender(hovered, EquipmentSlot.HEAD)) {
                Minecraft.getInstance().getItemModelResolver().updateForLiving(state.headItem, hovered, ItemDisplayContext.HEAD, wearer);
            } else {
                state.headItem.clear();
            }
        }
    }

    /** GUI units per block so the whole player (plus a little headroom) fits the card's viewport height. */
    static float playerScale(int viewportHeight, float boundingBoxHeight) {
        float height = boundingBoxHeight > 0 ? boundingBoxHeight : 1.8F;
        return viewportHeight / (height * 1.15F);
    }

    private TooltipCompanionCard() {}
}
