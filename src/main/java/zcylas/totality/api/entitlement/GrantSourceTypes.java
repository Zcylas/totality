package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;

/**
 * Grant Source type identifiers (canonical §4.1) — where an entitlement grant comes from
 * (a Class, an Origin, a quest, debug tooling...). Not to be confused with a Content Source (the
 * universe or material that inspired content, e.g. D&amp;D or Skyrim), which Entitlement does not model.
 *
 * <p>Only the source types that existing Totality systems actually produce today are registered by
 * {@code TotalityEntitlements}; future systems register their own through
 * {@link EntitlementCatalog#registerSourceType}.
 */
public final class GrantSourceTypes {

    public static final Identifier BASELINE = id("baseline");
    public static final Identifier CLASS = id("class");
    public static final Identifier SUBCLASS = id("subclass");
    public static final Identifier COVENANT = id("covenant");
    public static final Identifier SPECIES = id("species");
    public static final Identifier ORIGIN = id("origin");
    public static final Identifier MASTERY = id("mastery");
    public static final Identifier QUEST = id("quest");
    public static final Identifier ADMIN = id("admin");
    public static final Identifier DEBUG = id("debug");
    public static final Identifier LEGACY_MIGRATION = id("legacy_migration");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private GrantSourceTypes() {}
}
