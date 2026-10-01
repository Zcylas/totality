package zcylas.totality.client.photo;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import zcylas.totality.Totality;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GPU textures for the Gallery: small thumbnails (an LRU of at most {@link #MAX_THUMBNAILS}, read from the
 * {@code thumbnails/} cache and regenerated from the photo when that file is missing) and ONE full-resolution photo at a
 * time for the viewer. Hundreds of photos never mean hundreds of full-size textures: a grid only ever holds thumbnails.
 *
 * <p>Decoding happens on the Gallery I/O thread; uploads on the render thread. Textures are released one client tick
 * after they are dropped ({@link #tick}), so a texture is never closed during the frame that still draws it.
 */
public final class PhotoTextures {

    public static final int MAX_THUMBNAILS = 72;

    /** A ready texture and the size of the image it holds. */
    public record Loaded(DynamicTexture texture, int width, int height) {}

    private static final LinkedHashMap<String, Loaded> THUMBS = new LinkedHashMap<>(16, 0.75f, true);
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static final List<Runnable> RELEASE = new ArrayList<>();
    private static String fullId;
    private static Loaded full;
    private static int generation;

    private PhotoTextures() {}

    /** Linear filtering: photos are scaled down to thumbnails and screens, never magnified pixel art. */
    public static GpuSampler sampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    /** The thumbnail if it is ready; otherwise starts loading it and returns null (draw a placeholder). */
    public static Loaded thumbnail(Gallery gallery, PhotoMetadata meta) {
        Loaded t = THUMBS.get(meta.id());
        if (t != null || LOADING.contains(meta.id()) || FAILED.contains(meta.id()) || gallery.isMissing(meta.id())) return t;
        LOADING.add(meta.id());
        int gen = generation;
        GalleryStore store = gallery.store();
        Gallery.io(() -> {
            NativeImage image = readThumbnail(store, meta);
            Minecraft.getInstance().execute(() -> {
                LOADING.remove(meta.id());
                if (image == null) {
                    FAILED.add(meta.id());
                } else if (gen != generation) {
                    image.close();
                } else {
                    putThumbnail(meta.id(), image);
                }
            });
        });
        return null;
    }

    /** The full photograph for the viewer if it is ready; otherwise loads it (replacing the previous one). */
    public static Loaded full(Gallery gallery, PhotoMetadata meta) {
        if (meta.id().equals(fullId)) return full;
        releaseFull();
        fullId = meta.id();
        int gen = generation;
        Path path = gallery.store().imagePath(meta);
        Gallery.io(() -> {
            NativeImage image = read(path);
            Minecraft.getInstance().execute(() -> {
                if (image == null) return;
                if (gen != generation || !meta.id().equals(fullId)) {
                    image.close();
                    return;
                }
                full = upload("Totality photo " + meta.id(), image);
            });
        });
        return null;
    }

    /** The viewer closed: its full-size texture is no longer needed. */
    public static void releaseFull() {
        if (full != null) RELEASE.add(full.texture()::close);
        full = null;
        fullId = null;
    }

    /** A freshly captured photo's thumbnail (made from the capture itself, no second decode). Takes ownership. */
    static void putThumbnail(String id, NativeImage image) {
        Loaded old = THUMBS.put(id, upload("Totality thumbnail " + id, image));
        if (old != null) RELEASE.add(old.texture()::close);
        FAILED.remove(id);
        while (THUMBS.size() > MAX_THUMBNAILS) {
            Iterator<Map.Entry<String, Loaded>> eldest = THUMBS.entrySet().iterator();
            RELEASE.add(eldest.next().getValue().texture()::close);
            eldest.remove();
        }
    }

    /** A deleted photo: drop its textures. */
    static void discard(String id) {
        Loaded t = THUMBS.remove(id);
        if (t != null) RELEASE.add(t.texture()::close);
        FAILED.remove(id);
        if (id.equals(fullId)) releaseFull();
    }

    /** World change or disconnect: drop everything (late loads are discarded by the generation check). */
    static void clear() {
        generation++;
        for (Loaded t : THUMBS.values()) RELEASE.add(t.texture()::close);
        THUMBS.clear();
        LOADING.clear();
        FAILED.clear();
        releaseFull();
    }

    /** Client tick: closes textures dropped since the last frame. */
    public static void tick() {
        List<Runnable> due = List.copyOf(RELEASE);
        RELEASE.clear();
        due.forEach(Runnable::run);
    }

    /** Releases a GPU resource on the next client tick (after the frame that may still draw it). */
    public static void releaseLater(Runnable release) {
        RELEASE.add(release);
    }

    public static int cachedThumbnails() {
        return THUMBS.size();
    }

    public static boolean hasFullTexture() {
        return full != null;
    }

    private static Loaded upload(String label, NativeImage image) {
        int w = image.getWidth(), h = image.getHeight();
        return new Loaded(new DynamicTexture(() -> label, image), w, h);
    }

    /** I/O thread: the cached thumbnail, or a new one made from the photo (and written to the cache). */
    private static NativeImage readThumbnail(GalleryStore store, PhotoMetadata meta) {
        Path cached = store.thumbnailPath(meta.id());
        if (Files.exists(cached)) {
            NativeImage image = read(cached);
            if (image != null) return image;
        }
        NativeImage photo = read(store.imagePath(meta));
        if (photo == null) return null;
        try {
            return PhotoCapture.writeThumbnail(photo, cached);
        } finally {
            photo.close();
        }
    }

    private static NativeImage read(Path path) {
        if (!Files.exists(path)) return null;
        try (InputStream in = Files.newInputStream(path)) {
            return NativeImage.read(in);
        } catch (IOException | RuntimeException e) {
            Totality.LOGGER.warn("[Gallery] could not read {}", path, e);
            return null;
        }
    }
}
