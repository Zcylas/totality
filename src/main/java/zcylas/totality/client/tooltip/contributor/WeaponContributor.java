package zcylas.totality.client.tooltip.contributor;

import zcylas.totality.api.rpg.combat.weapon.TotalityWeaponItem;
import zcylas.totality.api.rpg.combat.weapon.WeaponType;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TotalityIcons;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Migrated from the old hardcoded {@code TooltipWeaponBlock} call inside the renderer. Still
 * discovers applicability via {@code instanceof TotalityWeaponItem} — that capability check now
 * lives here, inside a contributor, rather than being baked into the renderer's draw sequence.
 *
 * A later vanilla-weapon adapter (consuming {@code WeaponDataResolver}/{@code VanillaWeaponStats},
 * which already implement the "TotalityWeaponItem-first, vanilla-map-fallback" pattern for
 * combat math) can be added as its own contributor without touching this class or the renderer —
 * that migration is intentionally deferred past this pass.
 *
 * Preserves the same information the old {@code TooltipWeaponBlock} showed (dice expression,
 * damage type, STR/DEX or Finesse, weapon properties, category, range, stamina cost) through the
 * new generic section primitives; the old widget's boxed "dice die" graphic is simplified to a
 * plain stat row as part of moving off bespoke per-block pixel drawing.
 */
public final class WeaponContributor implements TooltipContributor {

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        if (!(ctx.stack().getItem() instanceof TotalityWeaponItem weapon)) return List.of();

        List<TooltipSection> sections = new ArrayList<>();
        sections.add(new TooltipSection.Heading("Weapon"));

        String dice = weapon.getDiceCount() + weapon.getDamageDie().getLabel();
        String damageType = capitalize(weapon.getDamageType().getId().getPath());
        String ability = weapon.isFinesse() ? "STR or DEX" : weapon.getDefaultAbilityScore().name();
        sections.add(new TooltipSection.StatRow(TotalityIcons.DAMAGE, 0xFFE08060, "Damage",
                dice + " " + damageType + " (" + ability + ")", 0xFFE08060));

        List<String> properties = new ArrayList<>();
        if (weapon.isFinesse()) properties.add("Finesse");
        if (weapon.getWeaponType() == WeaponType.THROWN) properties.add("Thrown");
        if (weapon.isLight()) properties.add("Light");
        if (weapon.isHeavy()) properties.add("Heavy");
        if (weapon.isReach()) properties.add("Reach");
        if (weapon.isVersatile()) properties.add("Versatile");
        if (!properties.isEmpty()) sections.add(new TooltipSection.PropertyBadges(properties));

        sections.add(new TooltipSection.StatRow(null, 0xFF6B7280, "Category",
                weapon.getWeaponCategory().displayName(), 0xFF6B7280));

        int[] range = weapon.getThrowRange();
        if (range != null) {
            sections.add(new TooltipSection.StatRow(TotalityIcons.RANGE, 0xFF42A5F5, "Range",
                    range[0] + " / " + range[1] + " ft.", 0xFF42A5F5));
        }

        int cost = weapon.getWeaponType() == WeaponType.THROWN
                ? weapon.getThrownAttackCost() : weapon.getNormalAttackCost();
        sections.add(new TooltipSection.StatRow(TotalityIcons.STAMINA, 0xFF66BB6A, "Stamina Cost",
                String.valueOf(cost), 0xFF66BB6A));

        return sections;
    }

    private static String capitalize(String path) {
        if (path.isEmpty()) return path;
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
}
