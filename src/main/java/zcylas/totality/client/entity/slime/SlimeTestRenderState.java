package zcylas.totality.client.entity.slime;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** DEVELOPMENT TEST: the Small Slime test entity's body texture and body tint (ARGB; white = no tint). */
public class SlimeTestRenderState extends LivingEntityRenderState {
    public Identifier bodyTexture = SlimeTestRenderer.TEXTURE;
    public int bodyTint = -1;
    /** Body and eyes in one pass from {@link #bodyTexture} (false in tint mode: eyes drawn untinted by a layer). */
    public boolean singlePass = true;
}
