package zcylas.totality.client.tooltip.contributor;

import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.industrial.energy.UEItem;
import zcylas.totality.client.tooltip.group.TooltipGroup;
import zcylas.totality.client.tooltip.group.TooltipGroups;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.TotalityIcons;
import zcylas.totality.client.tooltip.renderer.TooltipResourceColors;
import zcylas.totality.client.tooltip.section.TooltipSection;
import zcylas.totality.item.energy.BatteryItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The ENERGY resource section. Discovers applicability purely through the shared {@code UEItem} capability —
 * any current or future item implementing it gets this section automatically, with no per-item tooltip code.
 *
 * <p>Under the Energy group heading: the charge bar (UE blue, darkening as it depletes — never orange/red) and
 * the centred figures {@code current / max UE (pct)} — compact by default, exact with SHIFT. The I/O rates follow
 * directly beneath the figures, revealed with SHIFT unless the item authors them as always-visible
 * ({@link UEItem#showsEnergyRatesByDefault}); a battery's Active/Inactive state follows last. Only real UE data is shown — an item without the capability
 * gets no Energy section.
 */
public final class EnergyContributor implements TooltipContributor {

    private static final int GRAY = 0xFF888888;

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        ItemStack stack = ctx.stack();
        if (!(stack.getItem() instanceof UEItem ue)) return List.of();

        Boolean batteryActive = stack.getItem() instanceof BatteryItem ? BatteryItem.isActive(stack) : null;
        return sections(ue.getStoredEnergy(stack), ue.getEnergyCapacity(stack), ue.getEnergyMaxInput(stack),
                ue.getEnergyMaxOutput(stack), ue.showsEnergyRatesByDefault(), batteryActive, ctx.disclosure());
    }

    /**
     * The Energy section's content, in order: gauge (bar + figures), I/O rates (SHIFT-gated unless
     * {@code ratesByDefault}), then the battery's Active/Inactive status when {@code batteryActive} is non-null.
     * Pure — unit-tested.
     */
    static List<TooltipSection> sections(long stored, long cap, long maxIn, long maxOut, boolean ratesByDefault,
                                         Boolean batteryActive, TooltipDisclosureLevel disclosure) {
        boolean exact = disclosure.includes(TooltipDisclosureLevel.DETAILS);
        float fraction = ResourceFormat.fraction(stored, cap);

        List<TooltipSection> sections = new ArrayList<>();
        sections.add(new TooltipSection.ResourceGauge(stored, cap, TooltipResourceColors.energy(fraction),
                ResourceFormat.figures(stored, cap, "UE", exact), TooltipResourceColors.UE_BLUE));

        String ioVal = exact
                ? maxIn + " / " + maxOut + " UE/t"
                : ResourceFormat.compact(maxIn) + " / " + ResourceFormat.compact(maxOut) + " UE/t";
        TooltipSection.StatRow io = new TooltipSection.StatRow(TotalityIcons.ENERGY, TooltipResourceColors.UE_BLUE,
                "I/O Rate", ioVal, GRAY);
        sections.add(ratesByDefault ? io : io.withMinDisclosure(TooltipDisclosureLevel.DETAILS));

        if (batteryActive != null) {
            sections.add(new TooltipSection.Requirement(batteryActive ? "Active" : "Inactive", !batteryActive));
        }

        return sections;
    }

    /**
     * Energy always has a SHIFT view — exact figures (and, unless authored as always-visible, the I/O rates) — declared
     * unconditionally here, independent of the currently-selected disclosure level, so the SHIFT panel is offered
     * at the default view.
     */
    @Override
    public Set<TooltipDisclosureLevel> availableDisclosureLevels(TooltipContext ctx) {
        if (!(ctx.stack().getItem() instanceof UEItem)) return Set.of();
        return Set.of(TooltipDisclosureLevel.DETAILS);
    }

    @Override
    public TooltipGroup bodyGroup(TooltipContext ctx) {
        return TooltipGroups.ENERGY;
    }
}
