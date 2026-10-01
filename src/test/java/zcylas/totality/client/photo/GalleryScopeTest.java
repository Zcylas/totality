package zcylas.totality.client.photo;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Each world/server and player has its own Gallery directory; nothing can collide or escape the root. */
class GalleryScopeTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e5");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-000000005a77");
    private static final Path ROOT = Path.of("/games/mc/totality/gallery/v1");

    @Test
    void worldsServersAndPlayersAreSeparate() {
        Path a = GalleryScope.world("New World", ALEX).directory(ROOT);
        assertNotEquals(a, GalleryScope.world("New World (1)", ALEX).directory(ROOT), "another save");
        assertNotEquals(a, GalleryScope.world("New World", SAM).directory(ROOT), "another player in the same save");
        assertNotEquals(a, GalleryScope.server("New World", ALEX).directory(ROOT), "a server is never a world");
        assertNotEquals(GalleryScope.server("play.example.net", ALEX).directory(ROOT),
                GalleryScope.server("mc.example.org", ALEX).directory(ROOT));
        assertEquals(a, GalleryScope.world("New World", ALEX).directory(ROOT), "stable across sessions");
    }

    @Test
    void namesThatSanitiseAlikeStillDiffer() {
        assertNotEquals(GalleryScope.world("My World", ALEX).key(), GalleryScope.world("My_World", ALEX).key());
        assertNotEquals(GalleryScope.world("Café", ALEX).key(), GalleryScope.world("Caf_", ALEX).key());
    }

    @Test
    void serverAddressesAreNormalised() {
        assertEquals("play.example.net", GalleryScope.normaliseAddress(" Play.Example.NET:25565 "));
        assertEquals("play.example.net:25570", GalleryScope.normaliseAddress("play.example.net:25570"));
        assertEquals(GalleryScope.server("play.example.net", ALEX), GalleryScope.server("PLAY.example.net:25565", ALEX));
        assertEquals("unknown", GalleryScope.normaliseAddress(null));
    }

    @Test
    void directoryNamesAreSafeAndStayUnderTheRoot() {
        for (String hostile : new String[] {"../../etc", "..", "C:\\Windows\\System32", "con", "a/b\\c:d*e?f\"g<h>i|j", "", "\u0000\u0001"}) {
            GalleryScope scope = GalleryScope.world(hostile, ALEX);
            String key = scope.key();
            assertTrue(key.matches("world-[a-z0-9_-]+-[0-9a-f]{8}"), key);
            assertTrue(scope.directory(ROOT).normalize().startsWith(ROOT), key);
            assertTrue(key.length() <= 6 + 32 + 9, key);
        }
    }

    @Test
    void anIdentityIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> GalleryScope.world("w", null));
        assertThrows(IllegalArgumentException.class, () -> new GalleryScope("realm", "x", ALEX));
    }
}
