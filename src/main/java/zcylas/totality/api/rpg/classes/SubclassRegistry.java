package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import java.util.*;

public final class SubclassRegistry {
    private static final Map<Identifier, SubclassData> SUBCLASSES = new LinkedHashMap<>();

    public static void register(SubclassData data)          { SUBCLASSES.put(data.id(), data); }
    public static Optional<SubclassData> get(Identifier id) { return Optional.ofNullable(SUBCLASSES.get(id)); }

    public static List<SubclassData> getByClass(Identifier classId) {
        return SUBCLASSES.values().stream()
                .filter(s -> s.parentClassId().equals(classId))
                .toList();
    }

    private SubclassRegistry() {}
}