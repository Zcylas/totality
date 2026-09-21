package zcylas.totality.client.tooltip.contributor;

import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.industrial.energy.UEFormat;
import zcylas.totality.api.industrial.energy.UEItem;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.TotalityIcons;
import zcylas.totality.client.tooltip.section.TooltipSection;
import zcylas.totality.item.energy.BatteryItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Replaces the energy half of the old hardcoded {@code TooltipStatBlock}. Discovers
 * applicability purely through the shared {@code UEItem} capability — any current or future
 * item implementing it (batteries, energy cells, fluid tanks with energy, etc.) gets this
 * contributor automatically, with no per-item tooltip code and no per-tier registration needed
 * in the renderer.
 *
 * Default shows compact figures ({@code UEFormat.energy}); Details shows exact figures — the
 * same toggle the old renderer performed with a raw GLFW Shift poll, now driven by the shared
 * {@link TooltipDisclosureLevel} resolved once per frame.
 */
public final class EnergyContributor implements TooltipContributor {

    private static final int CYAN = 0xFF42C8F5;
    private static final int GRAY = 0xFF888888;

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        ItemStack stack = ctx.stack();
        if (!(stack.getItem() instanceof UEItem ue)) return List.of();

        long stored = ue.getStoredEnergy(stack);
        long cap = ue.getEnergyCapacity(stack);
        long maxIn = ue.getEnergyMaxInput(stack);
        long maxOut = ue.getEnergyMaxOutput(stack);
        int pct = cap > 0 ? (int) ((stored * 100L) / cap) : 0;
        boolean exact = ctx.disclosure().atLeast(TooltipDisclosureLevel.DETAILS);

        String energyVal = exact
                ? stored + " / " + cap + " UE (" + pct + "%)"
                : UEFormat.energy(stored) + " / " + UEFormat.energy(cap) + " UE (" + pct + "%)";
        String ioVal = exact
                ? maxIn + " / " + maxOut + " UE/t"
                : UEFormat.energy(maxIn) + " / " + UEFormat.energy(maxOut) + " UE/t";

        List<TooltipSection> sections = new ArrayList<>();
        sections.add(new TooltipSection.ProgressBar(cap > 0 ? (float) stored / cap : 0f, ue.getEnergyBarColor(stack)));
        sections.add(new TooltipSection.StatBlock(List.of(
                new TooltipSection.StatLine(TotalityIcons.ENERGY, CYAN, "Energy", energyVal, CYAN),
                new TooltipSection.StatLine(TotalityIcons.ENERGY, CYAN, "I/O Rate", ioVal, GRAY)
        )));

        if (stack.getItem() instanceof BatteryItem) {
            boolean active = BatteryItem.isActive(stack);
            sections.add(new TooltipSection.Requirement(active ? "Active" : "Inactive", !active));
        }

        return sections;
    }

    /**
     * Energy always has an exact-figure Details view (see {@code exact} above) — declared
     * unconditionally here, independent of the currently-selected disclosure level, so the
     * footer can correctly show "SHIFT: Details" at Default view. {@link #contribute} itself
     * cannot be used to infer this: the Energy row is present at every level (only its *text*
     * changes), so it never appears with an explicit Details-only marker for the inference the
     * old footer logic used to attempt.
     */
    @Override
    public Set<TooltipDisclosureLevel> availableDisclosureLevels(TooltipContext ctx) {
        if (!(ctx.stack().getItem() instanceof UEItem)) return Set.of();
        return Set.of(TooltipDisclosureLevel.DETAILS);
    }
}
