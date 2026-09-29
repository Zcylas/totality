package zcylas.totality.client.renderer.entity.skateboard;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** What the skateboard renderer draws: the board's own visual pose, independent of any rider. */
public class SkateboardRenderState extends EntityRenderState {
    public float yRot;
    /** Deck lean into a turn (degrees, positive to the right). */
    public float lean;
    /** Wheel hubs' turn (radians). */
    public float wheelSpin;
    public float hurtTime;
    public int hurtDir;
    public float damageTime;
}
