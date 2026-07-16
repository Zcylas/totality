package zcylas.totality.screen.classes;

/**
 * Session flag indicating whether the class selection flow was triggered by
 * a multiclassing action (spend a class point on a NEW class) vs first-time
 * class selection.
 *
 * Set to true before opening {@link ClassSelectionScreen} via the Multiclass button.
 * {@link ConfirmClassScreen} reads this to decide which payload to send, then resets it.
 */
public final class ClassScreenMode {
    public static boolean IS_MULTICLASSING = false;
    private ClassScreenMode() {}
}