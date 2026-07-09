package zcylas.totality.screen.equipment;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import zcylas.totality.menu.equipment.AccessoryInventoryMenu;

import java.lang.reflect.Method;

public class AccessoryInventoryScreen extends AbstractContainerScreen<AccessoryInventoryMenu> {

    private static final int IMAGE_W = 176;
    private static final int IMAGE_H = 166;

    private static final Identifier SLOT_ADDON =
            Identifier.fromNamespaceAndPath("totality", "textures/gui/container/equipment/slot_panel.png");
    private static final int ADDON_W = 26, ADDON_H = 26;

    // Cached reflection handle for InventoryScreen.extractRenderState(LivingEntity) (private static).
    // Null if reflection failed -- falls back to vanilla extractEntityInInventoryFollowsMouse.
    @Nullable
    private static final Method EXTRACT_RENDER_STATE;
    static {
        Method m = null;
        try {
            m = InventoryScreen.class.getDeclaredMethod("extractRenderState", LivingEntity.class);
            m.setAccessible(true);
        } catch (Exception ignored) {}
        EXTRACT_RENDER_STATE = m;
    }

    private final EffectsInInventory effects;

    // Full-body drag rotation -- accumulated over drag gestures, not clamped by atan.
    // entityYaw:   horizontal, degrees, unbounded. 0 = entity faces camera.
    //              entityYaw -= dx on drag -> drag right shows entity left side.
    // entityPitch: vertical tilt, degrees, clamped [-30, 30].
    //              Negative = entity tilts back (looks up toward viewer).
    private float entityYaw;
    private float entityPitch;
    private boolean entityAreaDragging;

    @SuppressWarnings("unused")
    public AccessoryInventoryScreen(AccessoryInventoryMenu menu, Inventory inv, Component title) {
        super(menu, inv, Component.translatable("container.crafting"), IMAGE_W, IMAGE_H);
        this.effects = new EffectsInInventory(this);
        this.titleLabelX = 97;
    }

    @Override
    protected void init() {
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
        entityYaw   = 0f;
        entityPitch = -10f;
        addRenderableWidget(new AccessoryInventoryButton(this));
    }

