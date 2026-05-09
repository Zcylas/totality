package zcylas.totality.api.rpg.skills.core;

/**
 * Client-side cache of the local player's skill data.
 * Populated when a sync packet is received from the server.
 * Used by HUD renderers and GUI screens.
 */
public final class ClientSkillsManager {

    private static final int[] levels = new int[Skill.values().length];
    private static final int[] xp     = new int[Skill.values().length];

    static {
        for (int i = 0; i < levels.length; i++) {
            levels[i] = 1;
            xp[i] = 0;
        }
    }

    private ClientSkillsManager() {}

    public static void apply(PlayerSkills skills) {
        Skill[] skillValues = Skill.values();
        for (int i = 0; i < skillValues.length; i++) {
            levels[i] = skills.getLevel(skillValues[i]);
            xp[i]     = skills.getXp(skillValues[i]);
        }
    }

    public static int getLevel(Skill skill) {
        return levels[skill.ordinal()];
    }

    public static int getXp(Skill skill) {
        return xp[skill.ordinal()];
    }

    public static int getXpRequired(Skill skill) {
        return skill.getXpRequired(getLevel(skill));
    }
}