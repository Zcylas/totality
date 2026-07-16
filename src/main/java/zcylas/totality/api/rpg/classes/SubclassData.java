package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import java.util.List;

public record SubclassData(
        Identifier id,
        Identifier parentClassId,
        String displayName,
        String description,
        // Covenant categories available for this subclass
        // Empty for non-Warlock/Cleric/Paladin subclasses
        List<Identifier> availableCovenantCategories,
        List<Identifier> startingAbilities
) {}