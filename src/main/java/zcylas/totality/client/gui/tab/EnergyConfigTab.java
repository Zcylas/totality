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

    private static final int SQUARE_SIZE    = 20;
    private static final int PADDING        = 5;
    private static final int PANEL_PADDING  = 6;
    private static final int ORIGIN_X       = 8;
    private static final int ORIGIN_Y       = 15;
    private static final int CLOSE_BTN_SIZE = 13;
    private static final int LABEL_H        = 8;

    // ── Layout (viewed from front of machine) ─────────────────────────────────
    //
    //  col:   0       1        2
    //  row 0:       [TOP]
    //  row 1: [LEFT][FRONT][RIGHT]
    //  row 2:       [BACK] [BOT]
    //
    // For non-directional blocks (hasDirectionalFacing = false), labels use
    // absolute directions: UP/DOWN/NORTH/SOUTH/WEST/EAST.
    // ──────────────────────────────────────────────────────────────────────────

    private final BlockPos           pos;
    private final Supplier<Direction> facingSupplier;
    private final boolean            hasDirectionalFacing;
    private final net.minecraft.client.gui.Font font = Minecraft.getInstance().font;

    public EnergyConfigTab(BlockPos pos, Supplier<Direction> facingSupplier, boolean hasDirectionalFacing) {
        this.pos                  = pos;
        this.facingSupplier       = facingSupplier;
        this.hasDirectionalFacing = hasDirectionalFacing;
    }

    /** Directional block — uses FRONT/BACK/LEFT/RIGHT labels. */
    public EnergyConfigTab(BlockPos pos, Supplier<Direction> facingSupplier) {
        this(pos, facingSupplier, true);
    }

    /** Non-directional block (e.g. Power Cell) — uses absolute direction labels. */
    public EnergyConfigTab(BlockPos pos) {
        this(pos, () -> Direction.NORTH, false);
    }

    @Override
    public void open() {
        configTabOpenPos = pos;
        GuiTab.pin(pos, "energy");
    }

    @Override
    public void close() {
        configTabOpenPos = null;
    }

    private boolean closedByX = false;
    public boolean wasClosedByX()  { return closedByX; }
    public void resetClosedByX()   { closedByX = false; }

    public void closeAndUnpin() {
        configTabOpenPos = null;
        closedByX = true;
        GuiTab.unpin(pos);
    }

    @Override
    public void draw(GuiGraphicsExtractor drawContext, int guiLeft, int guiTop, int mouseX, int mouseY) {
        int step   = SQUARE_SIZE + PADDING;
        int cellH  = SQUARE_SIZE + LABEL_H + 2;
        int stepY  = cellH + PADDING;

        // Panel covers 3 cols × 3 rows (but only 2 cols wide for rows 0 and 2)
        // We size it to fit the 3×3 logical grid
        int panelX = guiLeft + ORIGIN_X - PANEL_PADDING;
        int panelY = guiTop  + ORIGIN_Y - PANEL_PADDING;
        int panelW = step * 3 + PANEL_PADDING * 2;
        int panelH = stepY * 3 + PANEL_PADDING * 2;

        drawContext.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.CONFIG_BACKGROUND,
                panelX, panelY, panelW, panelH);

        // X button
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
            int y = guiTop  + xy[1];

            // Square
            drawContext.fill(x, y, x + SQUARE_SIZE, y + SQUARE_SIZE, 0xFF000000 | mode.getColor());
            if (mouseX >= x && mouseX < x + SQUARE_SIZE && mouseY >= y && mouseY < y + SQUARE_SIZE)
                drawContext.fill(x, y, x + SQUARE_SIZE, y + SQUARE_SIZE, 0x44FFFFFF);

            // Label below square
            String label = getFaceLabel(dir);
            int labelX = x + SQUARE_SIZE / 2 - font.width(label) / 2;
            int labelY = y + SQUARE_SIZE + 2;
            drawContext.text(font, label, labelX, labelY, 0xFFAAAAAA, false);
        }
    }

    @Override
    public boolean click(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int step   = SQUARE_SIZE + PADDING;
        int stepY  = (SQUARE_SIZE + LABEL_H + 2) + PADDING;
        int panelX = ORIGIN_X - PANEL_PADDING;
        int panelY = ORIGIN_Y - PANEL_PADDING;
        int panelW = step * 3 + PANEL_PADDING * 2;

        // X button
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
                SimpleSidedUEContainer.SideMode next    = current.next();
                SideModeClientCache.set(pos, dir, next);
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                ClientPlayNetworking.send(new SideModePayload(pos, dir, current));
                return true;
            }
        }
        return false;
    }

    // ── Face layout ───────────────────────────────────────────────────────────
    //
    //  col:   0       1        2
    //  row 0:       [TOP]
    //  row 1: [LEFT][FRONT][RIGHT]
    //  row 2:       [BACK] [BOT]

    private int[] getFaceSquarePos(Direction dir) {
        Direction facing = facingSupplier.get();
        int step  = SQUARE_SIZE + PADDING;
        int stepY = (SQUARE_SIZE + LABEL_H + 2) + PADDING;

        // Guard: getClockWise/getCounterClockWise throw on UP/DOWN
        if (facing.getAxis() == Direction.Axis.Y) {
            return getFaceSquarePosAbsolute(dir);
        }

        if (dir == Direction.UP)                  return new int[]{ORIGIN_X + step,     ORIGIN_Y};
        if (dir == facing)                        return new int[]{ORIGIN_X + step,     ORIGIN_Y + stepY};
        if (dir == facing.getClockWise())         return new int[]{ORIGIN_X,            ORIGIN_Y + stepY};
        if (dir == facing.getCounterClockWise())  return new int[]{ORIGIN_X + step * 2, ORIGIN_Y + stepY};
        if (dir == facing.getOpposite())          return new int[]{ORIGIN_X + step,     ORIGIN_Y + stepY * 2};
        if (dir == Direction.DOWN)                return new int[]{ORIGIN_X + step * 2, ORIGIN_Y + stepY * 2};

        return new int[]{0, 0};
    }

    private int[] getFaceSquarePosAbsolute(Direction dir) {
        int step  = SQUARE_SIZE + PADDING;
        int stepY = (SQUARE_SIZE + LABEL_H + 2) + PADDING;
        return switch (dir) {
            case UP    -> new int[]{ORIGIN_X + step,     ORIGIN_Y};
            case NORTH -> new int[]{ORIGIN_X + step,     ORIGIN_Y + stepY};
            case WEST  -> new int[]{ORIGIN_X,            ORIGIN_Y + stepY};
            case EAST  -> new int[]{ORIGIN_X + step * 2, ORIGIN_Y + stepY};
            case SOUTH -> new int[]{ORIGIN_X + step,     ORIGIN_Y + stepY * 2};
            case DOWN  -> new int[]{ORIGIN_X + step * 2, ORIGIN_Y + stepY * 2};
        };
    }

    private String getFaceLabel(Direction dir) {
        Direction facing = facingSupplier.get();

        if (!hasDirectionalFacing || facing.getAxis() == Direction.Axis.Y) {
            return switch (dir) {
                case UP    -> "Top";
                case DOWN  -> "Bot";
                case NORTH -> "North";
                case SOUTH -> "South";
                case WEST  -> "West";
                case EAST  -> "East";
            };
        }

        if (dir == Direction.UP)                  return "Top";
        if (dir == Direction.DOWN)                return "Bot";
        if (dir == facing)                        return "Front";
        if (dir == facing.getOpposite())          return "Back";
        if (dir == facing.getClockWise())         return "Left";
        if (dir == facing.getCounterClockWise())  return "Right";
        return dir.getName();
    }

    // ── Static state ──────────────────────────────────────────────────────────

    private static BlockPos configTabOpenPos = null;

    public static boolean isConfigTabOpen(BlockPos pos) {
        return pos.equals(configTabOpenPos);
    }
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        int step  = SQUARE_SIZE + PADDING;
        int stepY = (SQUARE_SIZE + LABEL_H + 2) + PADDING;
        int panelX = ORIGIN_X - PANEL_PADDING;
        int panelY = ORIGIN_Y - PANEL_PADDING;
        int panelW = step * 3 + PANEL_PADDING * 2;
        int panelH = stepY * 3 + PANEL_PADDING * 2;
        return mouseX >= panelX && mouseX < panelX + panelW
                && mouseY >= panelY && mouseY < panelY + panelH;
    }
}