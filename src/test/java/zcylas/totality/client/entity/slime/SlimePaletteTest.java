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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Small Slime gradient-map palettes: the palette data matches the research report, the texel height map follows the
 * model's own UVs, generation is deterministic, keeps transparency and never touches the eye region, the optional top
 * ramps blend as specified, and the reloadable texture regenerates identically from the resource manager.
 */
class SlimePaletteTest {

    private static final Path TEXTURE = Path.of("src/main/resources/assets/totality/textures/entity/slime_test/slime_base.png");
    private static final int W = 128, H = 128;

    private static int[] sourcePixels() throws IOException {
        BufferedImage img = ImageIO.read(TEXTURE.toFile());
        return img.getRGB(0, 0, W, H, null, 0, W);
    }

    private static boolean inRect(int i, int x, int y, int w, int h) {
        int px = i % W, py = i / W;
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    // ── palette data ─────────────────────────────────────────────────────────────

    @Test
    void palettesMatchTheApprovedValues() {
        // Research report (Anemo, Electro, Hydro, Pyro) and the refinement pass's approved Geo (geo_stone_v3), Dendro
        // (dendro_v2) and Cryo (cryo_snowcap), promoted to the normal element palettes on 2026-10-02.
        Map<SlimePalette, int[]> expected = Map.of(
                SlimePalettes.ANEMO, new int[]{0x2D7E6A, 0x4FB596, 0x8EDDC0, 0xC6F3DF, 0xF6FFF9},
                SlimePalettes.GEO, new int[]{0x5A4A3C, 0x85715E, 0xB09B84, 0xD2C0A6, 0xEEE2CC},
                SlimePalettes.ELECTRO, new int[]{0x2E1450, 0x55287F, 0x8547B5, 0xB57EDB, 0xEBD5FF},
                SlimePalettes.DENDRO, new int[]{0x6F7F4C, 0x9AAA70, 0xC3CDA0, 0xDDE5C2, 0xF5F8E6},
                SlimePalettes.HYDRO, new int[]{0x0E5670, 0x1888A6, 0x2DB6CB, 0x7EDFE8, 0xE2FCFF},
                SlimePalettes.PYRO, new int[]{0x7E1A06, 0xC2400E, 0xF0791C, 0xFFB23A, 0xFFE9A6},
                SlimePalettes.CRYO, new int[]{0x2D5BA3, 0x4A88D4, 0x7AB6EE, 0xB5DCFA, 0xF4FAFF});
        expected.forEach((palette, ramp) -> assertArrayEquals(ramp, palette.ramp(), palette.name()));
        assertArrayEquals(new int[]{0x211C18, 0x332B25, 0x4A4038, 0x66594D, 0x8C7F72}, SlimePalettes.GEO.topRamp());
        assertEquals(0.40F, SlimePalettes.GEO.topFrom());
        assertEquals(0.65F, SlimePalettes.GEO.topTo());
        assertArrayEquals(new int[]{0x2F5F1C, 0x4F8A2C, 0x78B444, 0xA6D66C, 0xE2F5B8}, SlimePalettes.DENDRO.topRamp());
        assertEquals(0.35F, SlimePalettes.DENDRO.topFrom());
        assertEquals(0.60F, SlimePalettes.DENDRO.topTo());
        assertArrayEquals(new int[]{0xA9C3D6, 0xD4E4EE, 0xEEF6FB, 0xFAFDFF, 0xFFFFFF}, SlimePalettes.CRYO.topRamp());
        assertEquals(0.62F, SlimePalettes.CRYO.topFrom());
        assertEquals(1.0F, SlimePalettes.CRYO.topTo());
        for (SlimePalette p : List.of(SlimePalettes.ANEMO, SlimePalettes.ELECTRO, SlimePalettes.HYDRO, SlimePalettes.PYRO)) {
            assertNull(p.topRamp(), p.name() + " has no secondary ramp");
        }
        assertArrayEquals(new float[]{0.30F, 0.50F, 0.68F, 0.85F, 0.98F}, SlimePalette.STOPS);
    }

    @Test
    void everyVariantMapsToItsOwnPalette() {
        assertNull(SlimePalettes.forVariant(SlimeTestVariant.GRAYSCALE), "grayscale draws the source texture");
        Set<String> names = new HashSet<>();
        for (SlimeTestVariant v : SlimeTestVariant.values()) {
            if (v == SlimeTestVariant.GRAYSCALE) continue;
            SlimePalette p = SlimePalettes.forVariant(v);
            assertNotNull(p, v.name());
            assertEquals(v.serializedName(), p.name(), "palette name = variant name");
            assertTrue(names.add(p.name()), "one texture per palette");
        }
        assertEquals(7, names.size());
    }

    @Test
    void existingPalettesKeepTheirOriginalBlend() {
        // The blend end (topTo) was added in the refinement pass; palettes without one end at the dome top and
        // must give exactly the colours of the original formula smoothstep((h - topFrom) / (1 - topFrom)).
        for (SlimePalette p : List.of(SlimePalettes.CRYO)) {
            assertEquals(1.0F, p.topTo(), p.name());
            SlimePalette base = SlimePalette.of("base", p.ramp()), top = SlimePalette.of("top", p.topRamp());
            for (int gi = 0; gi <= 40; gi++) {
                for (int hi = 0; hi <= 40; hi++) {
                    float g = gi / 40.0F, h = hi / 40.0F;
                    float t = Math.clamp((h - p.topFrom()) / (1.0F - p.topFrom()), 0.0F, 1.0F);
                    float w = t * t * (3.0F - 2.0F * t);
                    int b = base.colour(g, 0), tc = top.colour(g, 0);
                    int expected = w <= 0.0F ? b : lerp(b, tc, w);
                    assertEquals(expected, p.colour(g, h), p.name() + " g=" + g + " h=" + h);
                }
            }
        }
    }

    @Test
    void blendEndMakesTheTopRampFullAboveIt() {
        SlimePalette c = SlimePalettes.GEO;
        SlimePalette top = SlimePalette.of("top", c.topRamp()), base = SlimePalette.of("base", c.ramp());
        for (float g : new float[]{0.4F, 0.7F, 0.95F}) {
            assertEquals(base.colour(g, 0), c.colour(g, 0.40F), "nothing at topFrom");
            assertEquals(top.colour(g, 0), c.colour(g, 0.65F), "full dome at topTo");
            assertEquals(top.colour(g, 0), c.colour(g, 0.95F), "full dome above topTo");
        }
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | g << 8 | bl;
    }

    @Test
    void rejectsMalformedPalettes() {
        assertThrows(IllegalArgumentException.class, () -> SlimePalette.of("short", 1, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> SlimePalette.of("ok", 1, 2, 3, 4, 5).withTop(0.5F, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> SlimePalette.of("ok", 1, 2, 3, 4, 5).withTop(1.0F, 1, 2, 3, 4, 5));
        assertThrows(IllegalArgumentException.class, () -> SlimePalette.of("ok", 1, 2, 3, 4, 5).withTop(0.6F, 0.5F, 1, 2, 3, 4, 5));
        assertThrows(IllegalArgumentException.class, () -> SlimePalette.of("ok", 1, 2, 3, 4, 5).withTop(0.5F, 1.2F, 1, 2, 3, 4, 5));
    }

    @Test
    void rampHitsItsStopsAndClampsOutsideThem() {
        SlimePalette p = SlimePalettes.PYRO;
        for (int i = 0; i < SlimePalette.STOPS.length; i++) {
            assertEquals(p.ramp()[i], p.colour(SlimePalette.STOPS[i], 0.0F), "stop " + i);
        }
        assertEquals(p.ramp()[0], p.colour(0.0F, 0.0F));
        assertEquals(p.ramp()[4], p.colour(1.0F, 0.0F));
    }

    @Test
    void secondaryRampBlendsOnlyAboveItsStartHeight() {
        SlimePalette d = SlimePalettes.DENDRO;
        for (float g : new float[]{0.35F, 0.6F, 0.9F}) {
            assertEquals(SlimePalette.of("base", d.ramp()).colour(g, 0), d.colour(g, 0.0F));
            assertEquals(SlimePalette.of("base", d.ramp()).colour(g, 0), d.colour(g, d.topFrom()), "no top colour at topFrom");
            assertEquals(SlimePalette.of("top", d.topRamp()).colour(g, 0), d.colour(g, 1.0F), "pure top colour at the dome top");
            int mid = d.colour(g, (d.topFrom() + d.topTo()) / 2);
            assertNotEquals(d.colour(g, 0.0F), mid);
            assertNotEquals(d.colour(g, 1.0F), mid);
        }
    }

    // ── texel heights from the model's UVs ──────────────────────────────────────

    @Test
    void heightMapFollowsTheModelUvs() {
        float[] h = SlimeTexelHeights.forBaseModel();
        assertEquals(W * H, h.length);
        int defined = 0;
        for (int i = 0; i < h.length; i++) {
            if (inRect(i, 0, 64, 6, 12) || inRect(i, 10, 64, 2, 2)) assertTrue(Float.isNaN(h[i]), "eye region is not body: " + i);
            if (inRect(i, 0, 70, 128, 58)) assertTrue(Float.isNaN(h[i]), "nothing below the body regions: " + i);
            if (!Float.isNaN(h[i])) {
                defined++;
                assertTrue(h[i] >= 0.0F && h[i] <= 1.0F);
            }
        }
        assertTrue(defined > 3000, "body texels found: " + defined);
        // Side views (front/back/east at rows 0-29, west at 30-59): a texel row is one height, 2 texels per unit.
        for (int y = 0; y < 30; y++) {
            float expect = 1.0F - (y + 0.5F) / 30.0F;
            for (int x = 0; x < 120; x++) {
                float v = h[y * W + x];
                if (!Float.isNaN(v)) assertEquals(expect, v, 1e-4F, "side row " + y + " col " + x);
            }
            for (int x = 0; x < 40; x++) {
                float v = h[(30 + y) * W + x];
                if (!Float.isNaN(v)) assertEquals(expect, v, 1e-4F, "west row " + y);
            }
        }
        // Top faces sample the top view (40,30), bottom faces the bottom view (80,30) (see SlimeUvMappingTest): the
        // top-view centre is the dome top, the bottom-view centre the base.
        assertEquals(1.0F, h[(30 + 20) * W + 40 + 20], 1e-4F, "top-view centre = dome top");
        assertEquals(0.0F, h[(30 + 20) * W + 80 + 20], 1e-4F, "bottom-view centre = base");
    }

    // ── generation ───────────────────────────────────────────────────────────────

    @Test
    void generationIsDeterministicKeepsAlphaAndLeavesEyesAlone() throws IOException {
        int[] src = sourcePixels();
        float[] h = SlimeTexelHeights.forBaseModel();
        for (SlimePalette p : List.of(SlimePalettes.ANEMO, SlimePalettes.GEO, SlimePalettes.ELECTRO, SlimePalettes.DENDRO,
                SlimePalettes.HYDRO, SlimePalettes.PYRO, SlimePalettes.CRYO)) {
            int[] a = SlimePaletteMapper.remap(src, h, p), b = SlimePaletteMapper.remap(src, h, p);
            assertArrayEquals(a, b, p.name() + " deterministic");
            int changed = 0;
            for (int i = 0; i < src.length; i++) {
                assertEquals(src[i] >>> 24, a[i] >>> 24, "alpha kept at " + i);
                if (Float.isNaN(h[i]) || src[i] >>> 24 == 0) assertEquals(src[i], a[i], "non-body texel untouched at " + i);
                if (a[i] != src[i]) changed++;
            }
            assertTrue(changed > 3000, p.name() + " recolours the body");
            for (int i = 0; i < src.length; i++) {
                if (inRect(i, 0, 64, 12, 12)) assertEquals(src[i], a[i], "eye region identical");
            }
        }
        assertArrayEquals(src, sourcePixels(), "the source pixels are not modified");
    }

    @Test
    void identityRampReproducesTheGrayscaleTexture() throws IOException {
        int[] ramp = new int[5];
        for (int i = 0; i < 5; i++) {
            int g = Math.round(SlimePalette.STOPS[i] * 255.0F);
            ramp[i] = g << 16 | g << 8 | g;
        }
        int[] src = sourcePixels();
        int[] out = SlimePaletteMapper.remap(src, SlimeTexelHeights.forBaseModel(), SlimePalette.of("identity", ramp));
        for (int i = 0; i < src.length; i++) {
            for (int shift = 0; shift <= 16; shift += 8) {
                assertTrue(Math.abs(((src[i] >> shift) & 0xFF) - ((out[i] >> shift) & 0xFF)) <= 1, "texel " + i);
            }
        }
    }

    @Test
    void secondaryRampsOnlyColourTheUpperBody() throws IOException {
        int[] src = sourcePixels();
        float[] h = SlimeTexelHeights.forBaseModel();
        int[] withTop = SlimePaletteMapper.remap(src, h, SlimePalettes.DENDRO);
        int[] baseOnly = SlimePaletteMapper.remap(src, h, SlimePalette.of("dendro_base", SlimePalettes.DENDRO.ramp()));
        int lower = 0, upperDiffers = 0;
        for (int i = 0; i < src.length; i++) {
            if (Float.isNaN(h[i])) continue;
            if (h[i] <= SlimePalettes.DENDRO.topFrom()) {
                assertEquals(baseOnly[i], withTop[i], "below the dome the base ramp applies: " + i);
                lower++;
            } else if (h[i] > 0.8F && baseOnly[i] != withTop[i]) {
                upperDiffers++;
            }
        }
        assertTrue(lower > 1000 && upperDiffers > 500, lower + " / " + upperDiffers);
    }

    // ── reloadable texture ───────────────────────────────────────────────────────

    /** A resource manager that serves the repository texture for the slime source id only. */
    private static ResourceManager sourceOnly() {
        Identifier source = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "textures/entity/slime_test/slime_base.png");
        return new ResourceManager() {
            @Override public Set<String> getNamespaces() { return Set.of(Totality.MOD_ID); }
            @Override public Optional<Resource> getResource(Identifier id) {
                return id.equals(source) ? Optional.of(new Resource((PackResources) null, () -> Files.newInputStream(TEXTURE))) : Optional.empty();
            }
            @Override public List<Resource> getResourceStack(Identifier id) { return getResource(id).stream().toList(); }
            @Override public Map<Identifier, Resource> listResources(String dir, Predicate<Identifier> f) { return Map.of(); }
            @Override public Map<Identifier, List<Resource>> listResourceStacks(String dir, Predicate<Identifier> f) { return Map.of(); }
            @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
        };
    }

    @Test
    void reloadRegeneratesTheSameTextureFromTheSource() throws IOException {
        Identifier id = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "slime_test/palette/test_hydro");
        SlimePaletteTexture texture = new SlimePaletteTexture(id, SlimePalettes.HYDRO, null, null);
        int before = SlimePaletteTexture.generations();
        int[][] loads = new int[3][];
        for (int n = 0; n < 3; n++) {
            try (TextureContents contents = texture.loadContents(sourceOnly())) {
                NativeImage img = contents.image();
                assertEquals(W, img.getWidth());
                loads[n] = new int[W * H];
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) loads[n][y * W + x] = img.getPixel(x, y);
            }
        }
        assertEquals(before + 3, SlimePaletteTexture.generations(), "each reload regenerates");
        assertArrayEquals(loads[0], loads[1]);
        assertArrayEquals(loads[0], loads[2]);
        assertArrayEquals(SlimePaletteMapper.remap(sourcePixels(), SlimeTexelHeights.forBaseModel(), SlimePalettes.HYDRO), loads[0],
                "the texture equals the pure remap of the source");
    }
}
