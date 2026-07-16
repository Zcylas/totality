package zcylas.totality.api.rpg.classes.feature;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public final class ClassFeatureRegistry {

    // classId → (classLevel → features)
    private static final Map<Identifier, Map<Integer, List<ClassFeature>>> FEATURES
            = new LinkedHashMap<>();

    /** Register a feature granted at a specific class level. */
    public static void register(Identifier classId, int requiredLevel, ClassFeature feature) {
        FEATURES.computeIfAbsent(classId, k -> new LinkedHashMap<>())
                .computeIfAbsent(requiredLevel, k -> new ArrayList<>())
                .add(feature);
    }

    /** Features unlocked at exactly this class level. */
    public static List<ClassFeature> getFeaturesAt(Identifier classId, int classLevel) {
        return FEATURES.getOrDefault(classId, Map.of())
                .getOrDefault(classLevel, List.of());
    }

    /** All features for a class up to and including classLevel. */
    public static List<ClassFeature> getAllFeaturesUpTo(Identifier classId, int classLevel) {
        var levelMap = FEATURES.getOrDefault(classId, Map.of());
        return levelMap.entrySet().stream()
                .filter(e -> e.getKey() <= classLevel)
                .flatMap(e -> e.getValue().stream())
                .toList();
    }

    /** Returns all registered levels for a class — for Class tab display. */
    public static Map<Integer, List<ClassFeature>> getAllLevels(Identifier classId) {
        return Collections.unmodifiableMap(
                FEATURES.getOrDefault(classId, Map.of()));
    }

    /**
     * Called from ClassLevelUpRegistry when a player gains a class level.
     * Fires onGain for features unlocked at exactly newClassLevel.
     */
    public static void onClassLevelUp(ServerPlayer player,
                                      Identifier classId, int newClassLevel) {
        for (ClassFeature feature : getFeaturesAt(classId, newClassLevel)) {
            feature.onGain(player);
        }
    }

    /**
     * Called on player join to restore all features up to their current class level.
     * Safe to call multiple times — features should use putIfAbsent / check first.
     */
    public static void onPlayerJoin(ServerPlayer player,
                                    Identifier classId, int classLevel) {
        for (ClassFeature feature : getAllFeaturesUpTo(classId, classLevel)) {
            feature.onGain(player);
        }
    }

    private ClassFeatureRegistry() {}
}