package zcylas.totality.client.hologram;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Totality's hologram cues ({@code assets/totality/sounds.json}, {@code sounds/hologram/*.ogg}). Played
 * client-side only as UI sounds; they are not registry entries, so nothing reaches the server.
 */
public enum HologramSound {
    OPEN("hologram.open"),
    READY("hologram.ready"),
    WARNING("hologram.warning"),
    CLOSE("hologram.close"),
    SELECT("hologram.select");

    public final Identifier id;

    HologramSound(String path) {
        this.id = Identifier.fromNamespaceAndPath(Totality.MOD_ID, path);
    }
}
