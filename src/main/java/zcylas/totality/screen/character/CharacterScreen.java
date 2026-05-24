package zcylas.totality.screen.character;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import zcylas.totality.screen.character.tabs.*;

public class CharacterScreen extends BaseCharacterScreen {

    // ── Tab enum ──────────────────────────────────────────────────────────────
    public enum CharacterTab {
        OVERVIEW, ATTRIBUTES, ABILITY_CHECKS, SKILLS, ABILITIES, CLASS, ANCESTRY, LINEAGES;

        public String label() {
            return switch (this) {
                case OVERVIEW       -> "Overview";
                case ATTRIBUTES     -> "Attributes";
                case ABILITY_CHECKS -> "Ability Checks";
                case SKILLS         -> "Skills";
                case ABILITIES      -> "Abilities";
                case CLASS          -> "Class";
                case ANCESTRY       -> "Ancestry";
                case LINEAGES       -> "Lineages";
            };
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private CharacterTab activeTab;

    // ── Tab instances ─────────────────────────────────────────────────────────
    private OverviewTab      overviewTab;
    private AttributesTab    attributesTab;
    private AbilityChecksTab abilityChecksTab;
    private SkillsTab        skillsTab;
    private AbilitiesTab     abilitiesTab;
    private ClassTab         classTab;
    private AncestryTab      ancestryTab;
    private LineagesTab      lineagesTab;

    public CharacterScreen() { this(CharacterTab.OVERVIEW); }

    public CharacterScreen(CharacterTab startTab) {
        super(Component.literal("Character"));
        this.activeTab = startTab;
    }

    @Override
    public void init() {
        super.init();
        overviewTab      = new OverviewTab(this);
        attributesTab    = new AttributesTab(this);
        abilityChecksTab = new AbilityChecksTab(this);
        skillsTab        = new SkillsTab(this);
        abilitiesTab     = new AbilitiesTab(this);
        classTab         = new ClassTab(this);
        ancestryTab      = new AncestryTab(this);
        lineagesTab      = new LineagesTab(this);
        activeTabInstance().onOpen();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        int ba = tickFade();
        if (fadingOut && alpha <= 0f) return;

        drawHeader(g, "CHARACTER");
        drawTabBar(g, CharacterTab.values(), activeTab, mx, my);
        drawLeftPanel(g, mx, my);
        activeTabInstance().draw(g, font, mx, my, ba, contentX, contentY, contentW, contentH);
        drawBottomHints(g);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            long window = Minecraft.getInstance().getWindow().handle();
            boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                    || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            cycleTab(shift ? -1 : 1);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            fadeOutTo(() -> Minecraft.getInstance().setScreen(null));
            return true;
        }
        if (activeTabInstance().keyPressed(key)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        // Tab bar click
        CharacterTab[] tabs = CharacterTab.values();
        int tabBarY = 18;
        int tabBarH = HEADER_H - tabBarY - 1;
        int tabW    = (W - PAD * 2) / tabs.length;
        if (inB(mx, my, PAD, tabBarY, W - PAD * 2, tabBarH)) {
            int idx = (mx - PAD) / tabW;
            if (idx >= 0 && idx < tabs.length) {
                switchTab(tabs[idx]);
                return true;
            }
        }

        activeTabInstance().mouseClicked(mx, my);
        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        activeTabInstance().mouseScrolled((int) mx, (int) my, sy);
        return true;
    }

    @Override
    public boolean keyPressed(int key) {
        if (activeTabInstance().keyPressed(key))
            return true;
        return super.keyPressed(key);
    }

    // ── Tab management ────────────────────────────────────────────────────────

    private void cycleTab(int dir) {
        CharacterTab[] tabs = CharacterTab.values();
        switchTab(tabs[(activeTab.ordinal() + dir + tabs.length) % tabs.length]);
    }

    private void switchTab(CharacterTab tab) {
        if (tab == activeTab) return;
        activeTab = tab;
        activeTabInstance().onOpen();
        click();
    }

    private CharacterScreenTab activeTabInstance() {
        return switch (activeTab) {
            case OVERVIEW       -> overviewTab;
            case ATTRIBUTES     -> attributesTab;
            case ABILITY_CHECKS -> abilityChecksTab;
            case SKILLS         -> skillsTab;
            case ABILITIES      -> abilitiesTab;
            case CLASS          -> classTab;
            case ANCESTRY       -> ancestryTab;
            case LINEAGES       -> lineagesTab;
        };
    }
}