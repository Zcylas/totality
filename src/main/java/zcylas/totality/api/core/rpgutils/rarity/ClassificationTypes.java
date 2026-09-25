package zcylas.totality.api.core.rpgutils.rarity;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registry of known specific classification types — the TYPE half of a {@link Classification} pair
 * ({@code TOOL • AXE}). Types are namespaced {@link Identifier}s rather than an enum, so Totality and
 * future content integrations can each register the handful they actually use, without one central
 * list of every conceivable type. Broad categories stay the existing {@link ItemType} enum.
 *
 * <p>Registration is the validation point: {@link Classification#of(ItemType, Identifier)} (authoring)
 * rejects unregistered types. Decoding is deliberately lenient — a persisted stack whose type is no
 * longer registered (e.g. its integration was removed) keeps its data and still displays, via the
 * localization fallback.
 *
 * <p>Display names are localized under {@link #translationKey(Identifier)},
 * {@code classification_type.<namespace>.<path>}.
 */
public final class ClassificationTypes {

    private static final Set<Identifier> REGISTERED = Collections.synchronizedSet(new LinkedHashSet<>());

    public static final Identifier AXE = register(totality("axe"));
    public static final Identifier PICKAXE = register(totality("pickaxe"));
    public static final Identifier TWO_HANDED = register(totality("two_handed"));
    public static final Identifier BATTERY = register(totality("battery"));

    // Ordinary equipment/material types used by ItemClassificationResolver's vanilla mapping and inference.
    public static final Identifier SWORD = register(totality("sword"));
    public static final Identifier SPEAR = register(totality("spear"));
    public static final Identifier SHOVEL = register(totality("shovel"));
    public static final Identifier HOE = register(totality("hoe"));
    public static final Identifier BOW = register(totality("bow"));
    public static final Identifier CROSSBOW = register(totality("crossbow"));
    public static final Identifier TRIDENT = register(totality("trident"));
    public static final Identifier MACE = register(totality("mace"));
    public static final Identifier HELMET = register(totality("helmet"));
    public static final Identifier CHESTPLATE = register(totality("chestplate"));
    public static final Identifier LEGGINGS = register(totality("leggings"));
    public static final Identifier BOOTS = register(totality("boots"));
    public static final Identifier GEM = register(totality("gem"));
    public static final Identifier INGOT = register(totality("ingot"));

    /** Registers a specific classification type; returns it for use as a constant. Duplicates are a programming error. */
    public static Identifier register(Identifier type) {
        if (!REGISTERED.add(type)) {
            throw new IllegalStateException("Classification type already registered: " + type);
        }
        return type;
    }

    public static boolean isRegistered(Identifier type) {
        return REGISTERED.contains(type);
    }

    /** Snapshot of every registered type, in registration order. */
    public static Set<Identifier> registered() {
        synchronized (REGISTERED) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(REGISTERED));
        }
    }

    /** Localization key for a type's display name, e.g. {@code classification_type.totality.two_handed}. */
    public static String translationKey(Identifier type) {
        return "classification_type." + type.getNamespace() + "." + type.getPath();
    }

    /** Display fallback when no translation exists: the path with separators as hyphens, e.g. {@code two-handed}. */
    public static String fallbackName(Identifier type) {
        return type.getPath().replace('_', '-');
    }

    private static Identifier totality(String path) {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, path);
    }

    private ClassificationTypes() {}
}
