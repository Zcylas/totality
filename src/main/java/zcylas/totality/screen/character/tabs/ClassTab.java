package zcylas.totality.screen.character.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import zcylas.totality.screen.character.CharacterScreen;

public class ClassTab extends CharacterScreenTab {
    public ClassTab(CharacterScreen screen) { super(screen); }

    @Override
    public void draw(GuiGraphicsExtractor g, Font font,
                     int mx, int my, int ba, int x, int y, int w, int h) {
        // TODO
        g.text(font, Component.literal("Overview — coming soon"),
                x + 10, y + 10, 0xFF00CCFF, false);
    }
}