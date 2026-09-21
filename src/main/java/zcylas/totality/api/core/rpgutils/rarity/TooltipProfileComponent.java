package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Explicit, registration-authored opt-in into Totality's custom tooltip presentation.
 *
 * Presence of this component on an {@code ItemStack} is the <em>intended</em> long-term gate —
 * not rarity, not a gameplay capability interface such as {@code UEItem}/{@code TotalityWeaponItem},
 * not namespace. An item may carry this marker with no rarity/classification/lore at all; the
 * renderer must fall back to a neutral profile theme in that case rather than inventing rarity or
 * classification data.
 *
 * In practice, {@link ItemComponents#hasTooltipPresentation(net.minecraft.world.item.ItemStack)}
 * — the actual eligibility check the mixin calls — also still honors a documented, temporary
 * compatibility fallback: an item that carries a bare {@link ItemComponents#RARITY} component but
 * no explicit {@code TooltipProfileComponent} is still treated as opted-in, so the many
 * pre-existing rarity-bearing registrations this foundation pass did not hand-migrate keep
 * rendering. New registrations should set this component explicitly rather than relying on that
 * fallback; see {@code ItemComponents#hasTooltipPresentation} for the exact rule.
 *
 * A marker record today (no payload) so it stays the narrowest possible signal; a future variant
 * field (e.g. a named profile other than "standard") can be added later without breaking the
 * opt-in contract.
 */
public record TooltipProfileComponent() {

    public static final TooltipProfileComponent STANDARD = new TooltipProfileComponent();

    public static final Codec<TooltipProfileComponent> CODEC =
            MapCodec.unit(STANDARD).codec();

    public static final StreamCodec<ByteBuf, TooltipProfileComponent> STREAM_CODEC =
            StreamCodec.unit(STANDARD);
}
