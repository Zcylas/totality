package zcylas.totality.api.rpg.skills.core;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class SkillsComponents {

    public static final ComponentKey<PlayerSkillsComponent> PLAYER_SKILLS = ComponentRegistry.getOrCreate(
            Identifier.fromNamespaceAndPath("totality", "player_skills"),
            PlayerSkillsComponent.class
    );

    private SkillsComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                PLAYER_SKILLS,
                PlayerSkillsComponent::new,
                RespawnStrategy.ALWAYS_COPY  // skills survive death
        );
        PlayerComponentEvents.registerClientComponent(
                PLAYER_SKILLS,
                () -> new PlayerSkillsComponent(null)
        );
    }

    public static PlayerSkillsComponent get(ServerPlayer player) {
        return PLAYER_SKILLS.get((ComponentProvider) player);
    }

    public static PlayerSkills getSkills(ServerPlayer player) {
        return get(player).getSkills();
    }
}