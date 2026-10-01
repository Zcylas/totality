package zcylas.totality.client.photo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-level guarantees for Camera & Gallery V1: the wiring the real-client captures exercise, plus properties that
 * are easiest to state about the code itself (no networking, local storage only, Scan reserved, development capture
 * gated, home screen preserved).
 */
class CameraGalleryWiringTest {

    private static final Path MAIN = Path.of("src/main/java/zcylas/totality");

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    @Test
    void cameraMixinsAreRegisteredAsClientMixins() throws IOException {
        String json = Files.readString(Path.of("src/main/resources/totality.mixins.json"));
        int client = json.indexOf("\"client\"");
        for (String m : new String[] {"CameraCaptureMixin", "CameraFovMixin", "CameraHudMixin", "CameraKeyboardMixin", "CameraMouseMixin"}) {
            int at = json.indexOf("\"client.camera." + m + "\"");
            assertTrue(at > client, m + " is a client mixin");
        }
        assertTrue(read("TotalityClient.java").contains("CameraClient.register();"));
    }

    @Test
    void photographsNeverTouchTheNetworkOrTheServer() throws IOException {
        for (String dir : new String[] {"client/photo", "client/camera"}) {
            try (Stream<Path> files = Files.list(MAIN.resolve(dir))) {
                for (Path f : files.toList()) {
                    String src = Files.readString(f);
                    for (String forbidden : new String[] {"ClientPlayNetworking", "Payload", "ServerPlayer", "sendPacket"}) {
                        assertFalse(src.contains(forbidden), f.getFileName() + " must not use " + forbidden);
                    }
                }
            }
        }
    }

    @Test
    void galleriesLiveInThePersistentGameDirectory() throws IOException {
        String gallery = read("client/photo/Gallery.java");
        assertTrue(gallery.contains("mc.gameDirectory.toPath().resolve(\"totality\").resolve(\"gallery\").resolve(\"v1\")"));
        assertTrue(gallery.contains("mc.getUser().getProfileId()"), "isolated by player identity");
        assertTrue(gallery.contains("getWorldPath(LevelResource.ROOT)") && gallery.contains("getCurrentServer()"),
                "isolated by world save or server");
        assertFalse(gallery.contains("\"build\""));
    }

    @Test
    void developmentFullScreenCaptureIsGated() throws IOException {
        String capture = read("client/photo/PhotoCapture.java");
        assertTrue(capture.contains("return fullScreenDev && VerificationReporter.isDevEnvironment();"));
        assertTrue(capture.contains("fullScreenDev = on && VerificationReporter.isDevEnvironment();"));
        String viewfinder = read("client/camera/CameraViewfinder.java");
        assertFalse(viewfinder.toLowerCase().contains("fullscreen"), "no development control in the production viewfinder");
    }

    @Test
    void scanIsReservedAndInventsNothing() throws IOException {
        String mode = read("client/camera/CameraMode.java");
        assertTrue(mode.contains("SCAN(\"Scan\", false)"), "Scan exists but is unavailable in V1");
        for (String dir : new String[] {"client/photo", "client/camera"}) {
            try (Stream<Path> files = Files.list(MAIN.resolve(dir))) {
                for (Path f : files.toList()) {
                    String src = Files.readString(f);
                    for (String forbidden : new String[] {"Discovery(", "addSkillXp", "unlock(", "Codex.record"}) {
                        assertFalse(src.contains(forbidden), f.getFileName() + ": no invented discoveries or rewards");
                    }
                }
            }
        }
    }

    @Test
    void theCameraIsNotAScreenSoMovementContinues() throws IOException {
        String session = read("client/camera/CameraSession.java");
        assertFalse(session.contains("extends Screen"));
        assertTrue(session.contains("mc.options.setCameraType(previousCameraType)"), "perspective restored");
        assertFalse(session.contains("options.fov().set("), "the FOV setting is never written");
    }

    @Test
    void homeScreenKeepsItsLayoutAndGainsTheGalleryPage() throws IOException {
        String grid = read("screen/phone/PhoneAppGridScreen.java");
        assertTrue(grid.contains("dock.add(new App(\"Camera\",    \"Ca\", true, null, openCamera));"));
        assertTrue(grid.contains("secondary.add(new App(\"Gallery\",  \"Ga\", true, null, openGallery));"));
        assertFalse(grid.contains("all.add(new App(\"Gallery\""), "the main page's twelve apps are unchanged");
        assertTrue(grid.indexOf("pages.add(new Page(secondary, null));") > grid.indexOf("pages.add(new Page(all.subList("),
                "the Gallery page follows the main page");
    }
}
