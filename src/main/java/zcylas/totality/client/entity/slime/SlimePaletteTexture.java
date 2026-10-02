package zcylas.totality.client.entity.slime;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import zcylas.totality.Totality;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A Small Slime body texture derived from the grayscale source ({@link SlimeTestRenderer#TEXTURE}): a
 * {@link SlimePalette}, optionally an element {@link SlimeMotif} mask and element {@link SlimeEyeColours}, generated in
 * memory and never written to disk. Registered once per combination with the TextureManager, which rebuilds it from
 * the current resource packs on every resource reload (F3+T); each rebuild replaces (and closes) the previous GPU
 * texture, so repeated reloads do not accumulate textures. A missing or wrongly sized motif mask leaves the palette only.
 */
public final class SlimePaletteTexture extends ReloadableTexture {

    private static final Map<String, Identifier> REGISTERED = new ConcurrentHashMap<>();
    /** Completed generations, for development verification of reload behaviour. */
    private static final AtomicInteger GENERATIONS = new AtomicInteger();

    private final SlimePalette palette;
    private final SlimeMotif motif;
    private final SlimeEyeColours eyes;

    SlimePaletteTexture(Identifier id, SlimePalette palette, SlimeMotif motif, SlimeEyeColours eyes) {
        super(id);
        this.palette = palette;
        this.motif = motif;
        this.eyes = eyes;
    }

    /** The palette-only texture (no motif, unchanged eyes). Render thread only. */
    public static Identifier idFor(SlimePalette palette) {
        return idFor(palette, null, null);
    }

    /** The texture for this combination, registering (and generating) it on first use. Render thread only. */
    public static Identifier idFor(SlimePalette palette, SlimeMotif motif, SlimeEyeColours eyes) {
        String key = palette.name() + (motif == null ? "_plain" : "") + (eyes == null ? "" : "_eyes");
        return REGISTERED.computeIfAbsent(key, name -> {
            Identifier id = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "slime_test/palette/" + name);
            Minecraft.getInstance().getTextureManager().registerAndLoad(id, new SlimePaletteTexture(id, palette, motif, eyes));
            return id;
        });
    }

    public static Map<String, Identifier> registered() {
        return Map.copyOf(REGISTERED);
    }

    public static int generations() {
        return GENERATIONS.get();
    }

    @Override
    public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
        TextureContents source = TextureContents.load(resourceManager, SlimeTestRenderer.TEXTURE);
        NativeImage image = source.image();
        int w = image.getWidth(), h = image.getHeight();
        if (w != SlimeBaseGeometry.TEXTURE_WIDTH || h != SlimeBaseGeometry.TEXTURE_HEIGHT) {
            return source; // a resource pack replaced the texture with another size: show it unrecoloured
        }
        int[] mask = this.motif == null ? null : loadMask(resourceManager, this.motif.mask(), w, h);
        int[] mapped = SlimePaletteMapper.remap(pixels(image), SlimeTexelHeights.forBaseModel(), this.palette, mask,
                this.motif == null ? 0 : this.motif.overlayColour(), this.eyes, w);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) image.setPixel(x, y, mapped[y * w + x]);
        }
        GENERATIONS.incrementAndGet();
        return source;
    }

    private static int[] loadMask(ResourceManager resourceManager, Identifier id, int w, int h) {
        if (resourceManager.getResource(id).isEmpty()) {
            Totality.LOGGER.warn("Small Slime motif mask {} is missing; using the palette only", id);
            return null;
        }
        try (TextureContents mask = TextureContents.load(resourceManager, id)) {
            if (mask.image().getWidth() != w || mask.image().getHeight() != h) {
                Totality.LOGGER.warn("Small Slime motif mask {} is not {}x{}; using the palette only", id, w, h);
                return null;
            }
            return pixels(mask.image());
        } catch (IOException e) {
            Totality.LOGGER.warn("Small Slime motif mask {} could not be read; using the palette only", id, e);
            return null;
        }
    }

    private static int[] pixels(NativeImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out[y * w + x] = image.getPixel(x, y);
        }
        return out;
    }
}
