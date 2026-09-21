package zcylas.totality.client.tooltip;

import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.item.IdentificationStatus;
import zcylas.totality.api.item.TotalityItem;
import zcylas.totality.api.item.TotalityItemComponents;

/**
 * Neutral, future-facing visibility contract — deliberately narrow. It answers "is this section
 * visible right now" for the four {@link TooltipVisibility} levels, backed by the item's
 * already-existing {@link IdentificationStatus} where available.
 *
 * This is NOT the full Identification / Knowledge / Codex / Appraisal / Unlock-Access API. No
 * persistent knowledge state is created here, no shop/quest recognition, no Codex memory, no
 * heard-about-item tracking — this pass only reads identification state an item already
 * persists, and exposes it through a narrow visibility check so a future authoritative system
 * can slot in without the renderer or contributors changing.
 *
 * <p><b>Tooltip compatibility rule (deliberately narrower than {@code TotalityItem}'s own
 * default):</b> {@link TotalityItem#getIdentificationStatus(ItemStack)} defaults a
 * <em>missing</em> {@code IDENTIFICATION_STATUS} component to {@code UNIDENTIFIED} — a
 * reasonable gameplay default for a future Identification system, but not one this pass should
 * silently impose on every existing {@code TotalityWeaponItem}/{@code TotalityArmorItem} stack
 * today, since no Identification workflow exists yet to ever move them out of that state. {@link
 * #of(ItemStack)} therefore reads the raw component directly (not through {@code
 * getIdentificationStatus}) and treats an <em>absent</em> component as identified for tooltip
 * purposes only: a Netherite Shuriken or Ring of Protection with no explicitly-authored
 * identification state keeps showing its full tooltip exactly as before this contract existed.
 * An <em>explicitly present</em> component — {@code UNIDENTIFIED}, {@code PARTIALLY}, or {@code
 * IDENTIFIED} — is always honored exactly as authored. This does not change {@code
 * TotalityItem}'s own gameplay-facing default; other callers of {@code getIdentificationStatus}
 * (a future Identification API, gameplay checks, etc.) are unaffected. Nothing here writes the
 * component — reading a tooltip never creates persistent state. Once a real Identification API
 * exists and explicitly authors {@code UNIDENTIFIED}/{@code PARTIALLY} on a stack, this same
 * check honors that authored state immediately, with no further change needed here.
 */
public record TooltipKnowledgeView(IdentificationStatus identification) {

    public static final TooltipKnowledgeView IDENTIFIED =
            new TooltipKnowledgeView(IdentificationStatus.IDENTIFIED);

    public static TooltipKnowledgeView identified() {
        return IDENTIFIED;
    }

    public static TooltipKnowledgeView unidentified() {
        return new TooltipKnowledgeView(IdentificationStatus.UNIDENTIFIED);
    }

    public static TooltipKnowledgeView partiallyIdentified() {
        return new TooltipKnowledgeView(IdentificationStatus.PARTIALLY);
    }

    /**
     * Production item contexts default to fully identified until a real Identification/Knowledge
     * system exists and explicitly authors otherwise — see the tooltip compatibility rule above.
     * Items that aren't {@link TotalityItem} at all (batteries, Grimoires, the D&D healing
     * potion) have no identification concept and are always identified.
     */
    public static TooltipKnowledgeView of(ItemStack stack) {
        if (!(stack.getItem() instanceof TotalityItem)) {
            return IDENTIFIED;
        }
        IdentificationStatus explicit = stack.get(TotalityItemComponents.IDENTIFICATION_STATUS);
        return explicit != null ? new TooltipKnowledgeView(explicit) : IDENTIFIED;
    }

    public boolean isVisible(TooltipVisibility visibility, TooltipDisclosureLevel disclosure) {
        return switch (visibility) {
            case ALWAYS -> true;
            case WHEN_RECOGNIZED -> identification.isAtLeast(IdentificationStatus.PARTIALLY);
            case WHEN_IDENTIFIED -> identification.isAtLeast(IdentificationStatus.IDENTIFIED);
            case TECHNICAL -> disclosure == TooltipDisclosureLevel.TECHNICAL;
        };
    }
}
