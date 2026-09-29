package zcylas.totality.client.voice;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.voice.audio.WavPcmReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Client/server isolation of the Voice Input API, checked on the compiled bytecode of the whole mod
 * (Totality uses a single source set, so this is enforced by reference discipline, not by Loom):
 *
 * <ul>
 *   <li>only {@code zcylas.totality.client.voice} may reference Vosk ({@code org/vosk});</li>
 *   <li>only {@code TotalityClient} (the client entrypoint) may reference {@code client.voice} from outside;</li>
 *   <li>the engine-neutral {@code api.voice} must reference neither Vosk, the client voice package,
 *       nor any Minecraft client class.</li>
 * </ul>
 *
 * A class's constant pool contains every class it refers to, so a plain byte search over the class
 * files is exact for these package names.
 */
class VoiceClientIsolationTest {

    private static final String VOSK = "org/vosk/";
    private static final String CLIENT_VOICE = "zcylas/totality/client/voice/";
    private static final String API_VOICE = "zcylas/totality/api/voice/";
    /** Member/class names whose presence in a constant pool would mean sending to the server. */
    private static final List<String> SERVER_SENDERS = List.of(
            "sendChat", "sendCommand", "sendUnattendedCommand", "sendUnsignedCommand", "ClientPlayNetworking",
            "ServerboundChat", "openChatAndAddText", "sendPacket");

    @Test
    void voiceReferencesStayOnTheClientSide() throws Exception {
        Path classes = Path.of(WavPcmReader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(classes.resolve("zcylas/totality")), "expected compiled classes at " + classes);

        List<String> violations = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                scanned++;
                String name = classes.relativize(file).toString().replace('\\', '/');
                String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                boolean inClientVoice = name.startsWith(CLIENT_VOICE);
                if (bytes.contains(VOSK) && !inClientVoice) {
                    violations.add(name + " references Vosk");
                }
                if (bytes.contains(CLIENT_VOICE) && !inClientVoice && !name.equals("zcylas/totality/TotalityClient.class")) {
                    violations.add(name + " references the client voice package");
                }
                if (name.startsWith(API_VOICE) && (bytes.contains("net/minecraft/client/") || bytes.contains("zcylas/totality/client/"))) {
                    violations.add(name + " (engine-neutral API) references client code");
                }
                if (name.startsWith(API_VOICE) && bytes.contains("org/lwjgl/")) {
                    violations.add(name + " (engine-neutral API) references LWJGL/OpenAL");
                }
                // Phase 2: voice output is local only. No voice class may reach anything that sends
                // chat, commands or packets to the server.
                if (inClientVoice || name.startsWith(API_VOICE)) {
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
    void commonEntrypointDoesNotReachVoice() throws Exception {
        Path classes = Path.of(WavPcmReader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String common = new String(Files.readAllBytes(classes.resolve("zcylas/totality/Totality.class")),
                StandardCharsets.ISO_8859_1);
        assertFalse(common.contains("/voice/"), "the common (server) entrypoint must not reference voice code");
        String client = new String(Files.readAllBytes(classes.resolve("zcylas/totality/TotalityClient.class")),
                StandardCharsets.ISO_8859_1);
        assertTrue(client.contains(CLIENT_VOICE + "VoiceRuntime"), "TotalityClient should register the voice runtime");
    }
}
