package zcylas.totality.screen.character.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import zcylas.totality.screen.character.CharacterScreen;

public abstract class CharacterScreenTab {

    protected final CharacterScreen screen;
    protected Font font;

    protected CharacterScreenTab(CharacterScreen screen) {
        this.screen = screen;
    }

    /** Called when this tab becomes active — reset scroll/hover state. */
    public void onOpen() {}

    /** Draw this tab's content into the given content area. */
    public abstract void draw(GuiGraphicsExtractor g, Font font,
                              int mx, int my, int ba,
                              int x, int y, int w, int h);

    public boolean keyPressed(int key)                        { return false; }
    public void mouseClicked(int mx, int my)                  {}
    public void mouseScrolled(int mx, int my, double delta)   {}
}