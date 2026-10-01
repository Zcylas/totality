package zcylas.totality.client.photo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * One Gallery's files on disk (pure I/O, no Minecraft classes; unit-tested). Layout, under {@link GalleryScope#directory}:
 * <pre>
 *   context.json              which world/server and player this is (for people; never read back)
 *   photos/&lt;id&gt;.png          the photograph, full resolution, lossless
 *   photos/&lt;id&gt;.json         its metadata ({@link PhotoMetadata}; one small file per photo)
 *   thumbnails/&lt;id&gt;.png      a 256 px wide preview cache (regenerable; safe to delete)
 * </pre>
 * Every write goes to a temporary file first and is then moved into place, so a crash never leaves half a file.
 * Callers run these methods on the Gallery's I/O thread, never the render thread.
 */
public final class GalleryStore {

    public static final String PHOTOS = "photos";
    public static final String THUMBNAILS = "thumbnails";
    public static final int THUMB_WIDTH = 256;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);

    private final Path dir;

    public GalleryStore(Path dir) {
        this.dir = dir;
    }

    public Path directory() {
        return dir;
    }

    public Path imagePath(PhotoMetadata meta) {
        return dir.resolve(PHOTOS).resolve(meta.fileName());
    }

    public Path metadataPath(String id) {
        requireId(id);
        return dir.resolve(PHOTOS).resolve(id + ".json");
    }

    public Path thumbnailPath(String id) {
        requireId(id);
        return dir.resolve(THUMBNAILS).resolve(id + ".png");
    }

    /** A new stable id: capture time (local) plus 4 random hex digits, e.g. {@code 20261001-142530-123-a1b2}. */
    public static String newId(long epochMillis, Random random) {
        return ID_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
                + String.format(Locale.ROOT, "-%04x", random.nextInt(0x10000));
    }

    /** What {@link #load} found. {@code missing}: photos whose image file is gone (shown as such, deletable). */
    public record Loaded(List<PhotoMetadata> photos, Set<String> missing, int recovered, int nextNumber) {}

    /**
     * Reads every photo of this gallery, newest first. Damaged or missing pieces never fail the whole gallery:
     * <ul>
     *   <li>an image without (readable) metadata is recovered with default metadata, and its sidecar rewritten;
     *       an unreadable sidecar is kept aside as {@code <id>.json.corrupt};</li>
     *   <li>metadata without its image stays listed and is reported in {@code missing};</li>
     *   <li>files whose names are not photo ids are ignored.</li>
     * </ul>
     */
    public Loaded load() throws IOException {
        Path photos = dir.resolve(PHOTOS);
        Map<String, PhotoMetadata> byId = new HashMap<>();
        Map<String, Path> images = new HashMap<>();
        if (Files.isDirectory(photos)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(photos)) {
                for (Path f : files) {
                    String name = f.getFileName().toString();
                    int dot = name.indexOf('.');
                    if (dot < 0) continue;
                    String id = name.substring(0, dot), ext = name.substring(dot + 1);
                    if (!PhotoMetadata.isValidId(id)) continue;
                    if (ext.equals("png") || ext.equals("jpg")) images.put(id, f);
                }
            }
            for (String id : images.keySet()) {
                Path sidecar = metadataPath(id);
                if (!Files.exists(sidecar)) continue;
                try {
                    byId.put(id, PhotoMetadata.fromJson(Files.readString(sidecar, StandardCharsets.UTF_8), id));
                } catch (RuntimeException | IOException e) {
                    moveAside(sidecar);
                }
            }
            // Sidecars whose image is gone.
            try (DirectoryStream<Path> files = Files.newDirectoryStream(photos, "*.json")) {
                for (Path f : files) {
                    String id = f.getFileName().toString().replaceFirst("\\.json$", "");
                    if (!PhotoMetadata.isValidId(id) || images.containsKey(id) || byId.containsKey(id)) continue;
                    try {
                        byId.put(id, PhotoMetadata.fromJson(Files.readString(f, StandardCharsets.UTF_8), id));
                    } catch (RuntimeException | IOException e) {
                        moveAside(f);
                    }
                }
            }
        }
        int next = 1;
        for (PhotoMetadata m : byId.values()) next = Math.max(next, m.number() + 1);
        int recovered = 0;
        List<String> orphans = new ArrayList<>(images.keySet());
        orphans.removeAll(byId.keySet());
        orphans.sort(Comparator.naturalOrder());
        for (String id : orphans) {
            Path img = images.get(id);
            int[] size = imageSize(img);
            String ext = img.getFileName().toString().substring(id.length() + 1);
            PhotoMetadata m = new PhotoMetadata(id, next, "Recovered photo " + next, Files.getLastModifiedTime(img).toMillis(),
                    ext, size[0], size[1], false, "normal", 1f, 0f, "clean", "", 0, 0, 0, 0, 0, null);
            next++;
            writeMetadata(m);
            byId.put(id, m);
            recovered++;
        }
        Set<String> missing = new HashSet<>();
        for (PhotoMetadata m : byId.values()) if (!Files.exists(imagePath(m))) missing.add(m.id());
        List<PhotoMetadata> list = new ArrayList<>(byId.values());
        list.sort(NEWEST_FIRST);
        return new Loaded(List.copyOf(list), Set.copyOf(missing), recovered, next);
    }

    public static final Comparator<PhotoMetadata> NEWEST_FIRST =
            Comparator.comparingLong(PhotoMetadata::capturedAt).thenComparing(PhotoMetadata::id).reversed();

    /** Writes (or replaces) a photo's metadata sidecar. */
    public void writeMetadata(PhotoMetadata meta) throws IOException {
        writeAtomically(metadataPath(meta.id()), GSON.toJson(meta.toJson()).getBytes(StandardCharsets.UTF_8));
    }

    /** Records which world/server and player this directory is for (informational only). */
    public void writeContext(GalleryScope scope) throws IOException {
        Path f = dir.resolve("context.json");
        if (Files.exists(f)) return;
        JsonObject o = new JsonObject();
        o.addProperty("schema", PhotoMetadata.SCHEMA);
        o.addProperty("kind", scope.kind());
        o.addProperty("source", scope.source());
        o.addProperty("player", scope.player().toString());
        writeAtomically(f, GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
    }

    /** Removes the image, its metadata and its thumbnail. Missing files are fine. */
    public void delete(PhotoMetadata meta) throws IOException {
        Files.deleteIfExists(imagePath(meta));
        Files.deleteIfExists(metadataPath(meta.id()));
        Files.deleteIfExists(thumbnailPath(meta.id()));
    }

    /** Moves {@code tmp} to {@code target}, atomically where the file system allows. */
    public static void moveIntoPlace(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** A temporary path next to {@code target} (same directory, so the final move stays on one file system). */
    public static Path temporaryFor(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        return target.resolveSibling(target.getFileName() + ".tmp");
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Path tmp = temporaryFor(target);
        Files.write(tmp, bytes);
        moveIntoPlace(tmp, target);
    }

    private static void moveAside(Path f) {
        try {
            moveIntoPlace(f, f.resolveSibling(f.getFileName() + ".corrupt"));
        } catch (IOException ignored) {
            // Left in place: it is skipped again on the next load.
        }
    }

    private static void requireId(String id) {
        if (!PhotoMetadata.isValidId(id)) throw new IllegalArgumentException("invalid photo id: " + id);
    }

    /** Width and height from a PNG header (0, 0 when it isn't a readable PNG). */
    static int[] imageSize(Path png) {
        try (InputStream in = Files.newInputStream(png)) {
            byte[] h = in.readNBytes(24);
            if (h.length < 24 || (h[1] != 'P' || h[2] != 'N' || h[3] != 'G')) return new int[] {0, 0};
            return new int[] {readInt(h, 16), readInt(h, 20)};
        } catch (IOException e) {
            return new int[] {0, 0};
        }
    }

    private static int readInt(byte[] b, int at) {
        return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }
}
