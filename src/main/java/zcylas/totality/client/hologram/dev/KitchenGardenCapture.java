package zcylas.totality.client.hologram.dev;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.block.cooking.CuttingBoardBlock;
import zcylas.totality.blockentity.cooking.CuttingBoardBlockEntity;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.init.blocks.AlchemyBlocks;
import zcylas.totality.init.blocks.CookingBlocks;
import zcylas.totality.init.items.SKIngredientItems;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Creative Tests E and F footage for the opt-in capture run (inert in normal play):
 * scene 55 Graphite Ore revision ({@code GR_}), scene 56 Garlic crop and Clove ({@code GA_}), scene 57 Cutting Board
 * ({@code CB_}). World shots use a spectator camera with the HUD hidden; interactions are the local survival player's
 * real controls (use / attack keys, inventory clicks), with the integrated server's resulting state logged as
 * PASS/FAIL lines beside the frames.
 */
final class KitchenGardenCapture {

    private KitchenGardenCapture() {}

    private static final double EYE = 1.62;
    /** Harvest row x offsets, ordered nearest-first from the player standing at x - 0.5. */
    private static final int[] NEAREST_FIRST = {-1, 0, -2, 1, -3, 2};

    // ── shared helpers ──────────────────────────────────────────────────────────────────────────────────────────

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

    /** The local player's eyes at {@code eye}, looking at {@code look} (stage offsets); works in any game mode. */
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

    /** The local player turns to look at a point (client side, like moving the mouse). */
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

