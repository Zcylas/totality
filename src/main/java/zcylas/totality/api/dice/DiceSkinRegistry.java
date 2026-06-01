// api/dice/DiceSkinRegistry.java
package zcylas.totality.api.dice;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for all dice skins.
 * Skins are registered here and referenced by Identifier on the player component.
 */
public final class DiceSkinRegistry {

    private static final Map<Identifier, DiceSkin> REGISTRY = new LinkedHashMap<>();

    private DiceSkinRegistry() {}

    public static DiceSkin register(DiceSkin skin) {
        REGISTRY.put(skin.id(), skin);
        return skin;
    }

    public static DiceSkin get(Identifier id) {
        return id != null ? REGISTRY.getOrDefault(id, DiceSkin.DEFAULT) : DiceSkin.DEFAULT;
    }

    public static Collection<DiceSkin> all() { return REGISTRY.values(); }

    // ── Built-in skins ────────────────────────────────────────────────────────

    /** Default — dark purple/gold. Available from the start. */
    public static final DiceSkin DEFAULT = register(DiceSkin.DEFAULT);

    /** Bone — pale ivory. Earned from skeleton-related achievements. */
    public static final DiceSkin BONE = register(new DiceSkin(
            Identifier.fromNamespaceAndPath("totality", "bone"),
            "Bone",
            0xFF3C3028, 0xFF504840,
            0xFFD4C090, 0xFF8A7050,
            0xFFFFFFDD));

    /** Arcane — deep blue with teal glow. Earned from magic-related achievements. */
    public static final DiceSkin ARCANE = register(new DiceSkin(
            Identifier.fromNamespaceAndPath("totality", "arcane"),
            "Arcane",
            0xFF0A0C28, 0xFF101840,
            0xFF2868B8, 0xFF183060,
            0xFF40B8FF));

    /** Crimson — dark red. Earned from combat/blood-related achievements. */
    public static final DiceSkin CRIMSON = register(new DiceSkin(
            Identifier.fromNamespaceAndPath("totality", "crimson"),
            "Crimson",
            0xFF1A0808, 0xFF300C0C,
            0xFF882020, 0xFF501010,
            0xFFFF4040));

    /** Solar — golden. Kryptonian-themed. Earned by Kryptonian players. */
    public static final DiceSkin SOLAR = register(new DiceSkin(
            Identifier.fromNamespaceAndPath("totality", "solar"),
            "Solar",
            0xFF1A1400, 0xFF2A2000,
            0xFFD4A020, 0xFF806010,
            0xFFFFD700));
}