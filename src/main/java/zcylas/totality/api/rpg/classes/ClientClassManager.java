package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.rpg.classes.covenant.CovenantData;
import zcylas.totality.api.rpg.classes.covenant.CovenantRegistry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientClassManager {

    private static final Map<Identifier, Integer> classLevels = new LinkedHashMap<>();
    private static @Nullable Identifier subclassId = null;
    private static @Nullable Identifier covenantId = null;

    public static void apply(Map<Identifier, Integer> levels,
                             @Nullable Identifier subclass,
                             @Nullable Identifier covenant) {
        classLevels.clear();
        classLevels.putAll(levels);
        subclassId = subclass;
        covenantId = covenant;
    }

    public static Map<Identifier, Integer> getClassLevels() {
        return Collections.unmodifiableMap(classLevels);
    }

    public static @Nullable Identifier getPrimaryClassId() {
        return classLevels.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public static @Nullable Identifier getSubclassId() { return subclassId; }
    public static @Nullable Identifier getCovenantId() { return covenantId; }
    public static boolean hasClass()                   { return !classLevels.isEmpty(); }

    public static @Nullable ClassData getPrimaryClassData() {
        Identifier id = getPrimaryClassId();
        return id != null ? ClassRegistry.get(id).orElse(null) : null;
    }

    public static @Nullable SubclassData getSubclassData() {
        return subclassId != null ? SubclassRegistry.get(subclassId).orElse(null) : null;
    }

    public static @Nullable CovenantData getCovenantData() {
        return covenantId != null ? CovenantRegistry.get(covenantId).orElse(null) : null;
    }

    private ClientClassManager() {}
}