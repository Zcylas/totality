package zcylas.totality.client.entity.slime;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import zcylas.totality.Totality;
import zcylas.totality.entity.dev.SlimeTestVariant;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Small Slime element details (final textures, 2026-10-02): the motif masks exist for all seven elements and only mark
 * body texels, their three channels do what the mask format says, unrelated texels (eyes, transparent texels, unused
 * space) are never recoloured, element eye colours touch only the eye texels, generation is deterministic, and the
 * reloadable texture composites the mask from the resource manager (palette only when the mask is missing).
 */
class SlimeMotifTest {

    private static final Path TEXTURE = Path.of("src/main/resources/assets/totality/textures/entity/slime_test/slime_base.png");
    private static final Path MOTIFS = Path.of("src/main/resources/assets/totality/textures/entity/slime_test/motifs");
    private static final int W = 128, H = 128;
    private static final List<SlimeTestVariant> ELEMENTS = Arrays.stream(SlimeTestVariant.values())
            .filter(v -> v != SlimeTestVariant.GRAYSCALE).toList();

    private static int[] argb(Path png) throws IOException {
        BufferedImage img = ImageIO.read(png.toFile());
        assertEquals(W, img.getWidth(), png.toString());
        assertEquals(H, img.getHeight(), png.toString());
        return img.getRGB(0, 0, W, H, null, 0, W);
    }

    private static int[] mask(SlimeTestVariant v) throws IOException {
        return argb(MOTIFS.resolve(SlimePalettes.motifFor(v).element() + ".png"));
    }

    private static int[] uniformMask(int r, int g, int b) {
        int[] m = new int[W * H];
        Arrays.fill(m, 0xFF000000 | r << 16 | g << 8 | b);
        return m;
    }

    private static int firstBodyTexel(float[] h, float minHeight, float maxHeight) {
        for (int i = 0; i < h.length; i++) if (!Float.isNaN(h[i]) && h[i] >= minHeight && h[i] <= maxHeight) return i;
        throw new AssertionError("no body texel in range");
    }

    private static float gray(int p) {
        return (((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF)) / (3.0F * 255.0F);
    }

    // ── definitions and assets ───────────────────────────────────────────────────

    @Test
    void everyElementHasAMotifAndEyeColours() {
        assertNull(SlimePalettes.motifFor(SlimeTestVariant.GRAYSCALE));
        assertNull(SlimePalettes.eyesFor(SlimeTestVariant.GRAYSCALE));
        for (SlimeTestVariant v : ELEMENTS) {
            assertEquals(v.serializedName(), SlimePalettes.motifFor(v).element());
            assertEquals(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "textures/entity/slime_test/motifs/" + v.serializedName() + ".png"),
                    SlimePalettes.motifFor(v).mask());
            assertNotNull(SlimePalettes.eyesFor(v), v.name());
        }
        // Rim colours measured from the Small Slime references (UV decision report §2).
        assertEquals(new SlimeEyeColours(0x3493C7, 0x58B0D3, 0xE7F6E5), SlimePalettes.eyesFor(SlimeTestVariant.HYDRO));
        assertEquals(new SlimeEyeColours(0xD76942, 0xF5BB7E, 0xF3F4D8), SlimePalettes.eyesFor(SlimeTestVariant.GEO));
        assertEquals(new SlimeEyeColours(0x8239B2, 0xDE96D8, 0xF3F6DF), SlimePalettes.eyesFor(SlimeTestVariant.ELECTRO));
    }

    @Test
    void masksOnlyMarkBodyTexelsAwayFromTheEyes() throws IOException {
        float[] h = SlimeTexelHeights.forBaseModel();
        for (SlimeTestVariant v : ELEMENTS) {
            int[] m = mask(v);
            int marked = 0;
            for (int i = 0; i < m.length; i++) {
                if (m[i] >>> 24 == 0) continue;
                marked++;
                assertEquals(0xFF, m[i] >>> 24, v + ": mask alpha is 0 or 255");
                assertFalse(Float.isNaN(h[i]), v + ": detail on a texel no body face uses, at " + i);
                assertFalse(SlimePaletteMapper.inEyeRegion(i, W), v + ": detail in the eye region");
                assertFalse(i % W >= 80 && i / W >= 30 && i / W < 70, v + ": the hidden bottom view stays untouched");
            }
            assertTrue(marked > 50, v + " has details: " + marked);
        }
    }

