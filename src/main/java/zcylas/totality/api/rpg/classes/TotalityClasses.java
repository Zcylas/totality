package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.classes.barbarian.BarbarianClass;
import zcylas.totality.api.rpg.classes.monk.MonkClass;
import zcylas.totality.api.rpg.classes.warlock.WarlockClass;
import zcylas.totality.api.rpg.classes.wizard.WizardClass;

public final class TotalityClasses {

    // ── Class IDs (referenced by abilities, handlers, etc.) ───────────────────
    public static final Identifier BARBARIAN_ID = id("barbarian");
    public static final Identifier WARLOCK_ID   = id("warlock");
    public static final Identifier MONK_ID      = id("monk");
    public static final Identifier WIZARD_ID    = id("wizard");

    public static void register() {
        BarbarianClass.register();
        WarlockClass.register();
        MonkClass.register();
        WizardClass.register();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, path);
    }

    private TotalityClasses() {}
}