package zcylas.totality.client.photo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import zcylas.totality.Totality;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The photographs of the current world/server and player — the one Gallery both the Camera's shortcut and the
 * Gallery app show. Owns the in-memory list (main thread only) and sends every disk operation to a single background
 * I/O thread, so file work never blocks rendering and always happens in order (a photo's files are written before
 * the photo is listed; a delete after the rename that preceded it).
 *
 * <p>{@link #current()} follows the player: joining another world, server or account opens that Gallery instead;
 * {@link #closeCurrent()} runs on disconnect. Loading is asynchronous: {@link #ready()} turns true when done.
 */
public final class Gallery {

    /** Bumped by every change of any gallery: screens compare it to refresh. */
    private static int version;
    private static Gallery current;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Totality Gallery I/O");
        t.setDaemon(true);
        return t;
    });

    private final GalleryScope scope;
    private final GalleryStore store;
    private final List<PhotoMetadata> photos = new ArrayList<>();
    private final Set<String> missing = new HashSet<>();
    private boolean ready;
    private boolean failed;
    private int nextNumber = 1;

    private Gallery(GalleryScope scope, GalleryStore store) {
        this.scope = scope;
        this.store = store;
    }

    // ── Session ───────────────────────────────────────────────────────────────

    /** The Gallery of the current world/server and player, opening it if needed; empty when not in a world. */
    public static Optional<Gallery> current() {
        Minecraft mc = Minecraft.getInstance();
        GalleryScope scope = scopeFor(mc);
        if (scope == null) return Optional.empty();
        if (current == null || !current.scope.equals(scope)) {
            current = open(scope, root(mc));
        }
        return Optional.of(current);
    }

    /** Disconnect / world change: forget the open Gallery and its textures (files are already saved). */
    public static void closeCurrent() {
        current = null;
        PhotoTextures.clear();
        version++;
    }

    public static int version() {
        return version;
    }

    /** Where every Gallery lives: {@code <game dir>/totality/gallery/v1/} (persistent, never the build directory). */
    public static Path root(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("totality").resolve("gallery").resolve("v1");
    }

    /** Singleplayer: the save folder; multiplayer: the server address; always with the logged-in profile's UUID. */
    static GalleryScope scopeFor(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        UUID player = mc.getUser().getProfileId();
        IntegratedServer local = mc.getSingleplayerServer();
        if (local != null) {
            Path save = local.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return GalleryScope.world(save.getFileName().toString(), player);
        }
        ServerData server = mc.getCurrentServer();
        return GalleryScope.server(server != null ? server.ip : null, player);
    }

    private static Gallery open(GalleryScope scope, Path root) {
        Gallery g = new Gallery(scope, new GalleryStore(scope.directory(root)));
        IO.execute(() -> {
            try {
                GalleryStore.Loaded loaded = g.store.load();
                g.store.writeContext(scope);
                if (loaded.recovered() > 0) {
                    Totality.LOGGER.info("[Gallery] recovered {} photo(s) without metadata in {}", loaded.recovered(), g.store.directory());
                }
                Minecraft.getInstance().execute(() -> g.loaded(loaded));
            } catch (IOException | RuntimeException e) {
                Totality.LOGGER.warn("[Gallery] could not load {}", g.store.directory(), e);
                Minecraft.getInstance().execute(() -> {
                    g.failed = true;
                    g.ready = true;
                    version++;
                });
            }
        });
        return g;
    }

    private void loaded(GalleryStore.Loaded loaded) {
        // Photos taken while loading were added already; keep them and add the stored ones.
        for (PhotoMetadata m : loaded.photos()) {
            if (find(m.id()) == null) photos.add(m);
        }
        photos.sort(GalleryStore.NEWEST_FIRST);
        missing.addAll(loaded.missing());
        nextNumber = Math.max(nextNumber, loaded.nextNumber());
        ready = true;
        version++;
    }

    // ── Queries (main thread) ─────────────────────────────────────────────────

    public boolean ready() {
        return ready;
    }

    /** Loading failed (the directory is unreadable); the Gallery shows an error instead of photos. */
    public boolean failed() {
        return failed;
    }

    public GalleryScope scope() {
        return scope;
    }

    public GalleryStore store() {
        return store;
    }

    /** Newest first. */
    public List<PhotoMetadata> photos() {
        return List.copyOf(photos);
    }

    public List<PhotoMetadata> favorites() {
        return photos.stream().filter(PhotoMetadata::favorite).toList();
    }

    public PhotoMetadata latest() {
        return photos.isEmpty() ? null : photos.getFirst();
    }

    public PhotoMetadata find(String id) {
        for (PhotoMetadata m : photos) if (m.id().equals(id)) return m;
        return null;
    }

    /** The image file is gone (deleted outside the game): listed with a placeholder so it can be removed. */
    public boolean isMissing(String id) {
        return missing.contains(id);
    }

    // ── Changes (main thread) ─────────────────────────────────────────────────

    /** The next "Photo N" number, reserved at the moment of capture. */
    public int reserveNumber() {
        return nextNumber++;
    }

    /** Runs {@code task} on the Gallery I/O thread, in order with every other file operation. */
    public static void io(Runnable task) {
        IO.execute(task);
    }

    /** A photograph whose image and metadata are already on disk (written by {@link PhotoCapture}). */
    void added(PhotoMetadata meta) {
        if (find(meta.id()) != null) return;
        photos.add(meta);
        photos.sort(GalleryStore.NEWEST_FIRST);
        version++;
    }

    /** Renames the display name only; the id (and file names) never change. Blank names are refused. */
    public boolean rename(String id, String newName) {
        PhotoMetadata m = find(id);
        String clean = PhotoMetadata.cleanName(newName);
        if (m == null || clean.isEmpty()) return false;
        if (clean.equals(m.name())) return true;
        replace(m.withName(clean));
        return true;
    }

    public void setFavorite(String id, boolean favorite) {
        PhotoMetadata m = find(id);
        if (m != null && m.favorite() != favorite) replace(m.withFavorite(favorite));
    }

    /** Permanently deletes the photograph's files and drops its textures. */
    public void delete(String id) {
        PhotoMetadata m = find(id);
        if (m == null) return;
        photos.remove(m);
        missing.remove(id);
        PhotoTextures.discard(id);
        version++;
        IO.execute(() -> {
            try {
                store.delete(m);
            } catch (IOException e) {
                Totality.LOGGER.warn("[Gallery] could not delete photo {}", id, e);
            }
        });
    }

    private void replace(PhotoMetadata updated) {
        photos.replaceAll(p -> p.id().equals(updated.id()) ? updated : p);
        version++;
        IO.execute(() -> {
            try {
                store.writeMetadata(updated);
            } catch (IOException e) {
                Totality.LOGGER.warn("[Gallery] could not save photo {}", updated.id(), e);
            }
        });
    }
}