    // ── the mask channels ────────────────────────────────────────────────────────

    @Test
    void neutralMaskEqualsThePaletteOnly() throws IOException {
        int[] src = argb(TEXTURE);
        float[] h = SlimeTexelHeights.forBaseModel();
        for (SlimePalette p : List.of(SlimePalettes.GEO, SlimePalettes.DENDRO, SlimePalettes.CRYO)) {
            assertArrayEquals(SlimePaletteMapper.remap(src, h, p),
                    SlimePaletteMapper.remap(src, h, p, uniformMask(128, 128, 0), 0xFFFFFF, null, W), p.name());
        }
    }

    @Test
    void redShiftsTheToneGreenTheEdgeBlueTheOverlay() throws IOException {
        int[] src = argb(TEXTURE);
        float[] h = SlimeTexelHeights.forBaseModel();
        SlimePalette d = SlimePalettes.DENDRO;
        int i = firstBodyTexel(h, 0.2F, 0.3F); // below Dendro's green band (0.35 -> 0.60)
        float g = gray(src[i]);
        int[] darker = SlimePaletteMapper.remap(src, h, d, uniformMask(128 - 51, 128, 0), 0, null, W);
        assertEquals(d.colour(Math.clamp(g - 51 / 255.0F, 0, 1), h[i]), darker[i] & 0xFFFFFF, "tone -0.2");
        int[] raised = SlimePaletteMapper.remap(src, h, d, uniformMask(128, 128 + 102, 0), 0, null, W);
        assertEquals(d.colour(g, Math.clamp(h[i] + 102 / 255.0F, 0, 1)), raised[i] & 0xFFFFFF, "edge +0.4: into the green");
        assertNotEquals(d.colour(g, h[i]), raised[i] & 0xFFFFFF);
        int[] snow = SlimePaletteMapper.remap(src, h, d, uniformMask(128, 128, 255), 0xFFFFFF, null, W);
        int s = snow[i] & 0xFFFFFF;
        assertEquals((s >> 16) & 0xFF, s & 0xFF, "full overlay is the (shaded) overlay colour");
        assertEquals((s >> 16) & 0xFF, (s >> 8) & 0xFF);
    }

    @Test
    void realMasksLeaveUnrelatedTexelsAloneAndAreDeterministic() throws IOException {
        int[] src = argb(TEXTURE);
        float[] h = SlimeTexelHeights.forBaseModel();
        for (SlimeTestVariant v : ELEMENTS) {
            SlimePalette p = SlimePalettes.forVariant(v);
            SlimeMotif motif = SlimePalettes.motifFor(v);
            int[] m = mask(v);
            int[] a = SlimePaletteMapper.remap(src, h, p, m, motif.overlayColour(), null, W);
            assertArrayEquals(a, SlimePaletteMapper.remap(src, h, p, m, motif.overlayColour(), null, W), v + " deterministic");
            int[] plain = SlimePaletteMapper.remap(src, h, p);
            int changed = 0;
            for (int i = 0; i < src.length; i++) {
                assertEquals(src[i] >>> 24, a[i] >>> 24, v + ": alpha kept");
                if (Float.isNaN(h[i])) assertEquals(src[i], a[i], v + ": non-body texel untouched at " + i);
                if (m[i] >>> 24 == 0) assertEquals(plain[i], a[i], v + ": unmarked texel = palette only at " + i);
                if (a[i] != plain[i]) changed++;
            }
            assertTrue(changed > 50, v + " details visible: " + changed);
        }
    }

    // ── eyes ─────────────────────────────────────────────────────────────────────

