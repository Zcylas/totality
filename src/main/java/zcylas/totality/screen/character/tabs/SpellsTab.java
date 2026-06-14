package zcylas.totality.screen.character.tabs;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellRegistry;
import zcylas.totality.networking.ability.ClientAbilityManager;
import zcylas.totality.networking.ability.EquipAbilityPayload;
import zcylas.totality.networking.ability.FavoriteAbilityPayload;
import zcylas.totality.screen.character.CharacterScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Comparator;
import java.util.List;

/**
 * Character screen tab showing all spells the player has access to.
 * Favorites are spell favorites ({@link ClientAbilityManager#getSpellFavorites()}).
 * The spell radial (hold X) uses the same spell favorites list.
 */
public class SpellsTab extends CharacterScreenTab {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int PAD           = 6;
    private static final int CARD_H        = 36;
    private static final int CARD_GAP      = 3;
    private static final int HDR_H         = 20;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int COL_SECTION   = 0xFF9966CC;  // purple for spells
    private static final int COL_SELECTED  = 0xFF2A1040;
    private static final int COL_CARD      = 0xFF1A0830;
    private static final int COL_FAV_STAR  = 0xFFFFD700;
    private static final int COL_FAV_EMPTY = 0xFF444444;
    private static final int COL_LEVEL     = 0xFFCCA0FF;
    private static final int COL_SCHOOL    = 0xFF88BBFF;
    private static final int COL_NAME      = 0xFFFFFFFF;
    private static final int COL_DESC      = 0xFFAAAAAA;

    public static final int MAX_SPELL_FAVORITES = 12;

    private Spell   selectedSpell = null;
    private int     scrollY       = 0;

    public SpellsTab(CharacterScreen screen) { super(screen); }

    @Override public void onOpen() { scrollY = 0; selectedSpell = null; }

    @Override
    public void draw(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font,
                     int mx, int my, int ba, int x, int y, int w, int h) {

        List<Spell> spells = getSpells();
        int splitX = x + w / 2;
        int listW  = splitX - x - PAD;
        int detailW = w - listW - PAD * 3;

        // ── Spell list (left half) ────────────────────────────────────────────
        int cardY = y + HDR_H - scrollY;
        g.text(font, Component.literal("Spells"),
                x + PAD, y + 4, COL_SECTION, false);

        for (Spell spell : spells) {
            if (cardY + CARD_H < y) { cardY += CARD_H + CARD_GAP; continue; }
            if (cardY > y + h) break;

            boolean sel = spell == selectedSpell;
            g.fill(RenderPipelines.GUI,
                    x + PAD, cardY, x + PAD + listW, cardY + CARD_H,
                    sel ? COL_SELECTED : COL_CARD);

            // Icon
            if (spell.getIcon() != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, spell.getIcon(),
                        x + PAD + 2, cardY + (CARD_H - 16) / 2, 0f, 0f, 16, 16, 16, 16);
            }

            // Name + level + school
            g.text(font, Component.literal(spell.getDisplayName()),
                    x + PAD + 22, cardY + 4, COL_NAME, false);
            String sub = spell.getLevelDisplay() + "  ·  " + spell.getSchool().name();
            g.pose().pushMatrix();
            g.pose().scale(0.8f, 0.8f);
            g.text(font, Component.literal(sub),
                    Math.round((x + PAD + 22) / 0.8f),
                    Math.round((cardY + 16) / 0.8f),
                    COL_LEVEL, false);
            g.pose().popMatrix();

            // Favorite star
            boolean fav = ClientAbilityManager.isFavorite(spell.getId());
            g.text(font, Component.literal(fav ? "★" : "☆"),
                    x + PAD + listW - 12, cardY + (CARD_H - font.lineHeight) / 2,
                    fav ? COL_FAV_STAR : COL_FAV_EMPTY, false);

            cardY += CARD_H + CARD_GAP;
        }

        // ── Detail panel (right half) ─────────────────────────────────────────
        if (selectedSpell != null) {
            int dx = splitX + PAD;
            g.text(font, Component.literal(selectedSpell.getDisplayName()),
                    dx, y + 4, COL_NAME, false);
            g.text(font, Component.literal(selectedSpell.getLevelDisplay()
                            + " " + selectedSpell.getSchool().name()),
                    dx, y + 16, COL_SCHOOL, false);

            // Description wrapped
            int descY = y + 32;
            var descLines = font.split(Component.literal(selectedSpell.getDescription()), detailW - PAD);
            for (var line : descLines) {
                g.text(font, line, dx, descY, COL_DESC, false);
                descY += font.lineHeight + 2;
            }

            // Favorites strip
            List<Identifier> favIds = ClientAbilityManager.getSpellFavorites();
            int stripY = y + h - 22;
            g.text(font, Component.literal("Spell Favorites  [F to favourite]:"),
                    dx, stripY - font.lineHeight - 2, 0xFF888888, false);
            for (int i = 0; i < Math.min(favIds.size(), MAX_SPELL_FAVORITES); i++) {
                var favSpell = SpellRegistry.get(favIds.get(i));
                int fx = dx + i * 20;
                if (favSpell != null && favSpell.getIcon() != null) {
                    g.blit(RenderPipelines.GUI_TEXTURED, favSpell.getIcon(),
                            fx, stripY, 0f, 0f, 16, 16, 16, 16);
                }
            }
        }
    }

    @Override
    public void mouseClicked(int mx, int my) {
        // Simple click detection: find which card was clicked
        int x = screen.contentX, y = screen.contentY;
        int w = screen.contentW, listW = w / 2 - PAD;
        int cardY = y + HDR_H - scrollY;
        for (Spell spell : getSpells()) {
            if (mx >= x + PAD && mx <= x + PAD + listW
                    && my >= cardY && my <= cardY + CARD_H) {

                if (spell == selectedSpell) {
                    // Second click on star → toggle favorite
                    int starX = x + PAD + listW - 12;
                    if (mx >= starX) {
                        toggleFavorite(spell);
                        return;
                    }
                    // Second click on card → equip
                    ClientPlayNetworking.send(new EquipAbilityPayload(spell.getId()));
                } else {
                    selectedSpell = spell;
                }
                return;
            }
            cardY += CARD_H + CARD_GAP;
        }
    }

    @Override
    public void mouseScrolled(int mx, int my, double delta) {
        scrollY = Math.max(0, scrollY - (int)(delta * 12));
    }

    @Override
    public boolean keyPressed(int key) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_F && selectedSpell != null) {
            toggleFavorite(selectedSpell);
            return true;
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Spell> getSpells() {
        return SpellRegistry.all().stream()
                .filter(s -> s.isDefault() || ClientAbilityManager.hasAbility(s.getId()))
                .sorted(Comparator
                        .comparingInt(Spell::getSpellLevel)
                        .thenComparing(s -> s.getSchool().name())
                        .thenComparing(Spell::getDisplayName))
                .toList();
    }

    private void toggleFavorite(Spell spell) {
        boolean alreadyFav = ClientAbilityManager.isFavorite(spell.getId());
        if (!alreadyFav && ClientAbilityManager.getSpellFavorites().size() >= MAX_SPELL_FAVORITES)
            return;
        ClientPlayNetworking.send(new FavoriteAbilityPayload(spell.getId()));
    }
}