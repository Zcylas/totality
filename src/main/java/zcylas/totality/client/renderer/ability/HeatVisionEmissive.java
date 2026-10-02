package zcylas.totality.client.renderer.ability;

import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.vfx.glow.EmissiveBuffer;
import zcylas.totality.client.vfx.glow.EmissiveGlow;
import zcylas.totality.client.vfx.glow.EmissiveSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Heat Vision's contribution to the Totality Emissive Rendering Layer: the hot core of each beam and its impact
 * hotspot, as plain emissive geometry. The layer's bloom feature turns it into the beam's glow.
 *
 * <p>Brightness is capped per beam ({@link #CORE} and {@link #HOTSPOT} at full strength), so several beams add up
 * predictably. The beams are handed over once per frame and consumed by {@link #emit}; when no beam is drawn the
 * source unregisters itself, so a stopped beam leaves nothing behind in the layer.
 */
final class HeatVisionEmissive implements EmissiveSource {

    static final HeatVisionEmissive INSTANCE = new HeatVisionEmissive();

    /** Emissive colour of the core at full strength (linear light, the per-beam cap). */
    static final float[] CORE = {1.0f, 0.32f, 0.06f};
    static final float[] HOTSPOT = {1.0f, 0.45f, 0.12f};
    static final double CORE_WIDTH_SCALE = 0.45;
    static final double HOTSPOT_HALF_SIZE = 0.16;

    private final List<HeatVisionBeam> frameBeams = new ArrayList<>();
    private double baseHalfWidth;
    private double minHalfAngle;
    private int segments;

    private HeatVisionEmissive() {}

    /** Hands this frame's beams to the layer (render thread), or unregisters when there are none. */
    void submit(List<HeatVisionBeam> beams, int segments, double baseHalfWidth, double minHalfAngle) {
        frameBeams.clear();
        frameBeams.addAll(beams);
        this.segments = segments;
        this.baseHalfWidth = baseHalfWidth;
        this.minHalfAngle = minHalfAngle;
        if (frameBeams.isEmpty()) {
            EmissiveGlow.removeSource(this);
        } else {
            EmissiveGlow.addSource(this);
        }
    }

    /** Each beam at full strength counts as half an effect toward the layer's brightness budget (no group limit). */
    @Override
    public float emissiveDemand() {
        float d = 0.0f;
        for (HeatVisionBeam beam : frameBeams) d += 0.5f * beam.strength();
        return d;
    }

    @Override
    public String budgetGroup() {
        return "heat_vision";
    }

    @Override
    public void emit(EmissiveBuffer buffer, Vec3 camera, float partialTick) {
        QuadCollector quads = new QuadCollector(buffer);
        for (HeatVisionBeam beam : frameBeams) {
            HeatVisionGeometry.ribbon(beam, camera, segments, baseHalfWidth, minHalfAngle, CORE_WIDTH_SCALE,
                    CORE[0], CORE[1], CORE[2], quads);
            HeatVisionGeometry.hotspot(beam, camera, HOTSPOT_HALF_SIZE, minHalfAngle * 2.0,
                    HOTSPOT[0], HOTSPOT[1], HOTSPOT[2], quads);
        }
        frameBeams.clear();
    }

    /** Groups ribbon/hotspot vertices into flat-coloured emissive quads (strength scales the colour). */
    private static final class QuadCollector implements HeatVisionGeometry.VertexSink {
        private final EmissiveBuffer buffer;
        private final float[] v = new float[12];
        private int n;

        QuadCollector(EmissiveBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public void vertex(float x, float y, float z, float u, float vv, float r, float g, float b, float a) {
            v[n * 3] = x;
            v[n * 3 + 1] = y;
            v[n * 3 + 2] = z;
            if (++n < 4) return;
            n = 0;
            buffer.quad(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], r * a, g * a, b * a, 1.0f);
        }
    }
}
