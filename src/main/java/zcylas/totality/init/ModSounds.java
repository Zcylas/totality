package zcylas.totality.init;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import zcylas.totality.Totality;

public class ModSounds {

    public static final SoundEvent SHURIKEN_THROW = register("item.shuriken.throw");

    /** Eldritch Blast V2: original Totality sounds (Context/Tools/eldritch-blast-v2/make_eldritch_sounds.py). */
    public static final SoundEvent ELDRITCH_BLAST_CAST = register("spell.eldritch_blast.cast");
    public static final SoundEvent ELDRITCH_BLAST_IMPACT = register("spell.eldritch_blast.impact");
    /**
     * Eldritch Blast V2 comparison sounds extracted from a third-party gameplay clip: PRIVATE REVIEW MATERIAL ONLY, not
     * cleared for distribution. Defined only by the git-ignored built-in pack resourcepacks/eldritch_reference_private
     * (see EldritchBlastVfx); a clean checkout has no definitions or files for them and they are never played by default.
     * Not registered: the client plays them by id, and a registered event without a definition would log a warning.
     */
    public static final SoundEvent ELDRITCH_BLAST_CAST_REFERENCE = unregistered("spell.eldritch_blast.cast_reference");
    public static final SoundEvent ELDRITCH_BLAST_IMPACT_REFERENCE = unregistered("spell.eldritch_blast.impact_reference");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(Totality.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    private static SoundEvent unregistered(String name) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Totality.MOD_ID, name));
    }

    public static void register() {}

}
