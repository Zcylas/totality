package zcylas.totality.client.renderer.entity.state.basicweapon;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class ThrownShurikenRenderState extends EntityRenderState {
    public final ItemStackRenderState item = new ItemStackRenderState();
    public float spin;
    public boolean inGround;
}