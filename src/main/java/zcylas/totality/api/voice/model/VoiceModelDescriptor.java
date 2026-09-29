package zcylas.totality.api.voice.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * A speech model bundled inside the Totality jar, described by a properties manifest that the build
 * generates next to the archive (see {@code build.gradle}, "Voice Input API").
 *
 * @param id             versioned model id; also the extracted directory name and the archive's
 *                       single top-level directory
 * @param archiveResource classpath path of the bundled zip
 * @param archiveSha256  SHA-256 of the bundled zip, pinned by Totality (upstream publishes none)
 * @param archiveSize    size of the bundled zip in bytes
 * @param sourceUrl      where the build obtained the archive
 * @param license        SPDX id of the model's license
 * @param requiredFiles  files (relative to the model directory) that must exist after extraction
 */
public record VoiceModelDescriptor(String id, String archiveResource, String archiveSha256, long archiveSize,
                                   String sourceUrl, String license, List<String> requiredFiles) {

    public VoiceModelDescriptor {
        if (id == null || !id.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("Invalid model id: " + id);
        if (archiveSha256 == null || !archiveSha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Invalid archive SHA-256 for " + id);
        }
        archiveSha256 = archiveSha256.toLowerCase(Locale.ROOT);
        requiredFiles = List.copyOf(requiredFiles);
    }

    /** Classpath location of the manifest for {@code id}. */
    public static String manifestResource(String id) {
        return "/totality/voice/models/" + id + ".properties";
    }

    /** Reads the manifest for {@code id} from the classpath of {@code anchor}. */
    public static VoiceModelDescriptor load(Class<?> anchor, String id) throws IOException {
        String resource = manifestResource(id);
        try (InputStream in = anchor.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Bundled model manifest missing from the jar: " + resource);
            Properties p = new Properties();
            p.load(in);
            return new VoiceModelDescriptor(
                    require(p, "id"),
                    require(p, "archive"),
                    require(p, "archive.sha256"),
                    Long.parseLong(require(p, "archive.size")),
                    p.getProperty("source.url", ""),
                    p.getProperty("license", ""),
                    Arrays.stream(require(p, "required").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        }
    }

    private static String require(Properties p, String key) throws IOException {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Model manifest is missing '" + key + "'");
        return value.trim();
    }
}
