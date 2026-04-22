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
                    GLFW.GLFW_KEY_V,
                    TOTALITY_CATEGORY
            )
    );

    public static void register() {}

    private ModKeybinds() {}
}
