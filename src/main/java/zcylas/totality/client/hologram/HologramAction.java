package zcylas.totality.client.hologram;

import net.minecraft.network.chat.Component;

/**
 * One button on a hologram. Activating it closes the hologram and reports the id to the spec's
 * {@link HologramSpec.ActionHandler}. A hologram only presents choices: the owning system decides
 * what an action means and performs any real change through its own authority.
 *
 * @param primary drawn filled; the action the Confirm/Dismiss key triggers when no button is aimed at
 */
public record HologramAction(String id, Component label, boolean primary) {

    /** Id of the plain "close this" action. */
    public static final String DISMISS = "dismiss";

    public static HologramAction dismiss() {
        return new HologramAction(DISMISS, Component.literal("Dismiss"), true);
    }
}
