package zcylas.totality.client.photo;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Real photography: copies what the game actually rendered, at the window's full resolution, and saves it as a
 * lossless PNG in the current {@link Gallery}.
 *
 * <p><b>When.</b> A requested shot is taken inside the next rendered frame, right after the world pass (terrain,
 * entities, particles, weather, the vanilla post effect and any shader pack's final composite, which all draw into the
 * main render target) and before the GUI pass. The viewfinder, the HUD and the crosshair are GUI, so they are never in a
 * photograph; the Camera also hides the HUD, so the held item and block outline aren't drawn in the world pass.
 *
 * <p><b>How, once.</b> The frame is (1) copied GPU-to-GPU into a preview texture for the shutter animation (no CPU work)
 * and (2) read back once into a {@link NativeImage}. That single image is encoded to PNG and downscaled to the Gallery
 * thumbnail on the Gallery I/O thread; the thumbnail goes straight into the texture cache. Nothing is decoded or
 * processed twice.
 *
 * <p><b>Development capture.</b> With {@code -Dtotality.camera.fullScreenCapture=true} (development environment only) or
 * {@code /totalityphone camera-fullscreen}, shots are taken at the END of the frame instead: the complete visible screen
 * including the HUD, overlays and the Camera interface, for debugging Totality interfaces.
 */
public final class PhotoCapture {

    public static final String FULL_SCREEN_PROPERTY = "totality.camera.fullScreenCapture";

    /** Development only (see class doc); never on in normal play. */
    private static volatile boolean fullScreenDev = Boolean.getBoolean(FULL_SCREEN_PROPERTY);

    private static final Random RANDOM = new Random();
    private static Shot pending;
    private static boolean worldRendered;

    private PhotoCapture() {}

    /** What the Camera asks for, and how it hears back (all callbacks on the render thread). */
    public record Shot(String mode, float zoom, float fov, Consumer<Preview> preview, Consumer<PhotoMetadata> saved,
                       Runnable failed) {}

    /** The captured frame on the GPU, for the shutter animation. Stored bottom-up: draw it with v flipped. */
    public record Preview(String id, GpuTexture texture, GpuTextureView view, int width, int height) {
        /** Frees the GPU copy after the frame that last drew it. */
        public void release() {
            PhotoTextures.releaseLater(() -> {
                view.close();
                texture.close();
            });
        }
    }

    public static boolean fullScreen() {
        return fullScreenDev && VerificationReporter.isDevEnvironment();
    }

    /** Development command toggle. */
    public static void setFullScreen(boolean on) {
        fullScreenDev = on && VerificationReporter.isDevEnvironment();
    }

    /** Queues a photograph for the next frame. False while the previous one hasn't been taken yet. */
    public static boolean request(Shot shot) {
        if (pending != null) return false;
        pending = shot;
        return true;
    }

    public static boolean pending() {
        return pending != null;
    }

    // ── Render hooks (GameRenderer mixin) ─────────────────────────────────────

    /** The world pass of this frame finished. */
    public static void onWorldRendered() {
        worldRendered = true;
    }

    /** Between the world (with post effects) and the GUI: clean photographs. */
    public static void beforeGui(RenderTarget target) {
        if (pending != null && worldRendered && !fullScreen()) take(target);
    }

    /** After the GUI: development full-screen captures. */
    public static void endOfFrame(RenderTarget target) {
        if (pending != null && worldRendered && fullScreen()) take(target);
        worldRendered = false;
    }

    private static void take(RenderTarget target) {
        Shot shot = pending;
        pending = null;
        Minecraft mc = Minecraft.getInstance();
        Gallery gallery = Gallery.current().orElse(null);
        GpuTexture source = target.getColorTexture();
        if (gallery == null || source == null || mc.player == null || mc.level == null) {
            shot.failed().run();
            return;
        }
        int w = target.width, h = target.height;
        GpuDevice device = RenderSystem.getDevice();
        GpuTexture copy = device.createTexture(() -> "Totality camera preview", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                source.getFormat(), w, h, 1, 1);
        device.createCommandEncoder().copyTextureToTexture(source, copy, 0, 0, 0, 0, 0, w, h);
        long now = System.currentTimeMillis();
        String id = GalleryStore.newId(now, RANDOM);
        shot.preview().accept(new Preview(id, copy, device.createTextureView(copy), w, h));

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 pos = camera.position();
        PhotoMetadata meta = new PhotoMetadata(id, gallery.reserveNumber(), "", now, "png", w, h, false, shot.mode(),
                shot.zoom(), shot.fov(), fullScreen() ? "full_screen" : "clean", mc.level.dimension().identifier().toString(),
                pos.x, pos.y, pos.z, camera.yRot(), camera.xRot(), null);
        Screenshot.takeScreenshot(target, image -> Gallery.io(() -> save(gallery, meta, image, shot)));
    }

    /** Gallery I/O thread: writes the PNG, the thumbnail and the metadata, then lists the photo. */
    private static void save(Gallery gallery, PhotoMetadata meta, NativeImage image, Shot shot) {
        GalleryStore store = gallery.store();
        NativeImage thumb = null;
        try (image) {
            Path file = store.imagePath(meta);
            Path tmp = GalleryStore.temporaryFor(file);
            image.writeToFile(tmp);
            GalleryStore.moveIntoPlace(tmp, file);
            thumb = writeThumbnail(image, store.thumbnailPath(meta.id()));
            store.writeMetadata(meta);
        } catch (IOException | RuntimeException e) {
            Totality.LOGGER.warn("[Gallery] could not save photo {}", meta.id(), e);
            if (thumb != null) thumb.close();
            Minecraft.getInstance().execute(shot.failed());
            return;
        }
        NativeImage finalThumb = thumb;
        Minecraft.getInstance().execute(() -> {
            gallery.added(meta);
            PhotoTextures.putThumbnail(meta.id(), finalThumb);
            shot.saved().accept(meta);
        });
    }

    /**
     * Downscales {@code photo} to the Gallery thumbnail width (keeping its aspect ratio), writes it to {@code path} and
     * returns it (the caller owns it). Any thread.
     */
    static NativeImage writeThumbnail(NativeImage photo, Path path) {
        int w = Math.min(GalleryStore.THUMB_WIDTH, photo.getWidth());
        int h = Math.max(1, Math.round(w * photo.getHeight() / (float) photo.getWidth()));
        NativeImage thumb = new NativeImage(w, h, false);
        photo.resizeSubRectTo(0, 0, photo.getWidth(), photo.getHeight(), thumb);
        try {
            Path tmp = GalleryStore.temporaryFor(path);
            thumb.writeToFile(tmp);
            GalleryStore.moveIntoPlace(tmp, path);
        } catch (IOException e) {
            // The cache is optional: the thumbnail is regenerated next time.
            Totality.LOGGER.debug("[Gallery] could not cache thumbnail {}", path, e);
        }
        return thumb;
    }
}
