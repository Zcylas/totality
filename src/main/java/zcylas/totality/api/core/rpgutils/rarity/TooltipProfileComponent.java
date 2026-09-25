package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Optional, registration-authored tooltip <b>presentation</b> profile for an item.
 *
 * <p><b>Superseded as the eligibility gate (tooltip-eligibility review-fix pass):</b> the actual
 * decision of whether {@code TotalityTooltipRenderer} renders an item is now content-driven —
 * {@link zcylas.totality.client.tooltip.TotalityTooltipRenderer#isEligible} asks whether any
 * registered contributor has real content for the stack, never whether this marker (or
 * {@link ItemComponents#RARITY}) is present. This component is no longer consulted by that check
 * at all. It remains available as an explicit, narrow marker for anything that still wants one
 * (e.g. a future contributor keyed off it), but presence/absence no longer decides eligibility on
 * its own — an item with no rarity/classification/lore and no {@code TooltipProfileComponent}
 * still gets the Totality tooltip the moment any other contributor (Mining stats, Block
 * Durability, ...) has something to show for it.
 *
 * <p><b>Tooltip V2 presentation slice:</b> the "future variant field" this marker was reserved for.
 * It answers only <em>how</em> the tooltip is presented — never <em>what</em> the item's stats or
 * content are. Every field defaults to "not authored" ({@link #STANDARD}); the client resolver
 * falls back to AUTO inference / Totality defaults for anything left unauthored, so an item only
 * authors what it wants to change, e.g.
 * {@code TooltipProfileComponent.STANDARD.withPreview(TooltipPreviewMode.ITEM_MODEL)}. Switching an
 * item from {@code SPRITE} to {@code ITEM_MODEL} later is a one-line registration change — no
 * renderer or content change.
 *
 * <p>Persisted/synced like every other Totality item component. Stacks saved before this slice
 * carried an empty profile ({@code {}}), which still decodes to {@link #STANDARD}: every field is
 * optional with a default.
 *
 * @param previewMode      large header preview mode; {@code AUTO} = infer on the client
 * @param previewMotion    preview motion; {@code AUTO} = choose from the resolved preview mode
 * @param companionPreview opt-in companion card (e.g. {@code EQUIPPED_PLAYER} for armor)
 * @param vignetteStyle    explicit vignette geometry, or empty for the Totality default
 * @param dividerStyle     explicit header/body divider style, or empty for the Totality default
 * @param groupOrder       body groups (tooltip group ids) this item shows first, in this order — for an item whose
 *                         emphasis differs from its classification's default (e.g. a magical axe that leads with
 *                         Magic); empty = default ordering. Groups the item has no content for are ignored.
 */
public record TooltipProfileComponent(
        TooltipPreviewMode previewMode,
        TooltipPreviewMotion previewMotion,
        TooltipCompanionPreview companionPreview,
        Optional<TooltipVignetteStyle> vignetteStyle,
        Optional<TooltipDividerStyle> dividerStyle,
        List<Identifier> groupOrder
) {

    public TooltipProfileComponent {
        groupOrder = List.copyOf(groupOrder);
    }

    /** Nothing authored: every presentation choice is left to AUTO inference / Totality defaults. */
    public static final TooltipProfileComponent STANDARD = new TooltipProfileComponent(
            TooltipPreviewMode.AUTO, TooltipPreviewMotion.AUTO, TooltipCompanionPreview.NONE,
            Optional.empty(), Optional.empty(), List.of());

    public static final Codec<TooltipProfileComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            TooltipPreviewMode.CODEC.optionalFieldOf("preview", TooltipPreviewMode.AUTO)
                    .forGetter(TooltipProfileComponent::previewMode),
            TooltipPreviewMotion.CODEC.optionalFieldOf("preview_motion", TooltipPreviewMotion.AUTO)
                    .forGetter(TooltipProfileComponent::previewMotion),
            TooltipCompanionPreview.CODEC.optionalFieldOf("companion_preview", TooltipCompanionPreview.NONE)
                    .forGetter(TooltipProfileComponent::companionPreview),
            TooltipVignetteStyle.CODEC.optionalFieldOf("vignette")
                    .forGetter(TooltipProfileComponent::vignetteStyle),
            TooltipDividerStyle.CODEC.optionalFieldOf("divider")
                    .forGetter(TooltipProfileComponent::dividerStyle),
            Identifier.CODEC.listOf().optionalFieldOf("group_order", List.of())
                    .forGetter(TooltipProfileComponent::groupOrder)
    ).apply(i, TooltipProfileComponent::new));

    public static final StreamCodec<ByteBuf, TooltipProfileComponent> STREAM_CODEC = StreamCodec.composite(
            TooltipPreviewMode.STREAM_CODEC, TooltipProfileComponent::previewMode,
            TooltipPreviewMotion.STREAM_CODEC, TooltipProfileComponent::previewMotion,
            TooltipCompanionPreview.STREAM_CODEC, TooltipProfileComponent::companionPreview,
            ByteBufCodecs.optional(TooltipVignetteStyle.STREAM_CODEC), TooltipProfileComponent::vignetteStyle,
            ByteBufCodecs.optional(TooltipDividerStyle.STREAM_CODEC), TooltipProfileComponent::dividerStyle,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), TooltipProfileComponent::groupOrder,
            TooltipProfileComponent::new);

    public TooltipProfileComponent withPreview(TooltipPreviewMode mode) {
        return new TooltipProfileComponent(mode, previewMotion, companionPreview, vignetteStyle, dividerStyle, groupOrder);
    }

    public TooltipProfileComponent withMotion(TooltipPreviewMotion motion) {
        return new TooltipProfileComponent(previewMode, motion, companionPreview, vignetteStyle, dividerStyle, groupOrder);
    }

    public TooltipProfileComponent withCompanion(TooltipCompanionPreview companion) {
        return new TooltipProfileComponent(previewMode, previewMotion, companion, vignetteStyle, dividerStyle, groupOrder);
    }

    public TooltipProfileComponent withVignette(TooltipVignetteStyle style) {
        return new TooltipProfileComponent(previewMode, previewMotion, companionPreview, Optional.of(style), dividerStyle, groupOrder);
    }

    public TooltipProfileComponent withDivider(TooltipDividerStyle style) {
        return new TooltipProfileComponent(previewMode, previewMotion, companionPreview, vignetteStyle, Optional.of(style), groupOrder);
    }

    /** Body groups to show first, in this order (tooltip group ids, e.g. {@code totality:magic}). */
    public TooltipProfileComponent withGroupOrder(Identifier... groups) {
        return new TooltipProfileComponent(previewMode, previewMotion, companionPreview, vignetteStyle, dividerStyle, List.of(groups));
    }
}
