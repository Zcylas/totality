package zcylas.totality.client.tooltip;

/**
 * Narrow, future-facing visibility contract a {@code TooltipSection} can declare. Backed today
 * by {@link TooltipKnowledgeView}; not a full Identification/Knowledge/Codex/Appraisal system —
 * see that class's javadoc for what is and is not implemented in this pass.
 */
public enum TooltipVisibility {
    /** Always shown, regardless of identification or disclosure. */
    ALWAYS,
    /** Shown once the item is at least recognized (basic facts). */
    WHEN_RECOGNIZED,
    /** Shown only once the item is fully identified (magical identity, secrets). */
    WHEN_IDENTIFIED,
    /** Shown only at {@link TooltipDisclosureLevel#TECHNICAL}, independent of identification. */
    TECHNICAL
}