    private static Step boardState(double[] stage, String when) {
        return mc -> {
            HologramCapture.log("CB state " + when + ": board " + boardCount(stage) + ", garlic " + held(mc, SKIngredientItems.GARLIC)
                    + ", cloves " + held(mc, SKIngredientItems.GARLIC_CLOVE) + ", cloves dropped " + dropped(mc, stage, 0, 1.2, 4, 2.0, SKIngredientItems.GARLIC_CLOVE)
                    + ", garlic dropped " + dropped(mc, stage, 0, 1.2, 4, 2.0, SKIngredientItems.GARLIC));
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

    /** A command run at the stage origin (fixed once remembered), whatever the camera has done since. */
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

    /** A flat plains meadow around the stage (fills split under the 32768-block limit). */
    private static void meadow(List<Step> s, double[] stage, String ground) {
        for (int[] z : new int[][]{{-12, 3}, {4, 19}, {20, 30}}) {
            s.add(here(stage, String.format(Locale.ROOT, "fill ~-16 ~ ~%d ~16 ~12 ~%d minecraft:air", z[0], z[1])));
            s.add(here(stage, String.format(Locale.ROOT, "fill ~-16 ~13 ~%d ~16 ~24 ~%d minecraft:air", z[0], z[1])));
            s.add(here(stage, String.format(Locale.ROOT, "fill ~-16 ~-3 ~%d ~16 ~-1 ~%d minecraft:%s", z[0], z[1], ground)));
            s.add(here(stage, String.format(Locale.ROOT, "fillbiome ~-16 ~-3 ~%d ~16 ~12 ~%d minecraft:plains", z[0], z[1])));
        }
    }

    private static void begin(List<Step> s, double[] stage, String ground) {
        s.add(HologramCapture.command("gamerule spawn_mobs false"));
        s.add(HologramCapture.command("gamerule send_command_feedback false"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("weather clear"));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(hud(false));
        s.add(rememberStage(stage));
        meadow(s, stage, ground);
        s.add(here(stage, "kill @e[type=!minecraft:player,distance=..60]"));
        s.add(HologramCapture.waitTicks(40));
    }

    /** Reads the integrated server's state on the server thread (block entities are invisible from other threads). */
    private static <T> T serverNow(Function<MinecraftServer, T> query) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        return server.submit(() -> query.apply(server)).join();
    }

    private static int dropped(Minecraft mc, double[] stage, double dx, double dy, double dz, double radius, Item item) {
        return serverNow(server -> server.overworld().getEntitiesOfClass(ItemEntity.class,
                AABB.ofSize(at(stage, dx, dy, dz), radius * 2, radius * 2, radius * 2),
                e -> e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum());
    }

    private static int held(Minecraft mc, Item item) {
        return serverNow(server -> {
            ServerPlayer p = me(server);
            int n = 0;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
            return n;
        });
    }

    private static BlockState serverBlock(double[] stage, int dx, int dy, int dz) {
        return serverNow(server -> server.overworld().getBlockState(block(stage, dx, dy, dz)));
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

    /** Survival inventory (176 x 166), hovering main-inventory row {@code row} (1-3; 4 = hotbar), column {@code col}. */
    private static void inventoryShot(List<Step> s, String name, int col, int row) {
        s.add(HologramCapture.run("allow screens", () -> HologramCapture.allowScreens = true));
        s.add(HologramCapture.run("inventory", () -> Minecraft.getInstance().gui.setScreen(new InventoryScreen(Minecraft.getInstance().player))));
        s.add(HologramCapture.waitTicks(10));
        s.add(mc -> {
            int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
            int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
            double y = row == 4 ? top + 142 + 8 : top + 84 + (row - 1) * 18 + 8;
            return mouseTo(left + 8 + col * 18 + 8, y).tick(mc);
        });
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.screenshot(name));
        s.add(HologramCapture.run("close", () -> Minecraft.getInstance().gui.setScreen(null)));
        s.add(HologramCapture.run("screens back to normal", () -> HologramCapture.allowScreens = false));
    }

    // ── Scene 55: Graphite Ore ──────────────────────────────────────────────────────────────────────────────────

    static List<Step> graphiteScenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        begin(s, stage, "stone");
        // A line-up (z + 6): stone, graphite, coal, iron | deepslate, deepslate graphite, deepslate coal, deepslate iron.
        String[] lineUp = {"stone", "totality:graphite_ore", "coal_ore", "iron_ore",
                "deepslate", "totality:deepslate_graphite_ore", "deepslate_coal_ore", "deepslate_iron_ore"};
        for (int i = 0; i < lineUp.length; i++) {
            s.add(here(stage, String.format(Locale.ROOT, "setblock ~%d ~ ~6 %s", -7 + i * 2, lineUp[i])));
        }
        // Two rock faces (z + 14): stone on the left, deepslate on the right, each with a natural-looking graphite vein.
        s.add(here(stage, "fill ~-11 ~ ~14 ~-1 ~5 ~16 minecraft:stone"));
        s.add(here(stage, "fill ~1 ~ ~14 ~11 ~5 ~16 minecraft:deepslate"));
        int[][] vein = {{-7, 1}, {-6, 1}, {-6, 2}, {-5, 2}, {-7, 2}, {-5, 3}, {-4, 3}, {-8, 0}, {-3, 4}};
        for (int[] v : vein) {
            s.add(here(stage, String.format(Locale.ROOT, "setblock ~%d ~%d ~14 totality:graphite_ore", v[0], v[1])));
            s.add(here(stage, String.format(Locale.ROOT, "setblock ~%d ~%d ~14 totality:deepslate_graphite_ore", v[0] + 12, v[1])));
        }
        s.add(here(stage, "setblock ~-9 ~3 ~14 minecraft:coal_ore"));
        s.add(here(stage, "setblock ~-2 ~1 ~14 minecraft:coal_ore"));
        s.add(here(stage, "setblock ~9 ~3 ~14 minecraft:deepslate_coal_ore"));
        s.add(here(stage, "setblock ~3 ~0 ~14 minecraft:deepslate_iron_ore"));
        s.add(HologramCapture.waitTicks(30));

        view(s, stage, "GR_01_lineup_front", 0, 1.6, 0.5, 0, 0.5, 6.5);
        view(s, stage, "GR_02_lineup_angled_high", -4, 3.2, 2.5, 1.5, 0.3, 6.5);
        view(s, stage, "GR_03_graphite_ore_close_corner", -3.8, 1.9, 4.6, -5.0, 0.5, 6.0);
        view(s, stage, "GR_04_graphite_ore_close_top", -5.0, 2.6, 5.2, -5.0, 0.5, 6.0);
        view(s, stage, "GR_05_deepslate_graphite_close_corner", 4.2, 1.9, 4.6, 3.0, 0.5, 6.0);
        view(s, stage, "GR_06_deepslate_graphite_close_top", 3.0, 2.6, 5.2, 3.0, 0.5, 6.0);
        view(s, stage, "GR_07_graphite_beside_coal_close", -4.0, 1.3, 3.2, -4.0, 0.5, 6.0);
        view(s, stage, "GR_08_deepslate_graphite_beside_coal_close", 4.0, 1.3, 3.2, 4.0, 0.5, 6.0);
        view(s, stage, "GR_09_vein_stone", -5.5, 2.4, 8.5, -5.5, 2.2, 14.0);
        view(s, stage, "GR_10_vein_deepslate", 6.5, 2.4, 8.5, 6.5, 2.2, 14.0);
        view(s, stage, "GR_11_both_faces_distance", 0, 3.5, 1.0, 0, 2.2, 14.0);
        // Dim "cave" light: night, torches on the faces.
        s.add(HologramCapture.command("time set 18000"));
        s.add(here(stage, "setblock ~-6 ~4 ~13 minecraft:wall_torch[facing=north]"));
        s.add(here(stage, "setblock ~6 ~4 ~13 minecraft:wall_torch[facing=north]"));
        view(s, stage, "GR_12_faces_torchlight", 0, 3.0, 7.0, 0, 2.0, 14.0);
        s.add(HologramCapture.command("time set 6000"));

        // Inventory: both ores beside coal and iron ore and Graphite.
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(camera(stage, 0, 1.6, 0.5, 0, 0.5, 6.5));
        s.add(hud(true));
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("item replace entity @s hotbar.0 with totality:graphite_ore 64"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with totality:deepslate_graphite_ore 64"));
        s.add(HologramCapture.command("item replace entity @s hotbar.2 with minecraft:coal_ore 64"));
        s.add(HologramCapture.command("item replace entity @s hotbar.3 with minecraft:deepslate_coal_ore 64"));
        s.add(HologramCapture.command("item replace entity @s hotbar.4 with minecraft:iron_ore 64"));
        s.add(HologramCapture.command("item replace entity @s hotbar.5 with totality:graphite 12"));
        s.add(HologramCapture.command("item replace entity @s inventory.0 with totality:graphite_ore 20"));
        s.add(HologramCapture.command("item replace entity @s inventory.1 with totality:deepslate_graphite_ore 20"));
        s.add(slot(0));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot("GR_13_hotbar_held"));
        inventoryShot(s, "GR_14_inventory_graphite_ore_tooltip", 0, 4);
        inventoryShot(s, "GR_15_inventory_deepslate_graphite_tooltip", 1, 4);
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("gamemode creative @s"));
        return s;
    }

    // ── Scene 56: Garlic crop and Garlic Clove ─────────────────────────────────────────────────────────────────

    static List<Step> garlicScenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        s.add(HologramCapture.command("gamerule random_tick_speed 0"));
        begin(s, stage, "grass_block");
        // Stage row (z + 5): farmland x -4..3, ages 0..7, water at the ends of a channel behind (z + 6).
        s.add(here(stage, "fill ~-5 ~-1 ~5 ~4 ~-1 ~5 minecraft:farmland[moisture=7]"));
        s.add(here(stage, "fill ~-5 ~-1 ~6 ~4 ~-1 ~6 minecraft:water"));
        for (int age = 0; age < 8; age++) {
            s.add(here(stage, String.format(Locale.ROOT, "setblock ~%d ~ ~5 totality:garlic_crop[age=%d]", -4 + age, age)));
        }
        // A garden (z + 10..14): three farmland rows beside a water channel, mostly mature with some younger plants.
        s.add(here(stage, "fill ~-7 ~-1 ~10 ~7 ~-1 ~14 minecraft:farmland[moisture=7]"));
        s.add(here(stage, "fill ~-7 ~-1 ~12 ~7 ~-1 ~12 minecraft:water"));
        int[] garden = {7, 6, 7, 7, 5, 7, 6, 7, 7, 4, 7, 6, 7, 7, 5};
        for (int i = 0; i < garden.length; i++) {
            for (int z : new int[]{10, 11, 13, 14}) {
                int age = garden[(i + z) % garden.length];
                s.add(here(stage, String.format(Locale.ROOT, "setblock ~%d ~ ~%d totality:garlic_crop[age=%d]", -7 + i, z, age)));
            }
        }
        s.add(HologramCapture.waitTicks(30));
        view(s, stage, "GA_01_stages_0_to_7_front", -0.5, 1.1, 2.0, -0.5, 0.3, 5.5);
        view(s, stage, "GA_02_stages_0_to_7_angled", -6.0, 2.2, 2.6, -0.5, 0.2, 5.5);
        view(s, stage, "GA_03_stages_low_side", -6.5, 0.6, 4.2, 0.5, 0.35, 5.5);
        view(s, stage, "GA_04_immature_vs_mature_close", 1.0, 1.0, 3.6, 1.0, 0.3, 5.5);
        view(s, stage, "GA_05_mature_close", 3.5, 0.9, 4.0, 3.5, 0.4, 5.5);
        view(s, stage, "GA_06_mature_top", 3.5, 1.9, 5.2, 3.5, 0.2, 5.5);
        view(s, stage, "GA_07_garden_rows", -8.0, 3.2, 7.0, -1.0, 0.0, 12.0);
        view(s, stage, "GA_08_garden_eye_level", 0.0, 1.6, 8.0, 0.0, 0.3, 13.0);

        // Survival: plant, bone meal, harvest, Fortune, craft.
        s.add(here(stage, "fill ~-5 ~-1 ~2 ~4 ~-1 ~2 minecraft:farmland[moisture=7]"));
        s.add(here(stage, "fill ~-5 ~-1 ~1 ~4 ~-1 ~1 minecraft:water"));
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("item replace entity @s hotbar.0 with totality:garlic_clove 8"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with minecraft:bone_meal 32"));
        s.add(HologramCapture.command("item replace entity @s hotbar.2 with totality:garlic 3"));
        s.add(HologramCapture.command("item replace entity @s hotbar.3 with minecraft:netherite_hoe[minecraft:enchantments={\"minecraft:fortune\":3}]"));
        s.add(hud(true));
        s.add(camera(stage, 1.5, 1.62, -0.1, 1.5, 0, 2.5));
        s.add(slot(0));
        s.add(HologramCapture.waitTicks(15));
        s.add(HologramCapture.screenshot("GA_09_holding_clove"));
        s.add(lookAt(stage, 0.0, -0.02, 2.0));
        s.add(HologramCapture.waitTicks(3));
        s.add(use());
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.check("real right-click with a Clove on farmland planted garlic at age 0, one Clove used", () -> {
            var st = serverBlock(stage, 0, 0, 2);
            return st.is(AlchemyBlocks.GARLIC_CROP) && AlchemyBlocks.GARLIC_CROP.getAge(st) == 0 && held(Minecraft.getInstance(), SKIngredientItems.GARLIC_CLOVE) == 7;
        }));
        s.add(HologramCapture.screenshot("GA_10_planted_clove"));
        for (int x = 1; x <= 3; x++) {
            s.add(lookAt(stage, x, -0.02, 2.0));
            s.add(HologramCapture.waitTicks(6));
            s.add(use());
            s.add(HologramCapture.waitTicks(6));
        }
        s.add(HologramCapture.check("four Cloves planted in a row", () -> {
            for (int x = 0; x <= 3; x++) if (!serverBlock(stage, x, 0, 2).is(AlchemyBlocks.GARLIC_CROP)) return false;
            return held(Minecraft.getInstance(), SKIngredientItems.GARLIC_CLOVE) == 4;
        }));
        s.add(HologramCapture.screenshot("GA_11_row_planted"));
        // Bone meal on the first plant until it is mature.
        s.add(slot(1));
        for (int i = 0; i < 4; i++) {
            s.add(lookAt(stage, 0.0, 0.1, 2.0));
            s.add(HologramCapture.waitTicks(3));
            s.add(use());
            s.add(HologramCapture.waitTicks(6));
            int n = i;
            s.add(mc -> {
                var st = serverBlock(stage, 0, 0, 2);
                HologramCapture.log("GA bone meal " + (n + 1) + ": age " + (st.getBlock() instanceof CropBlock c ? c.getAge(st) : -1)
                        + ", bone meal left " + held(mc, Items.BONE_MEAL));
                return true;
            });
            s.add(HologramCapture.screenshot("GA_12_bone_meal_" + (i + 1)));
        }
        s.add(HologramCapture.check("bone meal (real use) grew the first plant", () -> {
            var st = serverBlock(stage, 0, 0, 2);
            return st.getBlock() instanceof CropBlock c && c.getAge(st) >= 5;
        }));
        s.add(here(stage, "setblock ~ ~ ~2 totality:garlic_crop[age=7]"));
        s.add(here(stage, "setblock ~1 ~ ~2 totality:garlic_crop[age=3]"));
        // Harvest by hand: immature, then mature.
        s.add(slot(4));
        s.add(lookAt(stage, 1.0, 0.1, 2.0));
        s.add(HologramCapture.waitTicks(3));
        s.add(attack());
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("breaking the immature plant (age 3) by hand drops exactly one Clove (dropped + picked up)", () ->
                dropped(Minecraft.getInstance(), stage, 1, 0.3, 2, 3, SKIngredientItems.GARLIC_CLOVE) + held(Minecraft.getInstance(), SKIngredientItems.GARLIC_CLOVE) - 4 == 1
                        && dropped(Minecraft.getInstance(), stage, 1, 0.3, 2, 3, SKIngredientItems.GARLIC) + held(Minecraft.getInstance(), SKIngredientItems.GARLIC) - 3 == 0));
        s.add(HologramCapture.screenshot("GA_13_immature_harvest_clove"));
        s.add(lookAt(stage, 0.0, 0.1, 2.0));
        s.add(HologramCapture.waitTicks(3));
        s.add(attack());
        s.add(HologramCapture.waitTicks(10));
        s.add(mc -> {
            HologramCapture.log("GA mature single: block now " + serverBlock(stage, 0, 0, 2) + ", Garlic within 2: "
                    + dropped(mc, stage, 0, 0.3, 2, 2, SKIngredientItems.GARLIC) + ", held " + held(mc, SKIngredientItems.GARLIC));
            return true;
        });
        s.add(HologramCapture.check("breaking the mature plant by hand drops exactly one Garlic (dropped + picked up)", () ->
                dropped(Minecraft.getInstance(), stage, 0, 0.3, 2, 3, SKIngredientItems.GARLIC) + held(Minecraft.getInstance(), SKIngredientItems.GARLIC) - 3 == 1));
        s.add(HologramCapture.screenshot("GA_14_mature_harvest_garlic"));
        s.add(here(stage, "kill @e[type=minecraft:item,distance=..20]"));
        // Fortune III: eight mature plants vs eight by hand.
        s.add(here(stage, "fill ~-5 ~ ~2 ~4 ~ ~2 minecraft:air"));
        s.add(here(stage, "fill ~-3 ~ ~2 ~2 ~ ~2 totality:garlic_crop[age=7]"));
        s.add(camera(stage, -0.5, 1.62, 0.2, -0.5, 0, 2.5));
        s.add(HologramCapture.waitTicks(5));
        int[] before = new int[1];
        s.add(mc -> { before[0] = held(mc, SKIngredientItems.GARLIC); return true; });
        s.add(slot(3));
        for (int x : NEAREST_FIRST) {   // the nearer mature plant's full-height box would catch a swing at a farther one
            s.add(lookAt(stage, x, 0.5, 2.0));
            s.add(HologramCapture.waitTicks(3));
            s.add(attack());
            s.add(HologramCapture.waitTicks(6));
            s.add(harvestLog(stage, x, before));
        }
        s.add(HologramCapture.waitTicks(10));
        s.add(mc -> {
            int garlic = dropped(mc, stage, -0.5, 0.3, 2, 9, SKIngredientItems.GARLIC) + held(mc, SKIngredientItems.GARLIC) - before[0];
            int cloves = dropped(mc, stage, -0.5, 0.3, 2, 9, SKIngredientItems.GARLIC_CLOVE);
            HologramCapture.log((garlic > 6 && cloves == 0 ? "PASS" : "FAIL") + ": Fortune III netherite hoe on 6 mature plants: " + garlic
                    + " Garlic (1 + Binomial(3, 4/7) each: 6..24, expected ~16.3), " + cloves + " Cloves");
            return true;
        });
        s.add(camera(stage, -0.5, 2.6, -0.5, -0.5, 0, 2.5));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.screenshot("GA_15_fortune_iii_drops"));
        s.add(here(stage, "kill @e[type=minecraft:item,distance=..20]"));
        s.add(here(stage, "fill ~-3 ~ ~2 ~2 ~ ~2 totality:garlic_crop[age=7]"));
        s.add(camera(stage, -0.5, 1.62, 0.2, -0.5, 0, 2.5));
        s.add(mc -> { before[0] = held(mc, SKIngredientItems.GARLIC); return true; });
        s.add(slot(4));
        for (int x : NEAREST_FIRST) {   // the nearer mature plant's full-height box would catch a swing at a farther one
            s.add(lookAt(stage, x, 0.5, 2.0));
            s.add(HologramCapture.waitTicks(3));
            s.add(attack());
            s.add(HologramCapture.waitTicks(6));
            s.add(harvestLog(stage, x, before));
        }
        s.add(HologramCapture.waitTicks(10));
        s.add(mc -> {
            int garlic = dropped(mc, stage, -0.5, 0.3, 2, 9, SKIngredientItems.GARLIC) + held(mc, SKIngredientItems.GARLIC) - before[0];
            HologramCapture.log((garlic == 6 ? "PASS" : "FAIL") + ": by hand six mature plants give exactly six Garlic (dropped + picked up = " + garlic + ")");
            return true;
        });
        s.add(camera(stage, -0.5, 2.6, -0.5, -0.5, 0, 2.5));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.screenshot("GA_16_bare_hand_drops"));
        s.add(here(stage, "kill @e[type=minecraft:item,distance=..20]"));

        // Crafting: 1 Garlic -> 4 Cloves through a real crafting table screen and real clicks.
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("item replace entity @s hotbar.0 with totality:garlic 3"));
        s.add(here(stage, "setblock ~ ~ ~3 minecraft:crafting_table"));
        s.add(camera(stage, 0.5, 1.62, 0.3, 0.5, 0.5, 3.5));
        s.add(HologramCapture.waitTicks(5));
        s.add(HologramCapture.run("allow screens", () -> HologramCapture.allowScreens = true));
        s.add(onServer(server -> {
            ServerPlayer p = me(server);
            BlockPos table = block(stage, 0, 0, 3);
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new CraftingMenu(id, inv, ContainerLevelAccess.create(p.level(), table)),
                    Component.translatable("container.crafting")));
        }));
        s.add(HologramCapture.waitTicks(10));
        s.add(click(37, ContainerInput.PICKUP, 0));      // pick up the Garlic stack (hotbar 0)
        s.add(HologramCapture.waitTicks(2));
        s.add(click(1, ContainerInput.PICKUP, 1));       // right-click: one Garlic into the first grid slot
        s.add(HologramCapture.waitTicks(2));
        s.add(click(37, ContainerInput.PICKUP, 0));      // put the rest back
        s.add(HologramCapture.waitTicks(6));
        s.add(mc -> {
            int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
            int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
            return mouseTo(left + 124 + 8, top + 35 + 8).tick(mc);
        });
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.screenshot("GA_17_crafting_garlic_to_cloves"));
        s.add(click(0, ContainerInput.QUICK_MOVE, 0));   // shift-click the result
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.check("crafting took one Garlic and gave four Garlic Cloves", () ->
                held(Minecraft.getInstance(), SKIngredientItems.GARLIC_CLOVE) == 4 && held(Minecraft.getInstance(), SKIngredientItems.GARLIC) == 2));
        s.add(HologramCapture.screenshot("GA_18_crafted_cloves_in_inventory"));
        s.add(HologramCapture.run("close", () -> Minecraft.getInstance().player.closeContainer()));
        s.add(HologramCapture.run("screens back to normal", () -> HologramCapture.allowScreens = false));

        // The items: Clove beside the existing Garlic, with the Clove's tooltip.
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("item replace entity @s hotbar.0 with totality:garlic_clove 16"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with totality:garlic 16"));
        s.add(HologramCapture.command("item replace entity @s hotbar.2 with totality:garlic_clove"));
        s.add(HologramCapture.command("item replace entity @s hotbar.3 with totality:garlic"));
        s.add(HologramCapture.command("item replace entity @s hotbar.4 with minecraft:wheat_seeds"));
        s.add(HologramCapture.command("item replace entity @s hotbar.5 with minecraft:carrot"));
        s.add(slot(0));
        inventoryShot(s, "GA_19_inventory_clove_tooltip", 0, 4);
        inventoryShot(s, "GA_20_inventory_garlic_tooltip", 1, 4);
        s.add(here(stage, "summon minecraft:item ~-0.4 ~ ~2.2 {Item:{id:\"totality:garlic_clove\",count:1},PickupDelay:32767s,Age:-32768s}"));
        s.add(here(stage, "summon minecraft:item ~0.4 ~ ~2.2 {Item:{id:\"totality:garlic\",count:1},PickupDelay:32767s,Age:-32768s}"));
        s.add(here(stage, "setblock ~ ~ ~3 minecraft:air"));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(hud(false));
        s.add(camera(stage, 0.0, 1.3, 0.8, 0.0, 0.1, 2.7));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot("GA_21_clove_and_garlic_dropped"));
        s.add(here(stage, "kill @e[type=minecraft:item,distance=..20]"));
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("gamerule random_tick_speed 3"));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(hud(true));
        return s;
    }

    private static Step harvestLog(double[] stage, int x, int[] before) {
        return mc -> {
            HologramCapture.log("GA harvest x=" + x + ": block now " + serverBlock(stage, x, 0, 2) + ", crosshair " + mc.hitResult
                    + ", Garlic dropped+picked so far " + (dropped(mc, stage, -0.5, 0.3, 2, 9, SKIngredientItems.GARLIC) + held(mc, SKIngredientItems.GARLIC) - before[0])
                    + ", all Garlic entities in 30 blocks " + dropped(mc, stage, -0.5, 0.3, 2, 30, SKIngredientItems.GARLIC));
            return true;
        };
    }

    private static Step click(int slotIndex, ContainerInput type, int button) {
        return mc -> {
            var menu = mc.player.containerMenu;
            mc.gameMode.handleContainerInput(menu.containerId, slotIndex, button, type, mc.player);
            HologramCapture.log("click: slot " + slotIndex + " " + type + " " + button);
            return true;
        };
    }

    // ── Scene 57: Cutting Board ────────────────────────────────────────────────────────────────────────────────

    static List<Step> cuttingBoardScenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        begin(s, stage, "spruce_planks");
        // A kitchen counter (z + 4): one block of polished andesite, a stone-brick wall behind it, a lantern and a potted
        // fern at its ends; the board goes on its top (y + 1).
        s.add(here(stage, "fill ~-4 ~ ~4 ~4 ~ ~4 minecraft:polished_andesite"));
        s.add(here(stage, "fill ~-4 ~ ~5 ~4 ~3 ~5 minecraft:stone_bricks"));
        s.add(here(stage, "setblock ~-4 ~1 ~4 minecraft:lantern"));
        s.add(here(stage, "setblock ~4 ~1 ~4 minecraft:potted_fern"));
        // Four boards set straight down, one per facing, on a second counter behind the wall (z + 8).
        s.add(here(stage, "fill ~-5 ~ ~8 ~5 ~ ~8 minecraft:polished_andesite"));
        String[] facings = {"north", "east", "south", "west"};
        for (int i = 0; i < 4; i++) {
            s.add(here(stage, String.format(Locale.ROOT, "setblock ~%d ~1 ~8 totality:cutting_board[facing=%s]", -3 + i * 2, facings[i])));
        }
        s.add(HologramCapture.waitTicks(20));

        // Placing one with the real use key: the player stands before the counter looking south (+z).
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("item replace entity @s hotbar.0 with totality:cutting_board 2"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with totality:garlic 8"));
        s.add(HologramCapture.command("item replace entity @s hotbar.2 with minecraft:iron_sword"));
        s.add(HologramCapture.command("item replace entity @s hotbar.3 with minecraft:iron_axe"));
        s.add(hud(true));
        s.add(camera(stage, 0.0, 1.62, 2.2, 0.0, 1.0, 4.0));
        s.add(slot(0));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.screenshot("CB_01_holding_board"));
        s.add(lookAt(stage, 0.0, 0.999, 4.0));
        s.add(HologramCapture.waitTicks(3));
        s.add(use());
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.check("real right-click placed a Cutting Board on the counter, facing the player's look (south)", () -> {
            var st = serverBlock(stage, 0, 1, 4);
            return st.is(CookingBlocks.CUTTING_BOARD) && st.getValue(CuttingBoardBlock.FACING) == Direction.SOUTH;
        }));
        s.add(HologramCapture.screenshot("CB_02_placed"));

        // Showcase views (spectator, HUD hidden). Facing south, the handle points to the player's right (-x).
        s.add(hud(false));
        s.add(HologramCapture.command("gamemode spectator @s"));
        view(s, stage, "CB_03_three_quarter", 1.1, 2.0, 2.6, 0.0, 1.05, 4.0);
        view(s, stage, "CB_04_close_handle_hole", -1.0, 1.75, 3.2, -0.3, 1.05, 4.0);
        view(s, stage, "CB_05_top_down", 0.0, 2.5, 3.97, 0.0, 1.0, 4.0);
        view(s, stage, "CB_06_side_profile", 0.0, 1.14, 2.6, 0.0, 1.06, 4.0);
        view(s, stage, "CB_07_end_profile", -1.7, 1.14, 4.0, 0.0, 1.06, 4.0);
        view(s, stage, "CB_08_kitchen_counter", 2.6, 2.3, 1.2, 0.0, 0.9, 4.0);
        view(s, stage, "CB_09_four_facings", 0.0, 2.8, 10.6, 0.0, 1.0, 8.0);

        // Ingredients, chopping, full inventory, taking back, breaking — the survival player's real controls.
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(hud(true));
        s.add(camera(stage, 0.0, 1.62, 2.2, 0.0, 1.0, 4.0));
        s.add(slot(1));
        s.add(lookAt(stage, 0.1, 1.12, 4.0));
        s.add(HologramCapture.waitTicks(3));
        s.add(use());
        s.add(HologramCapture.waitTicks(8));
        s.add(boardCheck(stage, "one real use with Garlic: one Garlic on the board, seven in hand", 1, SKIngredientItems.GARLIC, 7));
        s.add(HologramCapture.screenshot("CB_10_garlic_on_board_hud"));
        s.add(hud(false));
        s.add(HologramCapture.command("gamemode spectator @s"));
        view(s, stage, "CB_11_one_garlic_close", 0.8, 1.7, 3.1, 0.0, 1.08, 4.0);
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(camera(stage, 0.0, 1.62, 2.2, 0.0, 1.0, 4.0));
        s.add(hud(true));
        s.add(lookAt(stage, 0.1, 1.12, 4.0));
        for (int i = 0; i < 2; i++) {
            s.add(HologramCapture.waitTicks(3));
            s.add(use());
            s.add(HologramCapture.waitTicks(5));
        }
        s.add(boardCheck(stage, "two more uses: three Garlic on the board", 3, SKIngredientItems.GARLIC, 5));
        s.add(hud(false));
        s.add(HologramCapture.command("gamemode spectator @s"));
        view(s, stage, "CB_12_three_garlic_close", 0.8, 1.7, 3.1, 0.0, 1.08, 4.0);
        view(s, stage, "CB_13_three_garlic_top", 0.0, 2.2, 3.9, 0.0, 1.0, 4.0);
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(camera(stage, 0.0, 1.62, 2.2, 0.0, 1.0, 4.0));
        s.add(hud(true));
        s.add(slot(2));
        s.add(lookAt(stage, 0.1, 1.12, 4.0));
        s.add(HologramCapture.waitTicks(3));
        s.add(use());
        s.add(HologramCapture.waitTicks(8));
        s.add(boardCheck(stage, "one real sword use: one Garlic chopped, exactly four Cloves in the inventory", 2, SKIngredientItems.GARLIC_CLOVE, 4));
        s.add(HologramCapture.check("... nothing dropped, the sword took one point of wear", () ->
                dropped(Minecraft.getInstance(), stage, 0, 1.2, 4, 1.5, SKIngredientItems.GARLIC_CLOVE) == 0
                        && serverNow(server -> me(server).getInventory().getItem(2).getDamageValue()) == 1));
        s.add(HologramCapture.screenshot("CB_14_after_first_chop_hud"));
        s.add(HologramCapture.waitTicks(3));
        s.add(use());
        s.add(HologramCapture.waitTicks(5));
        s.add(use());
        s.add(HologramCapture.waitTicks(8));
        s.add(boardCheck(stage, "three chops in all: the board is empty, twelve Cloves", 0, SKIngredientItems.GARLIC_CLOVE, 12));
        s.add(use());
        s.add(HologramCapture.waitTicks(6));
        s.add(boardCheck(stage, "a sword on the empty board makes nothing", 0, SKIngredientItems.GARLIC_CLOVE, 12));
        s.add(HologramCapture.screenshot("CB_15_board_empty_twelve_cloves"));
        inventoryShot(s, "CB_16_inventory_after_chopping", 0, 1);

        // Full inventory: the output is popped on top of the board.
        s.add(HologramCapture.command("clear @s"));
        for (int i = 0; i < 27; i++) s.add(HologramCapture.command("item replace entity @s inventory." + i + " with minecraft:cobblestone 64"));
        for (int i = 3; i < 9; i++) s.add(HologramCapture.command("item replace entity @s hotbar." + i + " with minecraft:cobblestone 64"));
        s.add(HologramCapture.command("item replace entity @s hotbar.0 with minecraft:cobblestone 64"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with totality:garlic 1"));
        s.add(HologramCapture.command("item replace entity @s hotbar.2 with minecraft:iron_sword"));
        s.add(slot(1));
        s.add(lookAt(stage, 0.1, 1.12, 4.0));
        s.add(HologramCapture.waitTicks(3));
        s.add(use());
        s.add(HologramCapture.waitTicks(6));
        s.add(boardState(stage, "after adding the Garlic (full inventory)"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with minecraft:cobblestone 64"));   // no free slot left
        s.add(slot(2));
        s.add(HologramCapture.waitTicks(6));
        s.add(use());
        s.add(HologramCapture.waitTicks(6));
        s.add(boardState(stage, "after chopping (full inventory)"));
        s.add(HologramCapture.check("full inventory: the Garlic is used once, the four Cloves pop onto the board", () ->
                dropped(Minecraft.getInstance(), stage, 0, 1.2, 4, 1.5, SKIngredientItems.GARLIC_CLOVE) == 4
                        && boardCount(stage) == 0 && held(Minecraft.getInstance(), SKIngredientItems.GARLIC_CLOVE) == 0));
        s.add(HologramCapture.waitTicks(25));   // the popped Cloves settle on the board first
        s.add(HologramCapture.screenshot("CB_17_full_inventory_cloves_on_board"));
        s.add(here(stage, "kill @e[type=minecraft:item,distance=..20]"));

        // Take back with an empty hand, then break with contents.
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with totality:garlic 4"));
        s.add(HologramCapture.command("item replace entity @s hotbar.3 with minecraft:iron_axe"));
        s.add(slot(1));
        for (int i = 0; i < 4; i++) {
            s.add(HologramCapture.waitTicks(3));
            s.add(use());
            s.add(HologramCapture.waitTicks(4));
        }
        s.add(slot(5));
        s.add(HologramCapture.waitTicks(3));
        s.add(use());
        s.add(HologramCapture.waitTicks(6));
        s.add(boardState(stage, "after the empty-hand use"));
        s.add(HologramCapture.check("an empty hand takes all four Garlic back", () ->
                boardCount(stage) == 0 && held(Minecraft.getInstance(), SKIngredientItems.GARLIC) == 4));
        s.add(HologramCapture.command("clear @s totality:garlic"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with totality:garlic 2"));
        s.add(slot(1));
        s.add(HologramCapture.waitTicks(4));
        s.add(use());
        s.add(HologramCapture.waitTicks(5));
        s.add(use());
        s.add(HologramCapture.waitTicks(6));
        s.add(boardState(stage, "before breaking"));
        s.add(slot(3));
        s.add(HologramCapture.until("axe breaks the board (attack held)",
                () -> !serverBlock(stage, 0, 1, 4).is(CookingBlocks.CUTTING_BOARD),
                () -> Minecraft.getInstance().options.keyAttack.setDown(true), 200));
        s.add(HologramCapture.run("release attack", () -> Minecraft.getInstance().options.keyAttack.setDown(false)));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("survival break with two Garlic stored: one Cutting Board and the two Garlic drop (dropped + picked up)", () ->
                dropped(Minecraft.getInstance(), stage, 0, 1.2, 4, 3, CookingBlocks.CUTTING_BOARD.asItem()) + held(Minecraft.getInstance(), CookingBlocks.CUTTING_BOARD.asItem()) == 1
                        && dropped(Minecraft.getInstance(), stage, 0, 1.2, 4, 3, SKIngredientItems.GARLIC) + held(Minecraft.getInstance(), SKIngredientItems.GARLIC) == 2));
        s.add(HologramCapture.screenshot("CB_18_broken_board_and_contents"));
        s.add(here(stage, "kill @e[type=minecraft:item,distance=..20]"));

        // The item: inventory tooltip, held, dropped; scale beside the player.
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("item replace entity @s hotbar.0 with totality:cutting_board"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with totality:garlic"));
        s.add(HologramCapture.command("item replace entity @s hotbar.2 with totality:garlic_clove"));
        s.add(slot(0));
        inventoryShot(s, "CB_19_inventory_board_tooltip", 0, 4);
        s.add(camera(stage, 0.0, 1.62, 2.2, 0.0, 1.0, 4.0));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.screenshot("CB_20_held_first_person"));
        s.add(here(stage, "setblock ~ ~1 ~4 totality:cutting_board[facing=south]"));
        s.add(here(stage, "summon minecraft:item ~ ~0.1 ~1 {Item:{id:\"totality:cutting_board\",count:1},PickupDelay:32767s,Age:-32768s}"));
        s.add(hud(false));
        s.add(HologramCapture.run("third person (front)", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT)));
        s.add(camera(stage, 0.8, 1.62, 3.0, 0.8, 1.4, 0.0));
        s.add(HologramCapture.waitTicks(15));
        s.add(HologramCapture.screenshot("CB_21_scale_beside_player"));
        s.add(HologramCapture.run("first person", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON)));
        s.add(HologramCapture.command("gamemode spectator @s"));
        view(s, stage, "CB_22_dropped_item", 0.0, 1.1, -0.2, 0.0, 0.1, 1.0);
        s.add(here(stage, "kill @e[type=minecraft:item,distance=..20]"));
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(hud(true));
        return s;
    }

    private static int boardCount(double[] stage) {
        return serverNow(server -> server.overworld().getBlockEntity(block(stage, 0, 1, 4)) instanceof CuttingBoardBlockEntity board
                ? board.getIngredient().getCount() : -1);
    }

    /** Server board count and player's item count; also that the client's copy of the board agrees (sync). */
    private static Step boardCheck(double[] stage, String label, int onBoard, Item item, int inInventory) {
        return mc -> {
            int client = mc.level.getBlockEntity(block(stage, 0, 1, 4)) instanceof CuttingBoardBlockEntity b ? b.getIngredient().getCount() : -1;
            int server = boardCount(stage);
            int have = held(mc, item);
            HologramCapture.log((server == onBoard && client == onBoard && have == inInventory ? "PASS: " : "FAIL: ") + label
                    + " (server board " + server + ", client board " + client + ", inventory " + have + ")");
            return true;
        };
    }
}
