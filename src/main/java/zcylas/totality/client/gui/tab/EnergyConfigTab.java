package zcylas.totality.client.gui.tab;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import zcylas.totality.api.industrial.energy.base.SimpleSidedUEContainer;
import zcylas.totality.client.config.SideModeClientCache;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.networking.config.SideModePayload;

import java.util.function.Supplier;

public class EnergyConfigTab extends GuiTab {

    private static final int SQUARE_SIZE = 16;
    private static final int PADDING = 3;
    private static final int PANEL_PADDING = 6;
    private static final int ORIGIN_X = 8;
    private static final int ORIGIN_Y = 15;
    private static final int CLOSE_BTN_SIZE = 13;

    private final BlockPos pos;
    private final Supplier<Direction> facingSupplier;
    private final net.minecraft.client.gui.Font font = Minecraft.getInstance().font;

    public EnergyConfigTab(BlockPos pos, Supplier<Direction> facingSupplier) {
        this.pos = pos;
        this.facingSupplier = facingSupplier;
    }

    public EnergyConfigTab(BlockPos pos) {
        this(pos, () -> Direction.NORTH);
    }

    @Override
    public void open() {
        configTabOpenPos = pos;
        GuiTab.pin(pos, "energy");
    }

    @Override
    public void close() {
        configTabOpenPos = null;
        // keep pinned — only X button unpins
    }

    private boolean closedByX = false;

    public boolean wasClosedByX() {
        return closedByX;
    }

    public void resetClosedByX() {
        closedByX = false;
    }

    public void closeAndUnpin() {
        configTabOpenPos = null;
        closedByX = true;
        GuiTab.unpin(pos);
    }

    @Override
    public void draw(GuiGraphicsExtractor drawContext, int guiLeft, int guiTop, int mouseX, int mouseY) {
        int step = SQUARE_SIZE + PADDING;
        int panelX = guiLeft + ORIGIN_X - PANEL_PADDING;
        int panelY = guiTop + ORIGIN_Y - PANEL_PADDING;
        int panelW = step * 3 + PANEL_PADDING * 2;
        int panelH = step * 3 + PANEL_PADDING * 2;

        drawContext.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.CONFIG_BACKGROUND,
                panelX, panelY, panelW, panelH);

        // X button in top-right corner
        int xBtnX = panelX + panelW - CLOSE_BTN_SIZE - 3;
        int xBtnY = panelY + 3;
        boolean hovered = mouseX >= xBtnX && mouseX < xBtnX + CLOSE_BTN_SIZE
                && mouseY >= xBtnY && mouseY < xBtnY + CLOSE_BTN_SIZE;
        drawContext.blitSprite(RenderPipelines.GUI_TEXTURED,
                hovered ? TotalityGuiSprites.CLOSE_BUTTON_HOVERED : TotalityGuiSprites.CLOSE_BUTTON,
                xBtnX, xBtnY, CLOSE_BTN_SIZE, CLOSE_BTN_SIZE);

        for (Direction dir : Direction.values()) {
            SimpleSidedUEContainer.SideMode mode = SideModeClientCache.get(pos, dir);
            int[] xy = getFaceSquarePos(dir);
            int x = guiLeft + xy[0];
            int y = guiTop + xy[1];

            drawContext.fill(x, y, x + SQUARE_SIZE, y + SQUARE_SIZE, 0xFF000000 | mode.getColor());

            if (mouseX >= x && mouseX < x + SQUARE_SIZE && mouseY >= y && mouseY < y + SQUARE_SIZE) {
                drawContext.fill(x, y, x + SQUARE_SIZE, y + SQUARE_SIZE, 0x44FFFFFF);
            }
        }
    }

    @Override
    public boolean click(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int step = SQUARE_SIZE + PADDING;
        int panelX = ORIGIN_X - PANEL_PADDING;
        int panelY = ORIGIN_Y - PANEL_PADDING;
        int panelW = step * 3 + PANEL_PADDING * 2;

        // X button click
        int xBtnX = panelX + panelW - CLOSE_BTN_SIZE;
        int xBtnY = panelY;
        if (mouseX >= xBtnX && mouseX < xBtnX + CLOSE_BTN_SIZE
                && mouseY >= xBtnY && mouseY < xBtnY + CLOSE_BTN_SIZE) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            closeAndUnpin();
            return true;
        }

        for (Direction dir : Direction.values()) {
            int[] xy = getFaceSquarePos(dir);
            if (mouseX >= xy[0] && mouseX < xy[0] + SQUARE_SIZE
                    && mouseY >= xy[1] && mouseY < xy[1] + SQUARE_SIZE) {
                SimpleSidedUEContainer.SideMode current = SideModeClientCache.get(pos, dir);
                SimpleSidedUEContainer.SideMode next = current.next();
                SideModeClientCache.set(pos, dir, next);
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                ClientPlayNetworking.send(new SideModePayload(pos, dir, current));
                return true;
            }
        }
        return false;
    }

    private int[] getFaceSquarePos(Direction dir) {
        Direction facing = facingSupplier.get();
        int step = SQUARE_SIZE + PADDING;

        if (dir == Direction.UP)
            return new int[]{ORIGIN_X + step, ORIGIN_Y};
        if (dir == facing)
            return new int[]{ORIGIN_X + step, ORIGIN_Y + step};
        if (dir == facing.getClockWise())
            return new int[]{ORIGIN_X, ORIGIN_Y + step};
        if (dir == facing.getCounterClockWise())
            return new int[]{ORIGIN_X + step * 2, ORIGIN_Y + step};
        if (dir == facing.getOpposite())
            return new int[]{ORIGIN_X + step, ORIGIN_Y + step * 2};
        if (dir == Direction.DOWN)
            return new int[]{ORIGIN_X + step * 2, ORIGIN_Y + step * 2};

        return new int[]{0, 0};
    }



    private static BlockPos configTabOpenPos = null;

    public static boolean isConfigTabOpen(BlockPos pos) {
        return pos.equals(configTabOpenPos);
    }
}