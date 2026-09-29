package zcylas.totality.client.operator;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.operator.OperatorAction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Operator Mode's boundaries, checked on the compiled bytecode (a class's constant pool names every
 * class and member it uses):
 * <ul>
 *   <li>on the client, only {@code client.operator} sends Operator Mode requests — the voice packages
 *       never send anything (see {@code VoiceClientIsolationTest});</li>
 *   <li>neither the client sender nor the server authority ever touches chat, command strings or the
 *       command dispatcher: the request is a typed whitelist, never text to execute.</li>
 * </ul>
 */
class OperatorModeIsolationTest {

    private static final List<String> SERVERBOUND = List.of(
            "zcylas/totality/networking/operator/OperatorActionPayload",
            "zcylas/totality/networking/operator/OperatorAuthorizationQueryPayload");
    private static final List<String> NO_TEXT_EXECUTION = List.of(
            "sendChat", "sendCommand", "sendUnsignedCommand", "sendUnattendedCommand", "performPrefixedCommand",
            "performCommand", "CommandDispatcher", "getCommands", "ServerboundChat");

    @Test
    void onlyTheOperatorPackageSendsAndNothingExecutesText() throws Exception {
        Path root = Path.of(OperatorAction.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> violations = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                scanned++;
                String name = root.relativize(file).toString().replace('\\', '/');
                String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                boolean client = name.startsWith("zcylas/totality/client/");
                if (client && !name.startsWith("zcylas/totality/client/operator/")) {
                    for (String type : SERVERBOUND) if (bytes.contains(type)) violations.add(name + " sends " + type);
                }
                if (name.startsWith("zcylas/totality/client/operator/") || name.startsWith("zcylas/totality/server/operator/OperatorModeServer")
                        || name.startsWith("zcylas/totality/networking/operator/")) {
                    for (String t : NO_TEXT_EXECUTION) if (bytes.contains(t)) violations.add(name + " references " + t);
                }
            }
        }
        assertTrue(scanned > 100, "scanned only " + scanned);
        assertEquals(List.of(), violations);
    }
}
