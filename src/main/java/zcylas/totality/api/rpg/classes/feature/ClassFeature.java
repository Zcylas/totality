package zcylas.totality.api.rpg.classes.feature;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public abstract class ClassFeature {

    private final Identifier id;
    private final String displayName;
    private final String description;

    protected ClassFeature(Identifier id, String displayName, String description) {
        this.id          = id;
        this.displayName = displayName;
        this.description = description;
    }

    public Identifier getId()        { return id; }
    public String getDisplayName()   { return displayName; }
    public String getDescription()   { return description; }

    /** Called when the player gains this feature (level up or join). */
    public void onGain(ServerPlayer player) {}

    /** Called when the player loses this feature (rare — class reset). */
    public void onLose(ServerPlayer player) {}

    /** Called every server tick while the player has this feature. */
    public void onTick(ServerPlayer player) {}
}