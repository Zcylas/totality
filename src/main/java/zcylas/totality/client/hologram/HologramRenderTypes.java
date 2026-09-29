package zcylas.totality.client.hologram;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

import java.util.Optional;

/**
 * Hologram pipelines and textures. Every pipeline reuses vanilla's {@code core/position_tex_color}
 * shaders (no fog, only fully transparent texels discarded, so soft glows survive), draws both faces
 * and never writes depth, so hologram layers composite strictly in submission order (see
 * {@link HologramRenderer}); the {@link Mode} decides whether the world's depth is consulted.
 */
public final class HologramRenderTypes {

    /**
     * How a pass composites with the world. OVERLAY: no depth at all (first person — nothing can cut the
     * panel). WORLD: tested against the world's depth without writing it (third person — the character
     * and terrain really occlude the projected window). XRAY: drawn only where the world occludes it, for
     * a faint silhouette (the Mob HUD nameplate; the third-person window does not use it, so it never
     * paints over a character standing in front of it).
     */
    public enum Mode { OVERLAY, WORLD, XRAY }

    /** The mode subsequent {@link Tex#translucent()}/{@link Tex#additive()} lookups use (render thread). */
    static Mode mode = Mode.OVERLAY;

    private static final RenderPipeline[] PIPELINES = new RenderPipeline[Mode.values().length * 2];

    static {
        for (Mode m : Mode.values()) {
            for (boolean additive : new boolean[] {false, true}) {
                String name = "pipeline/hologram" + (additive ? "_additive" : "")
                        + (m == Mode.OVERLAY ? "" : "_" + m.name().toLowerCase(java.util.Locale.ROOT));
                RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                        .withLocation(Identifier.fromNamespaceAndPath(Totality.MOD_ID, name))
                        .withCull(false);
                switch (m) {
                    case OVERLAY -> b.withDepthStencilState(Optional.empty());
                    // Reversed-Z: GEQUAL passes where the panel is nearer than the world, LESS where hidden.
                    case WORLD -> b.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
                    case XRAY -> b.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN, false));
                }
                if (additive) b.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING));
                PIPELINES[m.ordinal() * 2 + (additive ? 1 : 0)] = RenderPipelines.register(b.build());
            }
        }
    }

    public static final Tex SURFACE = new Tex("panel_surface", 96, 96, false);
    public static final Tex FRAME = new Tex("panel_frame", 112, 112, false);
    public static final Tex GLOW = new Tex("panel_glow", 224, 224, false);
    public static final Tex GRID = new Tex("grid", 64, 64, true);
    public static final Tex LINE = new Tex("line_soft", 128, 16, false);
    public static final Tex SCANLINE = new Tex("scanline", 16, 64, false);
    public static final Tex WHITE = new Tex("white", 8, 8, false);
    public static final Tex BUTTON_FRAME = new Tex("button_frame", 64, 32, false);
    public static final Tex BUTTON_FILL = new Tex("button_fill", 64, 32, false);
    public static final Tex ICONS = new Tex("icons", 64 * 7, 64, false);
    public static final Tex ICON_HALO = new Tex("icon_halo", 64, 64, false);
    public static final Tex HIT_RING = new Tex("hit_ring", 32, 32, false);
    // Mob HUD V1 target nameplate.
    public static final Tex NAMEPLATE_FILL = new Tex("nameplate_fill", 256, 64, false);
    public static final Tex NAMEPLATE_FRAME = new Tex("nameplate_frame", 256, 64, false);
    public static final Tex NAMEPLATE_GLOW = new Tex("nameplate_glow", 320, 128, false);
    public static final Tex CHEVRON = new Tex("chevron", 32, 20, false);

    private HologramRenderTypes() {}

    /** A hologram texture with its translucent and additive render types per {@link Mode} (linear filtering). */
    public static final class Tex {
        public final Identifier id;
        public final int width;
        public final int height;
        private final RenderType[] types = new RenderType[PIPELINES.length];

        Tex(String name, int width, int height, boolean repeat) {
            this.id = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "textures/hologram/" + name + ".png");
            this.width = width;
            this.height = height;
            for (int i = 0; i < PIPELINES.length; i++) {
                types[i] = create("totality_hologram_" + name + "_" + i, PIPELINES[i], repeat);
            }
        }

        public RenderType translucent() {
            return types[mode.ordinal() * 2];
        }

        public RenderType additive() {
            return types[mode.ordinal() * 2 + 1];
        }

        private RenderType create(String name, RenderPipeline pipeline, boolean repeat) {
            return RenderType.create(name, RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", id, () -> repeat
                            ? RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR)
                            : RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                    .createRenderSetup());
        }
    }
}
