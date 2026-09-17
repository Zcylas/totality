package zcylas.totality.client.resource;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinel for the 2026-09-17 real-client manual test correction: real-client
 * testing found the Totality Food HUD showing vanilla's lossy 0-20 compatibility mirror (20/20,
 * 10/20 after {@code /totality food set 50}) instead of the true {@code totality:food} resource
 * (100/100, 50/100). The root cause was this class's own reader-registration wiring — Food was
 * registered with the native reader (still answering out of vanilla {@code FoodData}) even though
 * the Food 0-100 migration had already made it {@code GENERIC_COMPONENT} authority. This class
 * ({@code TotalityClientResourceReaders.register()}) is {@code @Environment(EnvType.CLIENT)} and
 * constructs a real {@code Minecraft.getInstance()}-backed access implementation, so it cannot be
 * invoked under plain JUnit — this is a source-text sentinel, not runtime proof, following the same
 * established convention as {@code Phase3CConsumerMigrationSourceRegressionTest}. The actual
 * resolution/routing logic this wiring depends on is proven with real, executing tests in
 * {@code ClientResourceServiceTest#foodResourceRepresentsTrueValueNotRawVanillaMirror} and
 * {@code NativeClientResourceReaderTest#foodIsNoLongerAnsweredByTheNativeReaderAfterTheFoodMigration}.
 */
class TotalityClientResourceReadersSourceRegressionTest {

    private static final Path READERS = Path.of(
            "src/main/java/zcylas/totality/client/resource/TotalityClientResourceReaders.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    @Test
    void foodIsRegisteredWithTheGenericReaderNotTheNativeReader() throws Exception {
        String source = read(READERS);

        int nativeBlockStart = source.indexOf("ClientResourceReader nativeReader =");
        int genericBlockStart = source.indexOf("ClientResourceReader genericReader =");
        assertTrue(nativeBlockStart >= 0 && genericBlockStart > nativeBlockStart,
                "expected to find the native reader block before the generic reader block");

        String nativeBlock = source.substring(nativeBlockStart, genericBlockStart);
        String genericBlock = source.substring(genericBlockStart);

        assertFalse(nativeBlock.contains("PlayerResourceIds.FOOD"),
                "Food must never again be registered with the native reader — that is exactly the bug "
                        + "the real-client HUD test found");
        assertTrue(genericBlock.contains("registerReader(PlayerResourceIds.FOOD, genericReader)"),
                "Food must be registered with the generic-synchronized reader, the same one Mana/Stamina/"
                        + "Rage/Spell Slots already use, since Food is GENERIC_COMPONENT authority");
    }

    @Test
    void healthAndBreathRemainOnTheNativeReader() throws Exception {
        String source = read(READERS);

        int nativeBlockStart = source.indexOf("ClientResourceReader nativeReader =");
        int genericBlockStart = source.indexOf("ClientResourceReader genericReader =");
        String nativeBlock = source.substring(nativeBlockStart, genericBlockStart);

        assertTrue(nativeBlock.contains("registerReader(PlayerResourceIds.HEALTH, nativeReader)"),
                "Health must remain on the native reader — it is still genuinely EXTERNAL_ADAPTER");
        assertTrue(nativeBlock.contains("registerReader(PlayerResourceIds.BREATH, nativeReader)"),
                "Breath must remain on the native reader — it is still genuinely EXTERNAL_ADAPTER");
    }
}
