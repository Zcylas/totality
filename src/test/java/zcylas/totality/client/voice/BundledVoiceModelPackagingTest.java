package zcylas.totality.client.voice;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.voice.model.VoiceModelDescriptor;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The build must put the pinned model archive and its manifest on the runtime classpath (they end up
 * in the jar the same way). Hashes the real ~41 MB archive but loads no native code.
 */
class BundledVoiceModelPackagingTest {

    @Test
    void bundledArchiveMatchesItsPinnedManifest() throws Exception {
        VoiceModelDescriptor d = VoiceModelDescriptor.load(VoskRecognitionBackend.class,
                VoskRecognitionBackend.BUNDLED_MODEL_ID);
        assertEquals("vosk-model-small-en-us-0.15", d.id());
        assertEquals("30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498", d.archiveSha256());
        assertEquals(41_205_931L, d.archiveSize());
        assertEquals("Apache-2.0", d.license());
        assertTrue(d.requiredFiles().containsAll(List.of("am/final.mdl", "conf/model.conf", "graph/HCLr.fst")));

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long size;
        try (InputStream in = VoskRecognitionBackend.class.getResourceAsStream("/" + d.archiveResource())) {
            assertNotNull(in, "bundled archive missing: " + d.archiveResource());
            size = new DigestInputStream(in, digest).transferTo(OutputStream.nullOutputStream());
        }
        assertEquals(d.archiveSize(), size);
        assertEquals(d.archiveSha256(), HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void voskWrapperAndNativesAreOnTheClasspathWithoutAnOlderJna() throws Exception {
        assertNotNull(VoskRecognitionBackend.class.getResource("/linux-x86-64/libvosk.so"));
        assertNotNull(VoskRecognitionBackend.class.getResource("/win32-x86-64/libvosk.dll"));
        for (String dll : List.of("libstdc++-6.dll", "libgcc_s_seh-1.dll", "libwinpthread-1.dll")) {
            assertNotNull(VoskRecognitionBackend.class.getResource("/win32-x86-64/" + dll), dll);
        }
        // JNA must be Minecraft's copy, never Vosk's transitive 5.7.0.
        Class<?> jna = Class.forName("com.sun.jna.Native", false, getClass().getClassLoader());
        String location = jna.getProtectionDomain().getCodeSource().getLocation().toString();
        assertTrue(location.contains("jna-5.17.0"), "unexpected JNA on the classpath: " + location);
    }
}
