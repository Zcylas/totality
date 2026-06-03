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
import zcylas.totality.client.renderer.gui.TotalityGuiGraphics;

@Environment(EnvType.CLIENT)
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin implements TotalityGuiGraphics {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GuiRenderState guiRenderState;
    @Shadow @Final public GuiGraphicsExtractor.ScissorStack scissorStack;

    @Shadow public abstract Matrix3x2fStack pose();

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