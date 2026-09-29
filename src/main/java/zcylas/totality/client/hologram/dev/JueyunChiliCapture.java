package zcylas.totality.client.hologram.dev;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceTarget;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.init.blocks.AlchemyBlocks;
import zcylas.totality.init.items.SKIngredientItems;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static zcylas.totality.api.core.util.MountainFlowerBushBlock.HARVESTED;

/**
 * Creative Test I footage for the opt-in capture run (inert in normal play): scene 60, the Jueyun Chili Plant and
 * Jueyun Chili ({@code JC_}). World shots use a spectator camera with the HUD hidden (both states side by side, one
 * plant from every side, a wild patch in day and golden-hour light); interactions are the local survival player's real
 * controls (use / attack keys, inventory), with the integrated server's resulting state — and the client's copy of
 * it — logged as PASS/FAIL lines beside the frames.
 */
final class JueyunChiliCapture {

    private JueyunChiliCapture() {}

    private static final double EYE = 1.62;
    private static final String FRUITING = "totality:jueyun_chili_plant[harvested=false]";
    private static final String PICKED = "totality:jueyun_chili_plant[harvested=true]";

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        s.add(HologramCapture.command("gamerule random_tick_speed 0"));
        s.add(HologramCapture.command("gamerule spawn_mobs false"));
        s.add(HologramCapture.command("gamerule send_command_feedback false"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("weather clear"));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(hud(false));
        s.add(rememberStage(stage));
        for (int[] z : new int[][]{{-12, 3}, {4, 19}, {20, 30}}) {
            s.add(here(stage, String.format(Locale.ROOT, "fill ~-16 ~ ~%d ~16 ~12 ~%d minecraft:air", z[0], z[1])));
            s.add(here(stage, String.format(Locale.ROOT, "fill ~-16 ~13 ~%d ~16 ~24 ~%d minecraft:air", z[0], z[1])));
            s.add(here(stage, String.format(Locale.ROOT, "fill ~-16 ~-3 ~%d ~16 ~-1 ~%d minecraft:grass_block", z[0], z[1])));
            s.add(here(stage, String.format(Locale.ROOT, "fillbiome ~-16 ~-3 ~%d ~16 ~12 ~%d minecraft:plains", z[0], z[1])));
        }
        s.add(here(stage, "kill @e[type=!minecraft:player,distance=..60]"));
        // The pair (z + 5): fruiting at x - 1, picked at x + 1. One plant alone (z + 10) for the all-sides views.
        s.add(here(stage, "setblock ~-1 ~ ~5 " + FRUITING));
        s.add(here(stage, "setblock ~1 ~ ~5 " + PICKED));
        s.add(here(stage, "setblock ~ ~ ~10 " + FRUITING));
        // A wild patch (z + 16..22) among grass, ferns and a few flowers; mostly fruiting, some picked.
        int[][] patch = {{-6, 17, 0}, {-4, 19, 0}, {-3, 16, 1}, {-1, 18, 0}, {1, 17, 0}, {2, 20, 1}, {4, 18, 0}, {6, 16, 0},
                {-5, 21, 1}, {0, 21, 0}, {5, 21, 0}, {-2, 22, 0}};
        for (int[] p : patch) {
            s.add(here(stage, String.format(Locale.ROOT, "setblock ~%d ~ ~%d %s", p[0], p[1], p[2] == 0 ? FRUITING : PICKED)));
        }
        String[] filler = {"short_grass", "fern", "short_grass", "poppy", "short_grass", "fern", "dandelion", "short_grass"};
        int[][] fillerAt = {{-7, 18}, {-5, 16}, {-2, 17}, {0, 19}, {3, 16}, {3, 19}, {5, 17}, {7, 19}, {-4, 22}, {1, 22}, {6, 22}, {-6, 20}};
        for (int i = 0; i < fillerAt.length; i++) {
            s.add(here(stage, String.format(Locale.ROOT, "setblock ~%d ~ ~%d minecraft:%s", fillerAt[i][0], fillerAt[i][1], filler[i % filler.length])));
        }
        s.add(HologramCapture.waitTicks(40));

        view(s, stage, "JC_01_states_side_by_side", 0, 1.25, 2.4, 0, 0.45, 5);
        view(s, stage, "JC_02_states_close_low", 0, 0.75, 3.3, 0, 0.45, 5);
        view(s, stage, "JC_03_fruiting_close", -1, 0.9, 3.6, -1, 0.5, 5);
        view(s, stage, "JC_04_picked_close", 1, 0.9, 3.6, 1, 0.5, 5);
        view(s, stage, "JC_05_states_from_above", 0, 2.6, 3.6, 0, 0.2, 5);
        // One plant from every side (plant block at z + 10; its centre is (0, 0.5, 10) from the stage).
        view(s, stage, "JC_06_single_from_north", 0, 1.0, 7.6, 0, 0.5, 10);
        view(s, stage, "JC_07_single_from_northeast", 1.8, 1.0, 8.2, 0, 0.5, 10);
        view(s, stage, "JC_08_single_from_east", 2.4, 1.0, 10, 0, 0.5, 10);
        view(s, stage, "JC_09_single_from_south", 0, 1.0, 12.4, 0, 0.5, 10);
        view(s, stage, "JC_10_single_from_west", -2.4, 1.0, 10, 0, 0.5, 10);
        view(s, stage, "JC_11_single_from_southwest", -1.8, 1.0, 11.8, 0, 0.5, 10);
        view(s, stage, "JC_12_single_top_down", 0.05, 2.6, 10.3, 0, 0.3, 10);
        view(s, stage, "JC_13_single_ground_contact", 0, 0.35, 8.4, 0, 0.25, 10);
        view(s, stage, "JC_14_wild_patch", 0, 2.4, 11.5, 0, 0.3, 19);
        view(s, stage, "JC_15_wild_patch_eye_level", -1, 1.62, 13.5, 0, 0.5, 19);
        s.add(HologramCapture.command("time set 12600"));
        view(s, stage, "JC_16_wild_patch_golden_hour", 5, 1.8, 13.0, -1, 0.4, 19);
        view(s, stage, "JC_17_states_golden_hour", 0, 1.1, 2.8, 0, 0.45, 5);
        s.add(HologramCapture.command("time set 6000"));

        // ── Survival: place, bone meal, harvest, harvest again, break, eat ─────────────────────────────────────
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("item replace entity @s hotbar.0 with totality:jueyun_chili_plant 4"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with minecraft:bone_meal 16"));
        s.add(hud(true));
        s.add(camera(stage, 1.5, EYE, -0.2, 0, 0, 2.5));
        s.add(slot(0));
        s.add(HologramCapture.waitTicks(15));
        s.add(HologramCapture.screenshot("JC_18_holding_plant"));
        s.add(lookAt(stage, 0.0, -0.02, 2.0));
        s.add(HologramCapture.waitTicks(4));
        s.add(use());
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.check("real right-click with the plant item on grass placed a picked plant, one item used", () ->
                serverBlock(stage, 0, 0, 2).is(AlchemyBlocks.JUEYUN_CHILI_PLANT) && serverBlock(stage, 0, 0, 2).getValue(HARVESTED)
                        && held(AlchemyBlocks.JUEYUN_CHILI_PLANT.asItem()) == 3));
        s.add(HologramCapture.screenshot("JC_19_placed_picked"));
        s.add(here(stage, "setblock ~-1 ~-1 ~2 minecraft:stone"));
        s.add(lookAt(stage, -1.0, -0.02, 2.0));
        s.add(HologramCapture.waitTicks(4));
        s.add(use());
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.check("real right-click with the plant item on stone places nothing (support rule), stack untouched", () ->
                serverBlock(stage, -1, 0, 2).isAir() && held(AlchemyBlocks.JUEYUN_CHILI_PLANT.asItem()) == 3));
        s.add(slot(1));
        s.add(lookAt(stage, 0.0, 0.35, 2.0));
        s.add(HologramCapture.waitTicks(4));
        s.add(use());
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.check("real bone meal on the picked plant: fruiting at once, one bone meal used; the client shows it too", () ->
                !serverBlock(stage, 0, 0, 2).getValue(HARVESTED) && held(Items.BONE_MEAL) == 15
                        && clientBlock(stage, 0, 0, 2).equals(serverBlock(stage, 0, 0, 2))));
        s.add(HologramCapture.screenshot("JC_20_bone_meal_fruiting"));
        s.add(slot(4));
        s.add(lookAt(stage, 0.0, 0.35, 2.0));
        s.add(HologramCapture.waitTicks(4));
        s.add(use());
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("real empty-hand right-click harvested exactly one Jueyun Chili (dropped + picked up); the plant stays, picked", () ->
                chilis(stage) == 1 && serverBlock(stage, 0, 0, 2).is(AlchemyBlocks.JUEYUN_CHILI_PLANT) && serverBlock(stage, 0, 0, 2).getValue(HARVESTED)));
        s.add(HologramCapture.check("the client's block state matches the server's after the harvest (picked)", () ->
                clientBlock(stage, 0, 0, 2).equals(serverBlock(stage, 0, 0, 2))));
        s.add(HologramCapture.screenshot("JC_21_harvested_one_chili"));
        for (int i = 0; i < 3; i++) {
            s.add(use());
            s.add(HologramCapture.waitTicks(6));
        }
        s.add(HologramCapture.check("three more real right-clicks on the picked plant give no chili", () ->
                chilis(stage) == 1 && serverBlock(stage, 0, 0, 2).getValue(HARVESTED)));
        s.add(HologramCapture.waitTicks(30));
        s.add(HologramCapture.check("the harvested chili lies on the ground beside the plant (popped like the bushes' flowers)", () ->
                dropped(stage, 0, 0.3, 2, 3, SKIngredientItems.JUEYUN_CHILI) == 1));
        s.add(HologramCapture.screenshot("JC_22_after_repeat_clicks"));
        s.add(attack());
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.check("breaking the picked plant by hand removes it and drops nothing; still one chili in total", () ->
                serverBlock(stage, 0, 0, 2).isAir() && chilis(stage) == 1 && dropped(stage, 0, 0.3, 2, 3, AlchemyBlocks.JUEYUN_CHILI_PLANT.asItem()) == 0));
        s.add(HologramCapture.screenshot("JC_23_broken"));

        // Eating: 40 Food -> 44 after one chili (the hold is longer than the 32-tick eat).
        s.add(onServer(server -> PlayerResourceService.INSTANCE.set(me(server), ResourceTarget.scalar(PlayerResourceIds.FOOD, 40),
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.ADMIN_COMMAND)))));
        s.add(HologramCapture.command("item replace entity @s hotbar.2 with totality:jueyun_chili 5"));
        s.add(slot(2));
        s.add(lookAt(stage, 0.0, 1.2, 6.0));
        s.add(HologramCapture.waitTicks(10));
        int[] eatTicks = {0};
        s.add(mc -> {
            mc.options.keyUse.setDown(true);
            if (eatTicks[0] == 16) HologramCapture.screenshot("JC_24_eating").tick(mc);
            return ++eatTicks[0] >= 44;
        });
        s.add(HologramCapture.run("release use", () -> Minecraft.getInstance().options.keyUse.setDown(false)));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.check("real eating (use held): one chili used, Food 40 -> 44", () ->
                held(SKIngredientItems.JUEYUN_CHILI) == 4 && food() == 44));
        s.add(mc -> {
            HologramCapture.log("JC eat: chilis " + held(SKIngredientItems.JUEYUN_CHILI) + ", food " + food());
            return true;
        });

        // Tooltips.
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("item replace entity @s hotbar.0 with totality:jueyun_chili 12"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with totality:jueyun_chili_plant 3"));
        s.add(HologramCapture.command("item replace entity @s hotbar.2 with totality:garlic 4"));
        s.add(slot(0));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.screenshot("JC_25_hotbar_chili"));
        inventoryShot(s, "JC_26_tooltip_chili", 0);
        inventoryShot(s, "JC_27_tooltip_plant", 1);
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("gamerule random_tick_speed 3"));
        s.add(HologramCapture.command("gamemode creative @s"));
        return s;
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────────────────

    private static Step onServer(Consumer<MinecraftServer> action) {
        return mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> action.accept(server));
            return true;
        };
    }

    private static ServerPlayer me(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (!(p instanceof TotalityFakePlayer)) return p;
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static Vec3 at(double[] stage, double dx, double dy, double dz) {
        return new Vec3(stage[0] + dx, stage[1] + dy, stage[2] + dz);
    }

    private static BlockPos block(double[] stage, int dx, int dy, int dz) {
        return BlockPos.containing(stage[0] + dx, stage[1] + dy, stage[2] + dz);
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    private static float pitchTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    private static Step camera(double[] stage, double ex, double ey, double ez, double lx, double ly, double lz) {
        return onServer(server -> {
            Vec3 eye = at(stage, ex, ey, ez), look = at(stage, lx, ly, lz);
            ServerPlayer p = me(server);
            p.teleportTo((ServerLevel) p.level(), eye.x, eye.y - EYE, eye.z, Set.of(), yawTo(eye, look), pitchTo(eye, look), true);
        });
    }

    private static void view(List<Step> s, double[] stage, String name, double ex, double ey, double ez, double lx, double ly, double lz) {
        s.add(camera(stage, ex, ey, ez, lx, ly, lz));
        s.add(HologramCapture.waitTicks(12));
        s.add(HologramCapture.screenshot(name));
    }

    private static Step lookAt(double[] stage, double x, double y, double z) {
        return mc -> {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 target = at(stage, x, y, z);
            float yaw = yawTo(eye, target), pitch = pitchTo(eye, target);
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
            return true;
        };
    }

    private static Step use() {
        return mc -> {
            KeyMapping.click(mc.options.keyUse.getDefaultKey());
            HologramCapture.log("press: use");
            return true;
        };
    }

    private static Step attack() {
        return mc -> {
            KeyMapping.click(mc.options.keyAttack.getDefaultKey());
            HologramCapture.log("press: attack");
            return true;
        };
    }

    private static Step hud(boolean shown) {
        return HologramCapture.run(shown ? "show HUD" : "hide HUD", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (hud.isHidden() == shown) hud.toggle();
            hud.getChat().clearMessages(false);
        });
    }

    private static Step slot(int index) {
        return HologramCapture.run("select hotbar slot " + index, () -> Minecraft.getInstance().player.getInventory().setSelectedSlot(index));
    }

    private static Step here(double[] stage, String command) {
        return mc -> HologramCapture.command(String.format(Locale.ROOT, "execute positioned %.3f %.3f %.3f run %s",
                stage[0], stage[1], stage[2], command)).tick(mc);
    }

    private static Step rememberStage(double[] stage) {
        return HologramCapture.run("remember the stage", () -> {
            var p = Minecraft.getInstance().player;
            stage[0] = Math.floor(p.getX()) + 0.5;
            stage[1] = Math.floor(p.getY());
            stage[2] = Math.floor(p.getZ()) + 0.5;
        });
    }

    private static <T> T serverNow(Function<MinecraftServer, T> query) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        return server.submit(() -> query.apply(server)).join();
    }

    private static int dropped(double[] stage, double dx, double dy, double dz, double radius, Item item) {
        return serverNow(server -> server.overworld().getEntitiesOfClass(ItemEntity.class,
                AABB.ofSize(at(stage, dx, dy, dz), radius * 2, radius * 2, radius * 2),
                e -> e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum());
    }

    private static int held(Item item) {
        return serverNow(server -> {
            ServerPlayer p = me(server);
            int n = 0;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
            return n;
        });
    }

    /** Every Jueyun Chili the harvest produced: still on the ground near the plant, or already picked up. */
    private static int chilis(double[] stage) {
        return dropped(stage, 0, 0.3, 2, 3, SKIngredientItems.JUEYUN_CHILI) + held(SKIngredientItems.JUEYUN_CHILI);
    }

    private static long food() {
        return serverNow(server -> PlayerResourceService.INSTANCE.query(me(server), PlayerResourceIds.FOOD)
                instanceof ResourceQueryResult.Success success ? success.snapshot().currentUnits() : -1L);
    }

    private static BlockState serverBlock(double[] stage, int dx, int dy, int dz) {
        return serverNow(server -> server.overworld().getBlockState(block(stage, dx, dy, dz)));
    }

    private static BlockState clientBlock(double[] stage, int dx, int dy, int dz) {
        return Minecraft.getInstance().level.getBlockState(block(stage, dx, dy, dz));
    }

    private static Step mouseTo(double guiX, double guiY) {
        return HologramCapture.run("mouse to " + guiX + "," + guiY, () -> {
            Minecraft mc = Minecraft.getInstance();
            double scale = mc.getWindow().getGuiScale();
            try {
                var fx = MouseHandler.class.getDeclaredField("xpos");
                var fy = MouseHandler.class.getDeclaredField("ypos");
                fx.setAccessible(true);
                fy.setAccessible(true);
                fx.setDouble(mc.mouseHandler, guiX * scale);
                fy.setDouble(mc.mouseHandler, guiY * scale);
            } catch (ReflectiveOperationException e) {
                HologramCapture.log("FAIL: cannot move the mouse: " + e);
            }
        });
    }

    /** Survival inventory (176 x 166), hovering hotbar column {@code col}. */
    private static void inventoryShot(List<Step> s, String name, int col) {
        s.add(HologramCapture.run("allow screens", () -> HologramCapture.allowScreens = true));
        s.add(HologramCapture.run("inventory", () -> Minecraft.getInstance().gui.setScreen(new InventoryScreen(Minecraft.getInstance().player))));
        s.add(HologramCapture.waitTicks(10));
        s.add(mc -> {
            int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
            int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
            return mouseTo(left + 8 + col * 18 + 8, top + 142 + 8).tick(mc);
        });
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.screenshot(name));
        s.add(HologramCapture.run("close", () -> Minecraft.getInstance().gui.setScreen(null)));
        s.add(HologramCapture.run("screens back to normal", () -> HologramCapture.allowScreens = false));
    }
}
