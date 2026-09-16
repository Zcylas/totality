package zcylas.totality.networking.classes;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.classes.ClassChangeReconciler;
import zcylas.totality.api.rpg.classes.ClassData;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.ClassRegistry;
import zcylas.totality.api.rpg.classes.PlayerClassComponent;
import zcylas.totality.api.rpg.classes.SubclassData;
import zcylas.totality.api.rpg.classes.SubclassRegistry;

/**
 * Server-authoritative handler for {@link SelectSubclassPayload} — applies a subclass choice to a
 * class the player already owns.
 *
 * <p>This is the fix for a pre-existing production bug discovered while auditing the class-level-up
 * flow for the Class Tab Quick Level-Up feature (see {@code
 * TOTALITY_CLASS_TAB_QUICK_LEVEL_UP_IMPLEMENTATION_2026-09-16.md}): before this class existed, the
 * only path that ever called {@code PlayerClassComponent.selectSubclass} was {@link
 * SelectClassHandler}, gated to a player's very first-ever class selection ({@code
 * comp.hasAnyClass()} rejects everything after that). Reaching a subclass milestone (Wizard/
 * Barbarian/Monk class level 3) via ordinary leveling opened {@code SubclassSelectionScreen}, let
 * the player pick a subclass, then silently discarded that choice — {@code ConfirmClassScreen} sent
 * a {@link SelectClassPayload}, which {@code SelectClassHandler} rejected outright since the player
 * already owned a class. This handler is the missing "apply subclass to an already-owned class"
 * counterpart, reusing the exact same {@code PlayerClassComponent#selectSubclass} the first-time
 * path already uses — no new subclass state, no duplicated validation.
 *
 * <p>Per-class subclass migration (2026-09-16): the rejection check below is scoped to the
 * TARGET class only ({@code comp.hasSubclass(classId)}), not a global "does this player have any
 * subclass at all" check — a Barbarian subclass must never block a Wizard subclass choice on the
 * same character, and vice versa. See {@code PlayerClassComponent}'s own field Javadoc for the full
 * per-class storage migration.
 */
public final class SelectSubclassHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                SelectSubclassPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        handle(context.player(), payload)));
    }

    private static void handle(ServerPlayer player, SelectSubclassPayload payload) {
        Identifier classId;
        Identifier subclassId;
        try {
            classId = Identifier.parse(payload.classId());
            subclassId = Identifier.parse(payload.subclassId());
        } catch (Exception e) {
            Totality.LOGGER.warn("SelectSubclassHandler: malformed identifier in payload from {}",
                    player.getName().getString());
            return;
        }

        PlayerClassComponent comp = ClassComponents.get(player);
        if (apply(comp, classId, subclassId)) {
            comp.sync();
            ClassChangeReconciler.reconcile(player);
            Totality.LOGGER.info("Subclass selected: {} for {} ({})",
                    subclassId, classId, player.getName().getString());
        } else {
            Totality.LOGGER.warn("SelectSubclassHandler: rejected {} choosing subclass {} for class {}",
                    player.getName().getString(), subclassId, classId);
        }
    }

    /**
     * Pure validation + apply core, package-visible so it can be exercised directly against a
     * {@code new PlayerClassComponent(null)} without a real {@code ServerPlayer} — mirrors this
     * codebase's established {@code PlayerResourceService}/{@code ResourceGrantReconciler}
     * precedent of separating pure logic from the impure packet-registration glue. Returns whether
     * the subclass was actually applied; {@code false} for every validation failure, with no
     * partial/side-effecting state change on rejection. Validates, at minimum, everything the
     * first-time {@link SelectClassHandler} path relies on being true by construction:
     * <ul>
     *   <li>the class exists</li>
     *   <li>the subclass exists and actually belongs to that class</li>
     *   <li>the player owns the target class</li>
     *   <li>the player's stored level for that class has reached its subclass-unlock milestone</li>
     *   <li>the TARGET class does not already have a subclass — the current design treats a
     *       subclass choice as permanent (see {@code ConfirmClassScreen}'s own "This choice is
     *       permanent" warning); no replacement path exists anywhere in the codebase, so this
     *       handler must not invent one. A subclass already chosen for a DIFFERENT class never
     *       blocks this — {@code hasSubclass(classId)} is checked per-class, not globally</li>
     * </ul>
     */
    static boolean apply(PlayerClassComponent comp, Identifier classId, Identifier subclassId) {
        ClassData classData = ClassRegistry.get(classId).orElse(null);
        if (classData == null) return false;

        SubclassData subclassData = SubclassRegistry.get(subclassId).orElse(null);
        if (subclassData == null || !subclassData.parentClassId().equals(classId)) return false;

        if (!comp.hasClass(classId)) return false;
        if (comp.getClassLevel(classId) < classData.subclassUnlockClassLevel()) return false;
        if (comp.hasSubclass(classId)) return false;

        comp.selectSubclass(classId, subclassId);
        return true;
    }

    private SelectSubclassHandler() {}
}
