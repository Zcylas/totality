package zcylas.totality.api.core.rpgutils.rarity;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Known content origins ({@link ContentOriginComponent}). Integrations may use their own namespaced ids; each
 * needs a {@code content_origin.<namespace>.<path>} translation.
 */
public final class ContentOrigins {

    public static final Identifier TOTALITY = id("totality");
    public static final Identifier BLEACH = id("bleach");
    public static final Identifier SKYRIM = id("skyrim");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, path);
    }

    private ContentOrigins() {}
}
