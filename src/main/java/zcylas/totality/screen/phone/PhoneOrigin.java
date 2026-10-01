package zcylas.totality.screen.phone;

import net.minecraft.client.Minecraft;
import zcylas.totality.client.camera.CameraSession;

/**
 * Where a Phone app was opened from, so it returns there: a home-screen page, or the Camera (whose session waits,
 * suspended, behind a Gallery opened from its shortcut). ESC = {@link #back()}; TAB = {@link #closeAll()}.
 *
 * @param page the home page index to return to
 */
public record PhoneOrigin(PhoneFrame frame, int page, boolean fromCamera) {

    public static PhoneOrigin home(PhoneFrame frame, int page) {
        return new PhoneOrigin(frame, page, false);
    }

    public static PhoneOrigin camera(PhoneFrame frame, int page) {
        return new PhoneOrigin(frame, page, true);
    }

    /** Back to the Camera viewfinder, or to the home page the app was opened from. */
    public void back() {
        if (fromCamera) CameraSession.resumeFromGallery();
        else Minecraft.getInstance().gui.setScreen(new PhoneAppGridScreen(frame, page));
    }

    /** The whole Phone session closes (a suspended Camera with it). */
    public void closeAll() {
        if (fromCamera) CameraSession.endSuspended();
        Minecraft.getInstance().gui.setScreen(null);
    }
}
