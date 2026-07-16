package zcylas.totality.api.rpg.classes.covenant;

import net.minecraft.resources.Identifier;

public record CovenantCategory(
        Identifier id,
        String displayName,
        String description
) {}