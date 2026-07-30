package zcylas.totality.client.tooltip.contributor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipSectionGroup;
import zcylas.totality.client.tooltip.TooltipVisibility;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Surfaces whatever vanilla/third-party tooltip lines the mixin captured before redirecting to
 * the custom renderer. The old renderer discarded this list entirely and silently; this
 * contributor is what makes that no longer happen. Only the exact duplicate of the item's own
 * display name (already drawn by the Header section), and the two recognized vanilla
 * "Advanced Tooltips" (F3+H) debug lines described below, are filtered — everything else survives
 * unfiltered, including enchantment lines, dyed-item info, trim info, attribute modifiers, and
 * any other mod's appended lines. No broad filtering by color, indentation, or translation-key
 * guessing is performed here.
 *
 * <p><b>Advanced-tooltip line ownership (visual-correction pass, Finding 3; made
 * language-independent in the micro-correction):</b> when Minecraft's own Advanced Tooltips
 * setting is on, {@code ItemStack.addDetailsToTooltip} (confirmed by decompiling
 * {@code minecraft-merged.jar} for MC 26.2) appends two exact, reliably-identifiable debug lines
 * to every item's tooltip:
 * <ul>
 *   <li>The item's registry id, built as {@code Component.literal(id.toString())} — never
 *       translated, so an exact string match against {@link BuiltInRegistries#ITEM}'s own key for
 *       this stack is already language-independent by construction. No change needed here.</li>
 *   <li>A component count, built as {@code Component.translatable("item.components", count)} —
 *       this pass previously recognized it with an English-only regex
 *       ({@code ^\d+ component\(s\)$} against the vanilla-locale rendered string), which would
 *       have missed it entirely under any other client language. It is now recognized by
 *       inspecting the {@link Component}'s own {@link Component#getContents()} for a
 *       {@link TranslatableContents} whose {@link TranslatableContents#getKey()} equals the
 *       literal translation key {@code "item.components"} — the exact semantic property vanilla
 *       itself used to build the line, not a guess based on its rendered text, color, or
 *       indentation, and correct in every client language without a list of per-language
 *       regexes.</li>
 * </ul>
 * Matched lines are dropped from this contributor's preserved-external output rather than being
 * duplicated into a second {@link TooltipSection.TechnicalInfo} block, because
 * {@link TechnicalInfoContributor} already surfaces the exact same two facts, gated the same way
 * (Ctrl-only), in its own clearer "Registry: ..." / "Components: N" format — re-adding the raw
 * vanilla strings here would only produce a duplicate, noisier line under Ctrl. Every other,
 * unrecognized line — including anything from another mod — is preserved exactly as before.
 */
public final class ExternalContentContributor implements TooltipContributor {

    private static final String COMPONENT_COUNT_TRANSLATION_KEY = "item.components";

    @Override
    public TooltipSectionGroup sectionGroup() {
        return TooltipSectionGroup.EXTERNAL;
    }

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        if (ctx.originalLines().isEmpty()) return List.of();

        String title = ctx.stack().getHoverName().getString();
        String registryId = BuiltInRegistries.ITEM.getKey(ctx.stack().getItem()).toString();

        List<Component> preserved = new ArrayList<>();
        for (Component line : ctx.originalLines()) {
            if (line.getString().equals(title)) continue;
            if (isRegistryIdLine(line, registryId)) continue;
            if (isComponentCountLine(line)) continue;
            preserved.add(line);
        }
        if (preserved.isEmpty()) return List.of();

        return List.of(new TooltipSection.ExternalContent(preserved)
                .withVisibility(TooltipVisibility.WHEN_IDENTIFIED));
    }

    /** True only for an exact registry-id string match — this line is never translated by vanilla. */
    private static boolean isRegistryIdLine(Component line, String registryId) {
        return line.getString().equals(registryId);
    }

    /**
     * True only when the component's own content is exactly vanilla's
     * {@code Component.translatable("item.components", count)} — recognized by translation key,
     * not by rendered text, so this is correct regardless of the client's language.
     */
    private static boolean isComponentCountLine(Component line) {
        return line.getContents() instanceof TranslatableContents tc
                && COMPONENT_COUNT_TRANSLATION_KEY.equals(tc.getKey());
    }
}
