package zcylas.totality.api.voice.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs a bundled model archive into {@code <modelsRoot>/<model id>/} — offline, verified, once.
 *
 * <ol>
 *   <li><b>Reuse:</b> an existing installation is reused only if its install record matches the
 *       pinned archive SHA-256 and every recorded file still has its recorded size and SHA-256.</li>
 *   <li><b>Invalid installation</b> (record missing, e.g. an interrupted install, or any file
 *       changed): that versioned directory — and only it — is deleted and reinstalled.</li>
 *   <li><b>Stage:</b> the archive is copied into a private staging directory while hashing; a hash
 *       or size mismatch aborts before anything is extracted.</li>
 *   <li><b>Extract safely:</b> every entry must live under the archive's single top-level directory
 *       (named after the model id), may not contain {@code ..}, absolute paths, drive letters or
 *       backslashes, and must resolve inside the staging directory; duplicates, excessive entry
 *       counts and excessive total size are rejected.</li>
 *   <li><b>Install atomically:</b> the install record is written last, then the whole staged
 *       directory is moved into place with an atomic rename. A crash at any earlier point leaves
 *       only a staging directory, which the next run deletes.</li>
 * </ol>
 *
 * <p>Blocking I/O: call off the render thread.
 */
public final class VoiceModelInstaller {

    public static final String INSTALL_RECORD = ".totality-model-install.properties";
    public static final int MAX_ENTRIES = 1_000;
    public static final long MAX_EXTRACTED_BYTES = 1024L * 1024 * 1024;

    private static final String STAGING_MARK = ".staging-";

    /** Opens the bundled archive's bytes (normally a classpath resource). */
    @FunctionalInterface
    public interface ArchiveSource {
        InputStream open() throws IOException;
    }

    public record InstalledModel(Path directory, boolean reused, int fileCount, long extractedBytes) {}

    private final Path modelsRoot;

    public VoiceModelInstaller(Path modelsRoot) {
        this.modelsRoot = modelsRoot.toAbsolutePath().normalize();
    }

    public Path modelDirectory(VoiceModelDescriptor model) {
        return modelsRoot.resolve(model.id());
    }

    public InstalledModel install(VoiceModelDescriptor model, ArchiveSource source) throws IOException {
        Path target = modelDirectory(model);
        InstalledModel existing = verifyInstallation(model);
        if (existing != null) return existing;

        Files.createDirectories(modelsRoot);
        deleteStaleStaging(model);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            deleteTree(target);
        }

