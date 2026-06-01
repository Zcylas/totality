// api/client/gui/SlotGenerator.java
package zcylas.totality.api.client.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.function.Consumer;

/**
 * Fluent slot layout builder for menus.
 * Adapted from owo-lib's SlotGenerator.
 *
 * Usage:
 *   SlotGenerator.begin(this::addSlot, 8, 18)
 *       .grid(inventory, 0, 3, 1)        // 3×1 input slots
 *       .moveTo(8, 84)
 *       .playerInventory(playerInventory); // player inv + hotbar
 */
public final class SlotGenerator {

    private int anchorX, anchorY;
    private int horizontalSpacing = 0;
    private int verticalSpacing   = 0;

    private SlotFactory    slotFactory  = Slot::new;
    private Consumer<Slot> slotConsumer;

    private SlotGenerator(Consumer<Slot> slotConsumer, int anchorX, int anchorY) {
        this.slotConsumer = slotConsumer;
        this.anchorX      = anchorX;
        this.anchorY      = anchorY;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static SlotGenerator begin(Consumer<Slot> slotConsumer, int anchorX, int anchorY) {
        return new SlotGenerator(slotConsumer, anchorX, anchorY);
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    public SlotGenerator moveTo(int anchorX, int anchorY) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        return this;
    }

    public SlotGenerator spacing(int spacing) {
        this.horizontalSpacing = spacing;
        this.verticalSpacing   = spacing;
        return this;
    }

    public SlotGenerator horizontalSpacing(int spacing) {
        this.horizontalSpacing = spacing;
        return this;
    }

    public SlotGenerator verticalSpacing(int spacing) {
        this.verticalSpacing = spacing;
        return this;
    }

    public SlotGenerator slotFactory(SlotFactory slotFactory) {
        this.slotFactory = slotFactory;
        return this;
    }

    public SlotGenerator defaultSlotFactory() {
        this.slotFactory = Slot::new;
        return this;
    }

    public SlotGenerator slotConsumer(Consumer<Slot> slotConsumer) {
        this.slotConsumer = slotConsumer;
        return this;
    }

    // ── Generation ────────────────────────────────────────────────────────────

    /** Add a grid of {@code width × height} slots starting at {@code startIndex}. */
    public SlotGenerator grid(Container container, int startIndex, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                slotConsumer.accept(slotFactory.create(
                        container,
                        startIndex + row * width + col,
                        anchorX + col * (18 + horizontalSpacing),
                        anchorY + row * (18 + verticalSpacing)
                ));
            }
        }
        return this;
    }

    /** Add a single slot at {@code index}. */
    public SlotGenerator single(Container container, int index) {
        slotConsumer.accept(slotFactory.create(container, index, anchorX, anchorY));
        return this;
    }

    /**
     * Add the standard player inventory (9×3) and hotbar (9×1).
     * Expects the anchor to be at the top-left of the inventory grid.
     */
    public SlotGenerator playerInventory(Inventory playerInventory) {
        this.grid(playerInventory, 9, 9, 3); // main inventory
        this.anchorY += 58;
        this.grid(playerInventory, 0, 9, 1); // hotbar
        this.anchorY -= 58;
        return this;
    }

    // ── Slot factory ──────────────────────────────────────────────────────────

    @FunctionalInterface
    public interface SlotFactory {
        Slot create(Container container, int index, int x, int y);
    }
}