    @Test
    void elementEyesRecolourOnlyTheEyeTexels() throws IOException {
        int[] src = argb(TEXTURE);
        float[] h = SlimeTexelHeights.forBaseModel();
        for (SlimeTestVariant v : ELEMENTS) {
            SlimePalette p = SlimePalettes.forVariant(v);
            SlimeEyeColours eyes = SlimePalettes.eyesFor(v);
            int[] plain = SlimePaletteMapper.remap(src, h, p, null, 0, null, W);
            int[] coloured = SlimePaletteMapper.remap(src, h, p, null, 0, eyes, W);
            Set<Integer> expected = Set.of(eyes.rim(), eyes.glow(), eyes.interior(), SlimePalette.lerp(eyes.interior(), 0xFFFFFF, 0.5F));
            for (int i = 0; i < src.length; i++) {
                if (!SlimePaletteMapper.inEyeRegion(i, W)) {
                    assertEquals(plain[i], coloured[i], v + ": outside the eyes nothing changes");
                } else if (src[i] >>> 24 == 0) {
                    assertEquals(src[i], coloured[i], v + ": transparent eye corners stay transparent");
                } else {
                    assertEquals(src[i], plain[i], v + ": plain eyes are the original texels");
                    assertTrue(expected.contains(coloured[i] & 0xFFFFFF), v + ": eye texel uses an eye colour");
                    if ((src[i] & 0xFF) == 58) assertEquals(eyes.rim(), coloured[i] & 0xFFFFFF, v + ": the dark rim becomes the rim colour");
                }
            }
        }
    }

    // ── reloadable texture ───────────────────────────────────────────────────────

    private static ResourceManager resources(boolean withMotif, String element) {
        Identifier source = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "textures/entity/slime_test/slime_base.png");
        Identifier motif = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "textures/entity/slime_test/motifs/" + element + ".png");
        return new ResourceManager() {
            @Override public Set<String> getNamespaces() { return Set.of(Totality.MOD_ID); }
            @Override public Optional<Resource> getResource(Identifier id) {
                if (id.equals(source)) return Optional.of(new Resource((PackResources) null, () -> Files.newInputStream(TEXTURE)));
                if (withMotif && id.equals(motif)) {
                    return Optional.of(new Resource((PackResources) null, () -> Files.newInputStream(MOTIFS.resolve(element + ".png"))));
                }
                return Optional.empty();
            }
            @Override public List<Resource> getResourceStack(Identifier id) { return getResource(id).stream().toList(); }
            @Override public Map<Identifier, Resource> listResources(String dir, Predicate<Identifier> f) { return Map.of(); }
            @Override public Map<Identifier, List<Resource>> listResourceStacks(String dir, Predicate<Identifier> f) { return Map.of(); }
            @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
        };
    }

    private static int[] load(SlimePaletteTexture texture, ResourceManager rm) throws IOException {
        try (TextureContents contents = texture.loadContents(rm)) {
            NativeImage img = contents.image();
            int[] out = new int[W * H];
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) out[y * W + x] = img.getPixel(x, y);
            return out;
        }
    }

    @Test
    void reloadCompositesTheMaskFromTheResources() throws IOException {
        SlimeTestVariant v = SlimeTestVariant.GEO;
        SlimeMotif motif = SlimePalettes.motifFor(v);
        SlimeEyeColours eyes = SlimePalettes.eyesFor(v);
        Identifier id = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "slime_test/palette/test_geo");
        SlimePaletteTexture texture = new SlimePaletteTexture(id, SlimePalettes.GEO, motif, eyes);
        int[] src = argb(TEXTURE);
        float[] h = SlimeTexelHeights.forBaseModel();
        int[] expected = SlimePaletteMapper.remap(src, h, SlimePalettes.GEO, mask(v), motif.overlayColour(), eyes, W);
        int before = SlimePaletteTexture.generations();
        assertArrayEquals(expected, load(texture, resources(true, "geo")), "palette + mask + eyes from the resource manager");
        assertArrayEquals(expected, load(texture, resources(true, "geo")), "reload regenerates the same texture");
        assertEquals(before + 2, SlimePaletteTexture.generations());
        assertArrayEquals(SlimePaletteMapper.remap(src, h, SlimePalettes.GEO, null, 0, eyes, W), load(texture, resources(false, "geo")),
                "a missing mask leaves the palette only");
    }
}
