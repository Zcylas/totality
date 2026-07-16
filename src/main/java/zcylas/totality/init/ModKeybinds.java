package zcylas.totality.init;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
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

    public static final KeyMapping OPEN_RADIAL = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.totality.open_radial",
                    GLFW.GLFW_KEY_UNKNOWN,
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

    public static final KeyMapping OPEN_ABILITY_RADIAL = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.totality.open_ability_radial",
                    GLFW.GLFW_KEY_UNKNOWN,
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

    public static void register() {}

    private ModKeybinds() {}
}