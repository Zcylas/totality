package zcylas.totality.client.hologram;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dedicated-server safety of Notification V2 holograms, checked on the compiled bytecode (Totality has
 * one source set, so this is reference discipline): only client-side classes may reference the
 * hologram package, the common entrypoint never does, and hologram code never sends anything to the
 * server — holograms are client-private presentation.
 */
class HologramClientIsolationTest {

    private static final String HOLOGRAM = "zcylas/totality/client/hologram/";
    private static final List<String> CLIENT_SIDE_PREFIXES = List.of(
            "zcylas/totality/client/", "zcylas/totality/mixin/client/", "zcylas/totality/TotalityClient");
    private static final List<String> SERVER_SENDERS = List.of(
            "ClientPlayNetworking", "ServerPlayNetworking", "sendPacket", "sendChat", "sendCommand");

    private static Path classes() throws Exception {
        return Path.of(HologramStack.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    @Test
    void onlyClientClassesReferenceHolograms() throws Exception {
        Path root = classes();
        List<String> violations = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                scanned++;
                String name = root.relativize(file).toString().replace('\\', '/');
                String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                boolean clientSide = CLIENT_SIDE_PREFIXES.stream().anyMatch(name::startsWith);
                if (!clientSide && bytes.contains(HOLOGRAM)) violations.add(name + " references the hologram package");
                if (name.startsWith(HOLOGRAM)) {
                    for (String sender : SERVER_SENDERS) {
                        if (bytes.contains(sender)) violations.add(name + " references " + sender);
                    }
                }
            }
        }
        assertTrue(scanned > 100, "scanned only " + scanned + " classes");
        assertEquals(List.of(), violations);
    }

    @Test
    void commonEntrypointDoesNotReachHolograms() throws Exception {
        String common = new String(Files.readAllBytes(classes().resolve("zcylas/totality/Totality.class")),
                StandardCharsets.ISO_8859_1);
        assertFalse(common.contains("/hologram/"));
        String client = new String(Files.readAllBytes(classes().resolve("zcylas/totality/TotalityClient.class")),
                StandardCharsets.ISO_8859_1);
        assertTrue(client.contains(HOLOGRAM + "HologramManager"), "TotalityClient should register holograms");
    }
}
