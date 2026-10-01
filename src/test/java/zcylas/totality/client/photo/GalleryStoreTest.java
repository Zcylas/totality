package zcylas.totality.client.photo;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Gallery's disk format: one PNG plus one metadata sidecar per photo. Persistence (a new store over the same
 * directory = a game restart), rename/favourite/delete, isolation, and graceful handling of damaged or missing files.
 */
class GalleryStoreTest {

    @TempDir Path root;
    private final Random random = new Random(7);

    private PhotoMetadata photo(GalleryStore store, int number, long time, boolean writeImage) throws IOException {
        String id = GalleryStore.newId(time, random);
        PhotoMetadata m = new PhotoMetadata(id, number, "", time, "png", 64, 36, false, "normal", 1f, 70f, "clean",
                "minecraft:overworld", 1.5, 70, -3.25, 90f, 10f, null);
        if (writeImage) {
            Files.createDirectories(store.imagePath(m).getParent());
            ImageIO.write(new BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB), "png", store.imagePath(m).toFile());
        }
        store.writeMetadata(m);
        return m;
    }

    @Test
    void photosSurviveARestartNewestFirst() throws IOException {
        GalleryStore store = new GalleryStore(root);
        PhotoMetadata a = photo(store, 1, 1_000_000L, true);
        PhotoMetadata b = photo(store, 2, 2_000_000L, true);
        GalleryStore.Loaded loaded = new GalleryStore(root).load();
        assertEquals(List.of(b, a), loaded.photos(), "same metadata after reloading, newest first");
        assertEquals(3, loaded.nextNumber());
        assertTrue(loaded.missing().isEmpty());
        assertEquals("Photo 2", loaded.photos().getFirst().name(), "default display names");
        assertEquals("minecraft:overworld", loaded.photos().getFirst().dimension());
    }

    @Test
    void renameAndFavouriteChangeOnlyMetadataNeverTheIdOrFiles() throws IOException {
        GalleryStore store = new GalleryStore(root);
        PhotoMetadata m = photo(store, 1, 5_000L, true);
        Path image = store.imagePath(m);
        store.writeMetadata(m.withName("  Sunrise Peaks  ").withFavorite(true));
        PhotoMetadata reloaded = store.load().photos().getFirst();
        assertEquals(m.id(), reloaded.id());
        assertEquals("Sunrise Peaks", reloaded.name());
        assertTrue(reloaded.favorite());
        assertTrue(Files.exists(image), "the image file kept its stable name");
        assertEquals(image, store.imagePath(reloaded));
    }

    @Test
    void deleteRemovesImageMetadataAndThumbnail() throws IOException {
        GalleryStore store = new GalleryStore(root);
        PhotoMetadata m = photo(store, 1, 5_000L, true);
        Files.createDirectories(store.thumbnailPath(m.id()).getParent());
        Files.write(store.thumbnailPath(m.id()), new byte[] {1});
        store.delete(m);
        assertFalse(Files.exists(store.imagePath(m)));
        assertFalse(Files.exists(store.metadataPath(m.id())));
        assertFalse(Files.exists(store.thumbnailPath(m.id())));
        assertTrue(store.load().photos().isEmpty());
    }

    @Test
    void anImageWithoutMetadataIsRecovered() throws IOException {
        GalleryStore store = new GalleryStore(root);
        PhotoMetadata kept = photo(store, 4, 9_000L, true);
        PhotoMetadata lost = photo(store, 5, 8_000L, true);
        Files.delete(store.metadataPath(lost.id()));
        GalleryStore.Loaded loaded = store.load();
        assertEquals(1, loaded.recovered());
        PhotoMetadata recovered = loaded.photos().stream().filter(p -> p.id().equals(lost.id())).findFirst().orElseThrow();
        assertEquals("Recovered photo 5", recovered.name(), "numbered after the existing photos");
        assertEquals(64, recovered.width(), "size read from the PNG header");
        assertEquals(36, recovered.height());
        assertTrue(Files.exists(store.metadataPath(lost.id())), "its sidecar is rewritten");
        assertTrue(loaded.photos().contains(kept));
    }

    @Test
    void corruptMetadataIsSetAsideAndThePhotoKept() throws IOException {
        GalleryStore store = new GalleryStore(root);
        PhotoMetadata m = photo(store, 1, 5_000L, true);
        Files.writeString(store.metadataPath(m.id()), "{ not json");
        GalleryStore.Loaded loaded = store.load();
        assertEquals(1, loaded.photos().size(), "the photograph is still there");
        assertEquals(1, loaded.recovered());
        assertTrue(Files.exists(store.metadataPath(m.id()).resolveSibling(m.id() + ".json.corrupt")), "the damaged file is kept aside");
    }

    @Test
    void metadataWithoutItsImageIsReportedMissing() throws IOException {
        GalleryStore store = new GalleryStore(root);
        PhotoMetadata m = photo(store, 1, 5_000L, false);
        GalleryStore.Loaded loaded = store.load();
        assertEquals(List.of(m), loaded.photos());
        assertEquals(Set.of(m.id()), loaded.missing());
    }

    @Test
    void foreignFilesAndBadIdsAreIgnored() throws IOException {
        GalleryStore store = new GalleryStore(root);
        Path photos = root.resolve(GalleryStore.PHOTOS);
        Files.createDirectories(photos);
        Files.writeString(photos.resolve("notes.txt"), "hello");
        Files.writeString(photos.resolve("..evil.png"), "x");
        Files.writeString(photos.resolve("desktop.ini"), "x");
        assertTrue(store.load().photos().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.metadataPath("../../x"));
        assertThrows(IllegalArgumentException.class, () -> store.thumbnailPath("a/b"));
    }

    @Test
    void aSidecarCannotClaimAnotherPhoto() throws IOException {
        GalleryStore store = new GalleryStore(root);
        PhotoMetadata a = photo(store, 1, 1_000L, true);
        PhotoMetadata b = photo(store, 2, 2_000L, true);
        // b's sidecar now claims to be a.
        Files.writeString(store.metadataPath(b.id()), Files.readString(store.metadataPath(a.id())));
        List<PhotoMetadata> photos = store.load().photos();
        assertEquals(2, photos.size());
        assertEquals(1, photos.stream().filter(p -> p.id().equals(a.id())).count(), "no duplicate");
    }

    @Test
    void separateScopesNeverSeeEachOthersPhotos() throws IOException {
        UUID alex = UUID.randomUUID(), sam = UUID.randomUUID();
        GalleryStore worldAlex = new GalleryStore(GalleryScope.world("World A", alex).directory(root));
        GalleryStore worldSam = new GalleryStore(GalleryScope.world("World A", sam).directory(root));
        GalleryStore worldB = new GalleryStore(GalleryScope.world("World B", alex).directory(root));
        GalleryStore server = new GalleryStore(GalleryScope.server("play.example.net", alex).directory(root));
        photo(worldAlex, 1, 1_000L, true);
        photo(server, 1, 2_000L, true);
        assertEquals(1, worldAlex.load().photos().size());
        assertEquals(1, server.load().photos().size());
        assertTrue(worldSam.load().photos().isEmpty(), "another player in the same world");
        assertTrue(worldB.load().photos().isEmpty(), "another world");
    }

    @Test
    void contextFileRecordsTheScopeForPeople() throws IOException {
        GalleryScope scope = GalleryScope.server("play.example.net", UUID.randomUUID());
        GalleryStore store = new GalleryStore(scope.directory(root));
        store.writeContext(scope);
        String text = Files.readString(store.directory().resolve("context.json"));
        assertTrue(text.contains("play.example.net") && text.contains(scope.player().toString()));
    }

    @Test
    void metadataJsonRoundTripsAndToleratesUnknownFields() {
        JsonObject extra = new JsonObject();
        extra.addProperty("codexDiscovery", "totality:forest_boar");
        PhotoMetadata m = new PhotoMetadata("20261001-142530-123-a1b2", 3, "Wolfden Lake", 42L, "png", 1920, 1012, true,
                "normal", 2f, 38.6f, "clean", "minecraft:overworld", 1, 2, 3, 4, 5, extra);
        JsonObject json = m.toJson();
        json.addProperty("addedByAFutureVersion", true);
        PhotoMetadata back = PhotoMetadata.fromJson(json.toString(), m.id());
        assertEquals(m, back);
        assertEquals("totality:forest_boar", back.extra().get("codexDiscovery").getAsString(), "free-form extension data survives");
        assertThrows(IllegalArgumentException.class, () -> PhotoMetadata.fromJson(json.toString(), "20261001-142530-123-ffff"));
    }

    @Test
    void namesAreCleanedAndIdsValidated() {
        assertEquals("Sunrise", PhotoMetadata.cleanName("  Sun\u00a7crise\n "));
        assertEquals(PhotoMetadata.MAX_NAME_LENGTH, PhotoMetadata.cleanName("x".repeat(100)).length());
        PhotoMetadata blank = new PhotoMetadata("20261001-142530-123-a1b2", 7, "   ", 0, "png", 1, 1, false, "normal",
                1, 70, "clean", "", 0, 0, 0, 0, 0, null);
        assertEquals("Photo 7", blank.name(), "a blank name falls back to the default");
        assertThrows(IllegalArgumentException.class, () -> new PhotoMetadata("../x", 1, "a", 0, "png", 1, 1, false,
                "normal", 1, 70, "clean", "", 0, 0, 0, 0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new PhotoMetadata("20261001-142530-123-a1b2", 1, "a", 0, "exe",
                1, 1, false, "normal", 1, 70, "clean", "", 0, 0, 0, 0, 0, null));
        assertTrue(PhotoMetadata.isValidId(GalleryStore.newId(System.currentTimeMillis(), new Random())));
    }
}