    // The ring/pouch panel is rendered outside the vanilla 176x166 background box (RING_PANEL_X=178
    // > imageWidth=176) — vanilla's own hasClickedOutside() only knows about that background box,
    // so without this override every click on those slots is misclassified as "clicked outside the
    // inventory", which forces AbstractContainerScreen.mouseClicked to send ContainerInput.THROW
    // instead of PICKUP for a perfectly valid slot — the item gets dropped instead of picked up.
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop) {
        int px = guiLeft + AccessoryInventoryMenu.RING_PANEL_X;
        int py = guiTop + AccessoryInventoryMenu.RING_PANEL_Y;
        int pw = ADDON_W;
        int ph = 4 + 18 * 4 + 4; // matches renderRingPanel's 4-row layout
        if (mouseX >= px && mouseX < px + pw && mouseY >= py && mouseY < py + ph) return false;
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        if (mouse.button() == 0) {
            double mx = mouse.x(), my = mouse.y();
            entityAreaDragging = mx >= leftPos + 26 && mx <= leftPos + 75
                    && my >= topPos + 8 && my <= topPos + 78;
        }
        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent mouse, double dx, double dy) {
        if (mouse.button() == 0 && entityAreaDragging) {
            entityYaw   -= (float) dx;
            entityPitch += (float) dy;
            entityPitch  = Math.max(-30f, Math.min(30f, entityPitch));
            return true;
        }
        return super.mouseDragged(mouse, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouse) {
        entityAreaDragging = false;
        return super.mouseReleased(mouse);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor gui, int mx, int my, float pt) {
        super.extractRenderState(gui, mx, my, pt);
        effects.extractRenderState(gui, mx, my);
    }

    @Override
    public boolean showsActiveEffects() {
        return effects.canSeeEffects();
    }

    private void renderRingPanel(@NonNull GuiGraphicsExtractor gui) {
        int px = leftPos + AccessoryInventoryMenu.RING_PANEL_X;
        int pt = topPos + AccessoryInventoryMenu.RING_PANEL_Y;
        int rows = 4;
        for (int j = 0; j < rows; j++) {
            gui.blit(RenderPipelines.GUI_TEXTURED, SLOT_ADDON, px + 4, pt + 4 + 18 * j, 4, 4, 18, 18, ADDON_W, ADDON_H);
        }
        gui.blit(RenderPipelines.GUI_TEXTURED, SLOT_ADDON, px + 4, pt, 4, 0, 18, 4, ADDON_W, ADDON_H);
        gui.blit(RenderPipelines.GUI_TEXTURED, SLOT_ADDON, px + 4, pt + 4 + 18 * rows, 4, 22, 18, 4, ADDON_W, ADDON_H);
        for (int j = 0; j < rows; j++) {
            gui.blit(RenderPipelines.GUI_TEXTURED, SLOT_ADDON, px,      pt + 4 + 18 * j, 0,  4, 4, 18, ADDON_W, ADDON_H);
            gui.blit(RenderPipelines.GUI_TEXTURED, SLOT_ADDON, px + 22, pt + 4 + 18 * j, 22, 4, 4, 18, ADDON_W, ADDON_H);
        }
        int pb = pt + 4 + 18 * rows;
        gui.blit(RenderPipelines.GUI_TEXTURED, SLOT_ADDON, px,      pt,  0,  0,  4, 4, ADDON_W, ADDON_H);
        gui.blit(RenderPipelines.GUI_TEXTURED, SLOT_ADDON, px + 22, pt,  22, 0,  4, 4, ADDON_W, ADDON_H);
        gui.blit(RenderPipelines.GUI_TEXTURED, SLOT_ADDON, px,      pb,  0,  22, 4, 4, ADDON_W, ADDON_H);
        gui.blit(RenderPipelines.GUI_TEXTURED, SLOT_ADDON, px + 22, pb,  22, 22, 4, 4, ADDON_W, ADDON_H);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor gui, int mx, int my, float pt) {
        super.extractBackground(gui, mx, my, pt);
        LocalPlayer player = minecraft.player;
        if (player == null) return;

        gui.blit(RenderPipelines.GUI_TEXTURED, InventoryScreen.INVENTORY_LOCATION,
                leftPos, topPos, 0, 0, 176, 166, 256, 256);

        renderRingPanel(gui);
        renderEntityWithRotation(gui, player);
    }

    private void renderEntityWithRotation(@NonNull GuiGraphicsExtractor gui, @NonNull LivingEntity entity) {
        if (EXTRACT_RENDER_STATE == null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    gui, leftPos + 26, topPos + 8, leftPos + 75, topPos + 78,
                    30, 0.0625f, leftPos + 51f, topPos + 30f, entity);
            return;
        }

        try {
            EntityRenderState state = (EntityRenderState) EXTRACT_RENDER_STATE.invoke(null, entity);

            if (state instanceof LivingEntityRenderState lrs) {
                // bodyRot=180+entityYaw: full 360-degree body spin, no atan limit.
                // bodyRot=180 faces camera; bodyRot=90 shows left side; bodyRot=0 shows back.
                // yRot=0: head stays neutral in model-space (same as vanilla at center-mouse).
                // yRot=entityYaw would produce an owl effect at 180-degree spin because
                // the model-space head twist exactly cancels the body rotation.
                lrs.bodyRot = 180f + entityYaw;
                lrs.yRot    = 0f;
                lrs.xRot    = entityPitch;
                lrs.boundingBoxWidth  /= lrs.scale;
                lrs.boundingBoxHeight /= lrs.scale;
                lrs.scale = 1f;
            }

            float pitchRad = -entityPitch * ((float) Math.PI / 180f);
            Quaternionf q2 = new Quaternionf().rotateX(pitchRad);
            Quaternionf q1 = new Quaternionf().rotateZ((float) Math.PI).mul(q2);

            Vector3f pos = new Vector3f(0f, state.boundingBoxHeight / 2f + 0.0625f, 0f);

            gui.entity(state, 30f, pos, q1, q2,
                    leftPos + 26, topPos + 8, leftPos + 75, topPos + 78);

        } catch (Exception e) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    gui, leftPos + 26, topPos + 8, leftPos + 75, topPos + 78,
                    30, 0.0625f, leftPos + 51f, topPos + 30f, entity);
        }
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor gui, int mx, int my) {
        gui.text(font, title, titleLabelX, titleLabelY, -12566464, false);
    }

    @Override
    protected void extractTooltip(@NonNull GuiGraphicsExtractor gui, int mx, int my) {
        if (menu.getCarried().isEmpty() && hoveredSlot != null && !hoveredSlot.hasItem()) {
            int idx = menu.slots.indexOf(hoveredSlot);
            Component tip = switch (idx) {
                case 5  -> Component.literal("Helmet");
                case 6  -> Component.literal("Chestplate");
                case 7  -> Component.literal("Leggings");
                case 8  -> Component.literal("Boots");
                case 45 -> Component.literal("Offhand");
                case 46 -> Component.literal("Phone");
                case 47 -> Component.literal("Left Ring");
                case 48 -> Component.literal("Right Ring");
                case 49 -> Component.literal("Pouch");
                default -> null;
            };
            if (tip != null) {
                gui.setTooltipForNextFrame(font, tip, mx, my);
                return;
            }
        }
        super.extractTooltip(gui, mx, my);
    }
}