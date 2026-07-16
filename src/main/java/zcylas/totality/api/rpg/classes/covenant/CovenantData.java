package zcylas.totality.api.rpg.classes.covenant;

import net.minecraft.resources.Identifier;
import java.util.List;

public record CovenantData(
        Identifier id,
        Identifier categoryId,
        String displayName,
        String description,
        List<Identifier> grantedAbilities
) {}