        Path staging = modelsRoot.resolve("." + model.id() + STAGING_MARK + System.nanoTime());
        Files.createDirectory(staging);
        try {
            Path archive = staging.resolve("archive.zip");
            stageArchive(model, source, archive);

            Path staged = staging.resolve(model.id());
            Files.createDirectory(staged);
            Map<String, FileRecord> files = extract(model, archive, staged);
            for (String required : model.requiredFiles()) {
                if (!files.containsKey(required)) {
                    throw new ModelIntegrityException("Model " + model.id() + " is missing required file " + required);
                }
            }
            writeRecord(staged.resolve(INSTALL_RECORD), model, files);

            try {
                moveIntoPlace(staged, target);
            } catch (FileAlreadyExistsException | DirectoryNotEmptyException raced) {
                // Another game instance installed it first; accept theirs only if it verifies.
                InstalledModel theirs = verifyInstallation(model);
                if (theirs != null) return theirs;
                throw new ModelIntegrityException("Model directory appeared during install and does not verify: " + target);
            }
            long bytes = files.values().stream().mapToLong(FileRecord::size).sum();
            return new InstalledModel(target, false, files.size(), bytes);
        } finally {
            deleteTree(staging);
        }
    }

    /**
     * Returns the installation if it exists and fully verifies against {@code model}, else null.
     * Never modifies anything.
     */
    public InstalledModel verifyInstallation(VoiceModelDescriptor model) throws IOException {
        Path target = modelDirectory(model);
        Path record = target.resolve(INSTALL_RECORD);
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(record, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(record, StandardCharsets.UTF_8)) {
            p.load(reader);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
        if (!model.archiveSha256().equals(p.getProperty("archive.sha256"))) return null;

        int count = 0;
        long bytes = 0;
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith("file.")) continue;
            String rel = key.substring("file.".length());
            String[] sizeAndHash = p.getProperty(key).split(":", 2);
            if (sizeAndHash.length != 2) return null;
            Path file = resolveInside(target, rel);
            if (file == null || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null;
            long size;
            try {
                size = Long.parseLong(sizeAndHash[0]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (Files.size(file) != size || !sha256(file).equals(sizeAndHash[1])) return null;
            count++;
            bytes += size;
        }
        if (count == 0 || count != parseInt(p.getProperty("files.count"))) return null;
        for (String required : model.requiredFiles()) {
            if (!p.containsKey("file." + required)) return null;
        }
        return new InstalledModel(target, true, count, bytes);
    }

    /** Forces the next {@link #install} to re-extract, without deleting anything but the record. */
    public void invalidate(VoiceModelDescriptor model) throws IOException {
        Files.deleteIfExists(modelDirectory(model).resolve(INSTALL_RECORD));
    }

    private void stageArchive(VoiceModelDescriptor model, ArchiveSource source, Path archive) throws IOException {
        MessageDigest digest = newSha256();
        long copied;
        try (InputStream in = source.open()) {
            if (in == null) throw new IOException("Bundled model archive missing: " + model.archiveResource());
            try (DigestInputStream hashing = new DigestInputStream(in, digest)) {
                copied = Files.copy(hashing, archive);
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (copied != model.archiveSize() || !actual.equals(model.archiveSha256())) {
            throw new ModelIntegrityException("Bundled model archive " + model.id() + " failed verification: expected "
                    + model.archiveSize() + " bytes / sha256 " + model.archiveSha256() + ", got " + copied
                    + " bytes / sha256 " + actual);
        }
    }

    private Map<String, FileRecord> extract(VoiceModelDescriptor model, Path archive, Path staged) throws IOException {
        Map<String, FileRecord> files = new TreeMap<>();
        String prefix = model.id() + "/";
        int entries = 0;
        long total = 0;
        byte[] buffer = new byte[64 * 1024];

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new ModelIntegrityException("Model archive has too many entries");
                String name = entry.getName();
                if (!name.startsWith(prefix)) {
                    throw new ModelIntegrityException("Unexpected archive entry outside " + prefix + ": " + name);
                }
                String rel = name.substring(prefix.length());
                if (rel.isEmpty()) continue;
                Path out = resolveInside(staged, rel.endsWith("/") ? rel.substring(0, rel.length() - 1) : rel);
                if (out == null) throw new ModelIntegrityException("Unsafe archive entry rejected: " + name);

                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                MessageDigest digest = newSha256();
                long size = 0;
                try (OutputStream os = new DigestOutputStream(
                        Files.newOutputStream(out, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), digest)) {
                    int n;
                    while ((n = zip.read(buffer)) > 0) {
                        size += n;
                        total += n;
                        if (total > MAX_EXTRACTED_BYTES) {
                            throw new ModelIntegrityException("Model archive expands beyond " + MAX_EXTRACTED_BYTES + " bytes");
                        }
                        os.write(buffer, 0, n);
                    }
                } catch (FileAlreadyExistsException duplicate) {
                    throw new ModelIntegrityException("Duplicate archive entry: " + name);
                }
                files.put(rel, new FileRecord(size, HexFormat.of().formatHex(digest.digest())));
            }
        }
        if (files.isEmpty()) throw new ModelIntegrityException("Model archive contained no files");
        return files;
    }

    /**
     * Resolves a '/'-separated relative path strictly inside {@code root}; null if it is absolute,
     * uses backslashes or drive letters, contains empty/"."/".." segments, or escapes {@code root}.
     */
    static Path resolveInside(Path root, String rel) {
        if (rel.isEmpty() || rel.startsWith("/") || rel.contains("\\") || rel.contains(":") || rel.indexOf('\0') >= 0) {
            return null;
        }
        for (String segment : rel.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return null;
        }
        Path resolved = root.resolve(rel).normalize();
        return resolved.startsWith(root) && !resolved.equals(root) ? resolved : null;
    }

    private static void writeRecord(Path record, VoiceModelDescriptor model, Map<String, FileRecord> files) throws IOException {
        Properties p = new Properties();
        p.setProperty("model.id", model.id());
        p.setProperty("archive.sha256", model.archiveSha256());
        p.setProperty("files.count", Integer.toString(files.size()));
        files.forEach((rel, f) -> p.setProperty("file." + rel, f.size() + ":" + f.sha256()));
        try (Writer writer = Files.newBufferedWriter(record, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            p.store(writer, "Written by Totality after a verified extraction. Deleting this file forces re-extraction.");
        }
    }

    private static void moveIntoPlace(Path staged, Path target) throws IOException {
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(staged, target);
        }
    }

    private void deleteStaleStaging(VoiceModelDescriptor model) throws IOException {
        String prefix = "." + model.id() + STAGING_MARK;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(modelsRoot, prefix + "*")) {
            for (Path child : children) {
                if (child.getFileName().toString().startsWith(prefix)) deleteTree(child);
            }
        }
    }

    /** Deletes {@code dir} recursively without following symbolic links. */
    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static String sha256(Path file) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static int parseInt(String value) {
        try {
            return value == null ? -1 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private record FileRecord(long size, String sha256) {}
}
