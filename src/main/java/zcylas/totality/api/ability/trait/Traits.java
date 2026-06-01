package zcylas.totality.api.ability.trait;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Factory class for all built-in reusable traits.
 *
 * Usage:
 *   Traits.noFallDamage()
 *   Traits.knockbackResistance(1.0)
 *   Traits.attackDamage(4.0)
 *   Traits.armor(4.0, 3.0)
 */
public final class Traits {

    private Traits() {}

    // ── No fall damage ────────────────────────────────────────────────────────

    public static Trait noFallDamage() {
        return new Trait() {
            @Override
            public void apply(ServerPlayer player) {
                player.fallDistance = 0f;
            }
            @Override
            public void remove(ServerPlayer player) {
                // Nothing to clean up — fallDistance resets naturally
            }
        };
    }

    // ── Attribute modifier traits ─────────────────────────────────────────────

    public static Trait knockbackResistance(String modId, double amount) {
        return attributeTrait(
                Identifier.fromNamespaceAndPath("totality", modId),
                Attributes.KNOCKBACK_RESISTANCE,
                amount,
                AttributeModifier.Operation.ADD_VALUE
        );
    }

    public static Trait attackDamage(String modId, double amount) {
        return attributeTrait(
                Identifier.fromNamespaceAndPath("totality", modId),
                Attributes.ATTACK_DAMAGE,
                amount,
                AttributeModifier.Operation.ADD_VALUE
        );
    }

    public static Trait armor(String modId, double armorAmount, double toughnessAmount) {
        Trait armorTrait = attributeTrait(
                Identifier.fromNamespaceAndPath("totality", modId + "_armor"),
                Attributes.ARMOR,
                armorAmount,
                AttributeModifier.Operation.ADD_VALUE
        );
        Trait toughnessTrait = attributeTrait(
                Identifier.fromNamespaceAndPath("totality", modId + "_toughness"),
                Attributes.ARMOR_TOUGHNESS,
                toughnessAmount,
                AttributeModifier.Operation.ADD_VALUE
        );
        // Combine into one trait
        return new Trait() {
            @Override
            public void apply(ServerPlayer player) {
                armorTrait.apply(player);
                toughnessTrait.apply(player);
            }
            @Override
            public void remove(ServerPlayer player) {
                armorTrait.remove(player);
                toughnessTrait.remove(player);
            }
        };
    }

    // ── Generic attribute trait ───────────────────────────────────────────────

    public static Trait attributeTrait(Identifier modId,
                                       Holder<Attribute> attribute,
                                       double amount,
                                       AttributeModifier.Operation operation) {
        return new Trait() {
            @Override
            public void apply(ServerPlayer player) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    instance.addOrUpdateTransientModifier(
                            new AttributeModifier(modId, amount, operation));
                }
            }
            @Override
            public void remove(ServerPlayer player) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) instance.removeModifier(modId);
            }
        };
    }
}