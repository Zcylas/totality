package zcylas.totality.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.contributor.BlockDurabilityContributor;
import zcylas.totality.client.tooltip.contributor.MiningToolContributor;
import zcylas.totality.client.tooltip.contributor.TooltipContributor;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Block Breaking V2 Pass 2 in a real client with real registrations: the Tooltip V2 mining rows of the completed
 * conventional tools (Gold, Hoes), no conventional rows for specialized sources (Sword, Shears), no numeric Force
 * Tolerance even at SHIFT, and Hoe as an Effective Tool on block items. Results go to
 * {@code totality-block-breaking-pass2-results.txt}; the test fails at the end if any check failed.
 */
public class BlockBreakingPass2ClientGameTest implements FabricClientGameTest {

    private final List<String> results = new ArrayList<>();
    private int failures;

    private void check(String name, boolean ok, Object detail) {
        String line = (ok ? "PASS " : "FAIL ") + name + " — " + detail;
        results.add(line);
        System.out.println("[BlockBreakingPass2] " + line);
        if (!ok) failures++;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(this::checks);
        }
        context.runOnClient(mc -> {
            try {
                Files.write(mc.gameDirectory.toPath().resolve("totality-block-breaking-pass2-results.txt"), results);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
        if (failures > 0) throw new AssertionError(failures + " Block Breaking Pass 2 check(s) failed");
    }

    private static List<String> rows(Minecraft mc, TooltipContributor contributor, Item item, TooltipDisclosureLevel level) {
        ItemStack stack = new ItemStack(item);
        List<Component> vanilla = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
        TooltipContext ctx = TooltipContext.hover(stack, level, vanilla, Optional.empty(), mc.player, mc.level);
        List<String> out = new ArrayList<>();
        for (TooltipSection s : contributor.contribute(ctx)) {
            if (s instanceof TooltipSection.IconStatRow row && level.includes(row.minDisclosure())) out.add(row.label() + ": " + row.value());
        }
        return out;
    }

    private void checks(Minecraft mc) {
        MiningToolContributor tools = new MiningToolContributor();
        List<String> gold = rows(mc, tools, Items.GOLDEN_PICKAXE, TooltipDisclosureLevel.DEFAULT);
        check("gold_pickaxe.mining_rows", gold.contains("Mining Damage: 25") && gold.stream().anyMatch(r -> r.startsWith("Mining Speed: 3"))
                && gold.contains("Mining Tier: 1"), gold);
        List<String> goldHoe = rows(mc, tools, Items.GOLDEN_HOE, TooltipDisclosureLevel.DEFAULT);
        check("gold_hoe.mining_rows", goldHoe.equals(gold), goldHoe);
        List<String> ironHoe = rows(mc, tools, Items.IRON_HOE, TooltipDisclosureLevel.DEFAULT);
        check("iron_hoe.mining_rows", ironHoe.contains("Mining Damage: 50") && ironHoe.contains("Mining Tier: 3"), ironHoe);
        for (Item item : List.of(Items.GOLDEN_PICKAXE, Items.IRON_HOE, Items.NETHERITE_PICKAXE)) {
            List<String> details = rows(mc, tools, item, TooltipDisclosureLevel.DETAILS_AND_TECHNICAL);
            check("no_force_tolerance." + item, !details.isEmpty() && details.stream().noneMatch(r -> r.startsWith("Force Tolerance")), details);
        }
        for (Item item : List.of(Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.SHEARS)) {
            check("specialized_source_no_rows." + item, rows(mc, tools, item, TooltipDisclosureLevel.DETAILS).isEmpty(),
                    rows(mc, tools, item, TooltipDisclosureLevel.DETAILS));
        }
        BlockDurabilityContributor blocks = new BlockDurabilityContributor();
        List<String> hay = rows(mc, blocks, Items.HAY_BLOCK, TooltipDisclosureLevel.DEFAULT);
        check("hay_block.effective_tool_hoe", hay.contains("Effective Tool: Hoe") && hay.stream().anyMatch(r -> r.startsWith("Required Mining Tier")), hay);
        List<String> stone = rows(mc, blocks, Items.STONE, TooltipDisclosureLevel.DEFAULT);
        check("stone.unchanged", stone.equals(List.of("Block Durability: 100", "Required Mining Tier: 1", "Effective Tool: Pickaxe")), stone);
    }
}
