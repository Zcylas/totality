package zcylas.totality.api.voice.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class VoiceModelInstallerTest {

    private static final String ID = "test-model-1.0";

    @TempDir
    Path root;
    Path models;
    VoiceModelInstaller installer;
    AtomicInteger archiveOpens;

    @BeforeEach
    void setUp() {
        models = root.resolve("totality").resolve("voice").resolve("models");
        installer = new VoiceModelInstaller(models);
        archiveOpens = new AtomicInteger();
    }

    static Map<String, String> modelFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(ID + "/", null);
        files.put(ID + "/am/", null);
        files.put(ID + "/am/final.mdl", "acoustic-model-bytes");
        files.put(ID + "/conf/model.conf", "--beam=10.0");
        files.put(ID + "/graph/phones/word_boundary.int", "1 nonword");
        return files;
    }

    static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                if (e.getValue() != null) zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    static VoiceModelDescriptor descriptor(byte[] archive) throws Exception {
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive));
        return new VoiceModelDescriptor(ID, "x/" + ID + ".zip", sha, archive.length, "test", "Apache-2.0",
                List.of("am/final.mdl", "conf/model.conf"));
    }

    VoiceModelInstaller.ArchiveSource source(byte[] archive) {
        return () -> {
            archiveOpens.incrementAndGet();
            return new ByteArrayInputStream(archive);
        };
    }

    @Test
    void installsVerifiedModelWithDirectoryStructure() throws Exception {
        byte[] archive = zip(modelFiles());
        VoiceModelInstaller.InstalledModel m = installer.install(descriptor(archive), source(archive));
        assertFalse(m.reused());
        assertEquals(models.resolve(ID), m.directory());
        assertEquals(3, m.fileCount());
        assertEquals("acoustic-model-bytes", Files.readString(m.directory().resolve("am/final.mdl")));
        assertEquals("1 nonword", Files.readString(m.directory().resolve("graph/phones/word_boundary.int")));
        assertTrue(Files.isRegularFile(m.directory().resolve(VoiceModelInstaller.INSTALL_RECORD)));
        assertNoStagingLeft();
    }

    @Test
    void reusesAVerifiedInstallationWithoutReopeningTheArchive() throws Exception {
        byte[] archive = zip(modelFiles());
        VoiceModelDescriptor d = descriptor(archive);
        installer.install(d, source(archive));
        VoiceModelInstaller.InstalledModel again = installer.install(d, source(archive));
        assertTrue(again.reused());
        assertEquals(1, archiveOpens.get(), "a verified installation must not be extracted again");
    }

    @Test
    void corruptedInstalledFileIsDetectedAndReplaced() throws Exception {
        byte[] archive = zip(modelFiles());
        VoiceModelDescriptor d = descriptor(archive);
        Path dir = installer.install(d, source(archive)).directory();
        Files.writeString(dir.resolve("am/final.mdl"), "acoustic-model-bytez"); // same size, different content
        assertNull(installer.verifyInstallation(d));

        VoiceModelInstaller.InstalledModel fixed = installer.install(d, source(archive));
        assertFalse(fixed.reused());
        assertEquals("acoustic-model-bytes", Files.readString(dir.resolve("am/final.mdl")));
    }

    @Test
    void deletedInstalledFileIsDetected() throws Exception {
        byte[] archive = zip(modelFiles());
        VoiceModelDescriptor d = descriptor(archive);
        Path dir = installer.install(d, source(archive)).directory();
        Files.delete(dir.resolve("conf/model.conf"));
        assertNull(installer.verifyInstallation(d));
        assertTrue(Files.exists(installer.install(d, source(archive)).directory().resolve("conf/model.conf")));
    }

    @Test
    void interruptedInstallationIsCleanedUpAndRedone() throws Exception {
        byte[] archive = zip(modelFiles());
        VoiceModelDescriptor d = descriptor(archive);
        // What a crash mid-way through leaves: a half-written staging dir, and a target without a record
        // (e.g. a pre-record copy or a manually copied folder).
        Path staleStaging = models.resolve("." + ID + ".staging-12345");
        Files.createDirectories(staleStaging.resolve(ID).resolve("am"));
        Files.writeString(staleStaging.resolve(ID).resolve("am/final.mdl"), "partial");
        Files.createDirectories(models.resolve(ID).resolve("am"));
        Files.writeString(models.resolve(ID).resolve("am/final.mdl"), "partial");

        VoiceModelInstaller.InstalledModel m = installer.install(d, source(archive));
        assertFalse(m.reused());
        assertEquals("acoustic-model-bytes", Files.readString(m.directory().resolve("am/final.mdl")));
        assertFalse(Files.exists(staleStaging));
        assertNoStagingLeft();
    }

    @Test
    void invalidateForcesReextraction() throws Exception {
        byte[] archive = zip(modelFiles());
        VoiceModelDescriptor d = descriptor(archive);
        installer.install(d, source(archive));
        installer.invalidate(d);
        assertFalse(installer.install(d, source(archive)).reused());
        assertEquals(2, archiveOpens.get());
    }

    @Test
    void archiveWithWrongHashIsRejectedBeforeExtraction() throws Exception {
        byte[] archive = zip(modelFiles());
        VoiceModelDescriptor d = descriptor(archive);
        byte[] tampered = archive.clone();
        tampered[tampered.length / 2] ^= 0x55;
        assertThrows(ModelIntegrityException.class, () -> installer.install(d, source(tampered)));
        assertFalse(Files.exists(models.resolve(ID)));
        assertNoStagingLeft();
    }

    @Test
    void missingArchiveResourceFails() throws Exception {
        byte[] archive = zip(modelFiles());
        assertThrows(IOException.class, () -> installer.install(descriptor(archive), () -> null));
        assertFalse(Files.exists(models.resolve(ID)));
    }

    @Test
    void pathTraversalEntriesAreRejected() throws Exception {
        for (String evil : List.of(ID + "/../evil.txt", ID + "/am/../../evil.txt", "/etc/evil.txt",
                "other-dir/evil.txt", ID + "/am\\..\\..\\evil.txt", ID + "/C:/evil.txt", ID + "/./evil.txt")) {
            Map<String, String> files = modelFiles();
            files.put(evil, "pwned");
            byte[] archive = zip(files);
            assertThrows(ModelIntegrityException.class, () -> installer.install(descriptor(archive), source(archive)),
                    evil);
            assertFalse(Files.exists(models.resolve(ID)), evil);
            try (Stream<Path> all = Files.walk(root)) {
                assertTrue(all.noneMatch(p -> p.getFileName().toString().equals("evil.txt")), evil);
            }
        }
        assertNoStagingLeft();
    }

    @Test
    void missingRequiredFileFailsTheInstall() throws Exception {
        Map<String, String> files = modelFiles();
        files.remove(ID + "/conf/model.conf");
        byte[] archive = zip(files);
        ModelIntegrityException e = assertThrows(ModelIntegrityException.class,
                () -> installer.install(descriptor(archive), source(archive)));
        assertTrue(e.getMessage().contains("conf/model.conf"));
        assertFalse(Files.exists(models.resolve(ID)));
    }

    @Test
    void newPinnedArchiveReplacesOldInstallationButLeavesUnrelatedFilesAlone() throws Exception {
        Files.createDirectories(models.resolve("some-other-model"));
        Files.writeString(models.resolve("some-other-model").resolve("keep.txt"), "keep");
        Files.writeString(models.resolve("notes.txt"), "keep");

        byte[] archive = zip(modelFiles());
        installer.install(descriptor(archive), source(archive));

        Map<String, String> v2 = modelFiles();
        v2.put(ID + "/am/final.mdl", "acoustic-model-bytes-v2");
        byte[] archive2 = zip(v2);
        VoiceModelDescriptor d2 = descriptor(archive2);
        assertNull(installer.verifyInstallation(d2), "a record for a different archive pin must not verify");
        VoiceModelInstaller.InstalledModel m = installer.install(d2, source(archive2));
        assertFalse(m.reused());
        assertEquals("acoustic-model-bytes-v2", Files.readString(m.directory().resolve("am/final.mdl")));
        assertEquals("keep", Files.readString(models.resolve("some-other-model").resolve("keep.txt")));
        assertEquals("keep", Files.readString(models.resolve("notes.txt")));
    }

    @Test
    void resolveInsideOnlyAcceptsPlainRelativePaths() {
        Path r = root.toAbsolutePath();
        assertEquals(r.resolve("am/final.mdl"), VoiceModelInstaller.resolveInside(r, "am/final.mdl"));
        for (String bad : List.of("", "/abs", "../x", "a/../../x", "a//b", "./a", "a\\b", "C:/x", "a/..", "a\0b")) {
            assertNull(VoiceModelInstaller.resolveInside(r, bad), bad);
        }
    }

    private void assertNoStagingLeft() throws IOException {
        if (!Files.isDirectory(models)) return;
        try (Stream<Path> children = Files.list(models)) {
            assertTrue(children.noneMatch(p -> p.getFileName().toString().contains(".staging-")));
        }
    }
}
