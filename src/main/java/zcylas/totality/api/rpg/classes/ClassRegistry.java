package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import java.util.*;

public final class ClassRegistry {
    private static final Map<Identifier, ClassData> CLASSES = new LinkedHashMap<>();

    public static void register(ClassData data)          { CLASSES.put(data.id(), data); }
    public static Optional<ClassData> get(Identifier id) { return Optional.ofNullable(CLASSES.get(id)); }
    public static List<ClassData> getAll()               { return List.copyOf(CLASSES.values()); }
    public static List<ClassData> getByCategory(ClassCategory category) {
        return CLASSES.values().stream()
                .filter(c -> c.category() == category)
                .toList();
    }
    private ClassRegistry() {}
}