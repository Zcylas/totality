package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Core operation identifiers (canonical §2.6). Queries are operation-specific; future systems
 * register additional namespaced actions through {@link EntitlementCatalog#registerAction}.
 */
public final class EntitlementActions {

    public static final Identifier VIEW = id("view");
    public static final Identifier LEARN = id("learn");
    public static final Identifier UNLOCK = id("unlock");
    public static final Identifier SELECT = id("select");
    public static final Identifier PREPARE = id("prepare");
    public static final Identifier EQUIP = id("equip");
    public static final Identifier INSTALL = id("install");
    public static final Identifier PURCHASE = id("purchase");
    public static final Identifier ACTIVATE = id("activate");
    public static final Identifier USE = id("use");
    public static final Identifier ENTER = id("enter");
    public static final Identifier OPEN_SERVICE = id("open_service");
    public static final Identifier START_QUEST = id("start_quest");

    public static final List<Identifier> CORE = List.of(
            VIEW, LEARN, UNLOCK, SELECT, PREPARE, EQUIP, INSTALL, PURCHASE, ACTIVATE, USE, ENTER,
            OPEN_SERVICE, START_QUEST);

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private EntitlementActions() {}
}
