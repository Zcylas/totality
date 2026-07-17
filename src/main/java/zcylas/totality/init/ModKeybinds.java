package zcylas.totality.init;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class ModKeybinds {

    public static final KeyMapping.Category TOTALITY_CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath("totality", "totality"));

    public static final KeyMapping OPEN_GRIMOIRE = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.totality.open_grimoire",
                    GLFW.GLFW_KEY_C,
                    TOTALITY_CATEGORY
            )
    );

    public static final KeyMapping BLOCK = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.totality.block",
                    GLFW.GLFW_KEY_V,
                    TOTALITY_CATEGORY
            )
    );
    /**
     * Opens the Phone app grid (or setup screen, if not yet set up) for the equipped Phone.
     * No-op if no Phone is equipped.
     * TODO: Once fully wired, remove /totality stats command.
     */
    public static final KeyMapping OPEN_MENU = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.totality.open_menu",
                    GLFW.GLFW_KEY_TAB,
                    TOTALITY_CATEGORY
            )
    );

    public static final KeyMapping USE_ABILITY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.totality.use_ability",
                    GLFW.GLFW_KEY_Z,
                    TOTALITY_CATEGORY
            )
    );

    public static final KeyMapping USE_SPELL = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.totality.use_spell",
                    GLFW.GLFW_KEY_X,
                    TOTALITY_CATEGORY
            )
    );

    /**
     * Held together with {@link #USE_ABILITY}/{@link #USE_SPELL} to open the respective Favorites
     * radial instead of activating/casting (Phase 4 correction pass, Part D) — replaces the old
     * hold-Z/hold-X-for-one-second radial trigger, which interrupted channeled abilities/spells
     * once the threshold passed. Rebindable specifically because the default (Left Alt) can
     * conflict with external overlays (Discord, Nvidia/AMD, Steam) on some systems.
     */
    public static final KeyMapping RADIAL_MODIFIER = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.totality.radial_modifier",
                    GLFW.GLFW_KEY_LEFT_ALT,
                    TOTALITY_CATEGORY
            )
    );
    public static final KeyMapping MOVEMENT_POWER = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.totality.movement_power",
                    GLFW.GLFW_KEY_GRAVE_ACCENT,
                    TOTALITY_CATEGORY
            )
    );

    /**
     * Hold over an item in your inventory to begin attuning to it.
     * Attunement takes 10 seconds. Releasing the key cancels the process.
     */
    public static final KeyMapping ATTUNE_ITEM = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.totality.attune_item",
                    GLFW.GLFW_KEY_R,
                    TOTALITY_CATEGORY
            )
    );

    /**
     * True if the CURRENT physical input bound to {@code mapping} is being held right now — reads
     * real hardware/GLFW state directly, NEVER {@link KeyMapping#isDown()} (radial correction pass
     * follow-up).
     *
     * <p>ROOT CAUSE this exists to work around: {@code Gui.setScreen(Screen)} unconditionally
     * calls {@code KeyMapping.releaseAll()} immediately before {@code Screen.init()} for EVERY
     * non-null screen it opens (confirmed by decompiled-bytecode inspection) — including a radial
     * screen opened from the very tick its chord key was pressed. That zeroes {@code isDown()} for
     * EVERY registered key mapping at that instant, regardless of whether the physical key is
     * still held, and nothing re-asserts it while the key stays continuously down (a new GLFW
     * press EVENT is required, which a still-held key does not generate). Any code that needs to
     * know "is this chord key still physically held" on a tick AFTER a screen may have opened —
     * the radial screens' own release checks, and {@code TotalityKeybindHandlers}' own chord/
     * channeled-ability tracking, which keeps running every tick regardless of what screen is
     * open — must never rely on {@code isDown()} for that; this method is the shared replacement.
     *
     * <p>Resolves the mapping's CURRENT (rebind-aware) bound key via {@link KeyMapping#saveString()}
     * round-tripped through {@link InputConstants#getKey(String)} — the exact same string
     * round-trip vanilla's own options serialization already uses — so a rebind is honored
     * immediately, without any cached "which key opened this" state to go stale.
     */
    public static boolean isPhysicallyDown(KeyMapping mapping) {
        Window window = Minecraft.getInstance().getWindow();
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        return switch (key.getType()) {
            case KEYSYM, SCANCODE -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
        };
    }

    public static void register() {}

    private ModKeybinds() {}
}