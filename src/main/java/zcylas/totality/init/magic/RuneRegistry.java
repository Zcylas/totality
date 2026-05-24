package zcylas.totality.init.magic;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.grimoire.rune.AbstractRune;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for all runes.
 * Runes register themselves here on mod init.
 */
public class RuneRegistry {

    private static final Map<Identifier, AbstractRune> MAP = new HashMap<>();

    public static void register(AbstractRune rune) {
        MAP.put(rune.getId(), rune);
    }

    public static AbstractRune get(Identifier id) {
        return MAP.get(id);
    }

    public static Collection<AbstractRune> getAll() {
        return MAP.values();
    }

    private RuneRegistry() {}
}