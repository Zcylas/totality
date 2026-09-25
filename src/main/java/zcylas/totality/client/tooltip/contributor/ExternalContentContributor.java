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
 * "Advanced Tooltips" (F3+H) debug lines described below, and the exact vanilla enchantment lines the
 * ENCHANTMENTS group now represents (see {@link #representedEnchantmentLines}), are filtered — everything
 * else survives unfiltered, including dyed-item info, trim info, attribute modifiers, and any other mod's
 * appended lines. No broad filtering by color, indentation, or translation-key guessing is performed here.
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

    private static final String DURABILITY_TRANSLATION_KEY = "item.durability";

    @Override
    public TooltipSectionGroup sectionGroup() {
        return TooltipSectionGroup.EXTERNAL;
    }

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        if (ctx.originalLines().isEmpty()) return List.of();

        String title = ctx.stack().getHoverName().getString();
        String registryId = BuiltInRegistries.ITEM.getKey(ctx.stack().getItem()).toString();
        List<Component> representedEnchantments = representedEnchantmentLines(ctx);

        List<Component> preserved = new ArrayList<>();
        for (Component line : ctx.originalLines()) {
            if (line.getString().equals(title)) continue;
            if (isRegistryIdLine(line, registryId)) continue;
            if (isComponentCountLine(line)) continue;
            if (isDurabilityLine(line)) continue;
            if (representedEnchantments.remove(line)) continue;
            preserved.add(line);
        }
        if (preserved.isEmpty()) return List.of();

        return List.of(new TooltipSection.ExternalContent(preserved)
                .withVisibility(TooltipVisibility.WHEN_IDENTIFIED));
    }

    /**
     * True only for vanilla's advanced-tooltip {@code Component.translatable("item.durability", remaining, max)} —
     * recognized by translation key, language-independently. The DURABILITY section already shows those exact
     * figures, so the preserved vanilla line would duplicate them.
     */
    private static boolean isDurabilityLine(Component line) {
        return line.getContents() instanceof TranslatableContents tc
                && DURABILITY_TRANSLATION_KEY.equals(tc.getKey());
    }

    /**
     * The exact components vanilla prints for the stack's enchantments ({@code Enchantment#getFullname}), which the
     * ENCHANTMENTS group ({@link EnchantmentsContributor}) now represents. A raw line is dropped only when it is
     * {@link Component#equals equal} to one of them — same translation key, level, style (gray, or red for a curse) —
     * and each represented enchantment removes at most one line. Anything that does not match exactly (another mod's
     * rephrased line, unrelated text) is preserved.
     */
    private static List<Component> representedEnchantmentLines(TooltipContext ctx) {
        List<Component> lines = new ArrayList<>();
        for (TooltipEnchantments.Entry entry : TooltipEnchantments.of(ctx.stack(),
                ctx.level() == null ? null : ctx.level().registryAccess())) {
            lines.add(entry.vanillaLine());
        }
        return lines;
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
