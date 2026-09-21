package zcylas.totality.item.soulgem;

import net.minecraft.world.item.Item;
import zcylas.totality.api.soulgem.SoulGemAcceptanceRule;

/**
 * The single reusable vessel item class for every ordinary Soul Gem tier (Petty, Common, and every
 * future tier). A gem's tier identity is entirely its registered {@link #acceptanceRule} plus its
 * normal item metadata (rarity/model/lore) — there is no {@code PettySoulGemItem}/{@code
 * CommonSoulGemItem} subclass.
 *
 * <p>State lives entirely in {@code CapturedSoulComponent.CAPTURED_SOUL}'s presence/absence on the
 * stack: EMPTY when absent, FILLED when present. There is no separate {@code _filled} item id and no
 * per-rank/per-soul filled variant. See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §11.
 */
public class SoulGemItem extends Item {

    private final SoulGemAcceptanceRule acceptanceRule;

    public SoulGemItem(Properties properties, SoulGemAcceptanceRule acceptanceRule) {
        super(properties);
        this.acceptanceRule = acceptanceRule;
    }

    public SoulGemAcceptanceRule acceptanceRule() { return acceptanceRule; }
}
