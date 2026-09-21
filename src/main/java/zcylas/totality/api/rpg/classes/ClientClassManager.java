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
    /** Per-class subclass mirror: {@code classId -> subclassId}. See {@code
     *  PlayerClassComponent}'s own field Javadoc for the full migration rationale (2026-09-16) —
     *  a single global subclass slot could not represent two different owned classes each having
     *  their own subclass. */
    private static final Map<Identifier, Identifier> subclassIds = new LinkedHashMap<>();
    private static @Nullable Identifier covenantId = null;

    public static void apply(Map<Identifier, Integer> levels,
                             Map<Identifier, Identifier> subclasses,
                             @Nullable Identifier covenant) {
        classLevels.clear();
        classLevels.putAll(levels);
        subclassIds.clear();
        subclassIds.putAll(subclasses);
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

    /** The subclass chosen for {@code classId}, or {@code null} — never any other class's. */
    public static @Nullable Identifier getSubclassId(Identifier classId) { return subclassIds.get(classId); }
    public static boolean hasSubclass(Identifier classId) { return subclassIds.containsKey(classId); }
    public static @Nullable Identifier getCovenantId() { return covenantId; }
    public static boolean hasClass()                   { return !classLevels.isEmpty(); }

    public static @Nullable ClassData getPrimaryClassData() {
        Identifier id = getPrimaryClassId();
        return id != null ? ClassRegistry.get(id).orElse(null) : null;
    }

    /** The subclass data chosen for {@code classId}, or {@code null} — never any other class's. */
    public static @Nullable SubclassData getSubclassData(Identifier classId) {
        Identifier id = subclassIds.get(classId);
        return id != null ? SubclassRegistry.get(id).orElse(null) : null;
    }

    public static @Nullable CovenantData getCovenantData() {
        return covenantId != null ? CovenantRegistry.get(covenantId).orElse(null) : null;
    }

    private ClientClassManager() {}
}