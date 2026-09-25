package zcylas.totality.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.renderer.gui.TotalityGuiGraphics;

@Environment(EnvType.CLIENT)
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin implements TotalityGuiGraphics {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GuiRenderState guiRenderState;
    @Shadow @Final public GuiGraphicsExtractor.ScissorStack scissorStack;

    @Shadow public abstract Matrix3x2fStack pose();

    /**
     * JEI z-order fix (playtest-correction pass, §18): the Totality tooltip panel used to be
     * painted immediately inside {@code AbstractContainerScreenMixin#onSetTooltip}, which runs
     * mid-screen-render — the same "main content" render stratum overlay mods like JEI's ingredient
     * panel paint into, so a later-drawn overlay could cover it. Vanilla's OWN tooltip never has
     * this problem because {@code setTooltipForNextFrame} only stores a {@code Runnable} and the
     * real draw happens later, from {@link #totality$flushDeferredTooltip}, in the same dedicated
     * topmost stratum {@code extractDeferredElements} already uses for vanilla's tooltip (see its
     * {@code nextStratum()} call — a screen's own render state is layered in strata, later strata
     * always drawn over earlier ones, regardless of insertion order within a stratum). Totality's
     * tooltip now uses that exact same mechanism instead of a parallel, earlier one, so it gets the
     * identical "always on top of ordinary screen content" guarantee vanilla tooltips already have.
     */
    @Unique
    private Runnable totality$deferredTooltip;

    @Override @Unique
    public void totality$deferTooltip(Runnable render) {
        this.totality$deferredTooltip = render;
    }

    /**
     * Mirrors vanilla's own {@code deferredTooltip} handling immediately above this injection
     * point in {@code extractDeferredElements}: one more {@code nextStratum()} call, then run and
     * clear. Vanilla's block and this one are mutually exclusive per hover target — Totality's
     * redirect in {@code AbstractContainerScreenMixin} never calls {@code setTooltipForNextFrame}
     * when it is about to show its own panel, so the two can never fire for the same tooltip.
     */
    @Inject(method = "extractDeferredElements", at = @At("TAIL"))
    private void totality$flushDeferredTooltip(int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (this.totality$deferredTooltip != null) {
            this.as().nextStratum();
            Runnable render = this.totality$deferredTooltip;
            this.totality$deferredTooltip = null;
            render.run();
        }
    }

    @Override @Unique
    public GuiGraphicsExtractor as() {
        return (GuiGraphicsExtractor)(Object) this;
    }

    @Override
    public Minecraft minecraft() { return minecraft; }

    @Override
    public Font font() { return minecraft.font; }

    @Override
    public GuiRenderState guiRenderState() { return guiRenderState; }

    @Override @Nullable
    public ScreenRectangle peekScissor() { return scissorStack.peek(); }

    @Override
    public void blit(RenderPipeline pipeline, Identifier texture,
                     int x0, int x1, int y0, int y1,
                     float u, float u2, float v, float v2,
                     int color) {
        innerBlit(pipeline, texture, x0, x1, y0, y1, u, u2, v, v2, color);
    }

    @Shadow
    private void innerBlit(RenderPipeline pipeline, Identifier location,
                           int x0, int x1, int y0, int y1,
                           float u, float u2, float v, float v2,
                           int color) {
        throw new UnsupportedOperationException("Shadowed by Mixin");
    }
}