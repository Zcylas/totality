package zcylas.totality.client.mining;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.mining.MiningTuning;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.init.ModKeybinds;
import zcylas.totality.networking.mining.PowerStrikeResultPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Power Mining HUD V2 scenes for the opt-in development capture run ({@link HologramCapture}); inert in
 * normal play. Survival, an iron pickaxe and a stone wall; the REAL input path (Attack key state plus the
 * modifier reported as held through {@link ModKeybinds#simulatePhysicalPress}), the real server power
 * swing, and the HUD's own diagnostics recorded every tick of a charge.
 */
public final class PowerMiningCapture {

    private PowerMiningCapture() {}

    static void registerIfRequested() {
        if (!HologramCapture.requested()) return;
        HologramCapture.addScene(35, scenes());
    }

    private record Sample(int ticks, float meter, float hud, String state, float offset) {}

    private static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("execute at @s run fill ~-5 ~ ~-3 ~5 ~5 ~7 minecraft:air"));
        s.add(HologramCapture.command("execute at @s run fill ~-5 ~-1 ~-3 ~5 ~-1 ~7 minecraft:grass_block"));
        s.add(HologramCapture.command("execute at @s run fill ~-3 ~ ~2 ~3 ~3 ~2 minecraft:stone"));
        s.add(HologramCapture.command("execute at @s run setblock ~-1 ~1 ~2 minecraft:iron_ore"));
        s.add(HologramCapture.command("execute at @s run setblock ~1 ~ ~2 minecraft:coal_ore"));
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(HologramCapture.command("item replace entity @s weapon.mainhand with minecraft:iron_pickaxe"));
        s.add(HologramCapture.look(0, 12));
        s.add(HologramCapture.waitTicks(40));

        // Alt alone: compact idle reticle, empty rail; no charge.
        s.add(alt(true));
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.screenshot("50_pm_idle"));
        s.add(HologramCapture.check("Power HUD: Alt alone shows the idle reticle, no charge",
                () -> "idle".equals(PowerMiningMeterHud.lastState()) && !ClientMiningController.isMeterActive()));

        // Alt + Left Click: White -> Green -> Orange -> Red at the real thresholds, then release at Red.
        List<Sample> samples = new ArrayList<>();
        int[] damageBefore = {0};
        s.add(HologramCapture.run("record tool damage", () -> damageBefore[0] = held().getDamageValue()));
        s.add(attack(true));
        s.add(charge(samples, 18, new int[] {4, 10, 14, 18}, new String[] {"51_pm_white", "52_pm_green", "53_pm_orange", "54_pm_red"}));
        s.add(check(samples));
        long[] released = {0};
        s.add(attack(false));
        s.add(HologramCapture.run("note release", () -> released[0] = ClientMiningController.lastReleaseNanos()));
        s.add(HologramCapture.screenshot("55_pm_release_discharge"));
        s.add(HologramCapture.until("strike result from the server", () -> ClientMiningController.lastStrikeNanos() >= released[0],
                () -> {}, 60));
        s.add(HologramCapture.screenshot("56_pm_impact"));
        s.add(HologramCapture.check("Power HUD: a landed Red strike shows the impact (server outcome DAMAGED/BROKEN)",
                () -> ClientMiningController.lastStrikeOutcome().landed() && "impact".equals(PowerMiningMeterHud.lastState())));
        s.add(HologramCapture.run("tool wear of the Red strike (existing rules, unchanged)", () -> HologramCapture.log(
                "info: iron pickaxe damage " + damageBefore[0] + " -> " + held().getDamageValue() + " after one Red power strike")));
        s.add(HologramCapture.waitTicks(30));

        // Miss: charge on the wall, turn to the sky, release — discharge only, never an impact.
        s.add(attack(true));
        s.add(HologramCapture.waitTicks(12));
        s.add(HologramCapture.look(0, -80));
        s.add(HologramCapture.waitTicks(2));
        String[] states = new String[1];
        s.add(attack(false));
        s.add(HologramCapture.run("note release", () -> {
            released[0] = ClientMiningController.lastReleaseNanos();   // the controller's own release instant
            states[0] = "";
        }));
        s.add(HologramCapture.screenshot("57_pm_miss_discharge"));
        s.add(watch(states, 30));
        s.add(HologramCapture.run("miss diagnostics", () -> HologramCapture.log("info: miss — result after release: "
                + (ClientMiningController.lastStrikeNanos() >= released[0]) + ", outcome " + ClientMiningController.lastStrikeOutcome()
                + ", HUD states: " + states[0].trim().replaceAll("(\\S+)( \\1)+", "$1…"))));
        s.add(HologramCapture.check("Power HUD: a miss never shows impact (server outcome MISS)",
                () -> ClientMiningController.lastStrikeNanos() >= released[0]
                        && ClientMiningController.lastStrikeOutcome() == PowerStrikeResultPayload.Outcome.MISS
                        && !states[0].contains("impact")));
        s.add(HologramCapture.look(0, 12));
        s.add(HologramCapture.waitTicks(20));

        // Cancel: Alt released before the click — the charge is void (existing mechanic).
        s.add(attack(true));
        s.add(HologramCapture.waitTicks(12));
        s.add(alt(false));
        s.add(HologramCapture.run("reset", () -> states[0] = ""));
        s.add(HologramCapture.screenshot("58_pm_cancelled"));
        s.add(watch(states, 3));
        s.add(HologramCapture.check("Power HUD: releasing Alt first cancels (brackets collapse, no strike)",
                () -> states[0].contains("cancelled") && !ClientMiningController.isMeterActive()));
        s.add(attack(false));
        s.add(HologramCapture.waitTicks(20));

        // Night and third person, charging to Orange.
        s.add(HologramCapture.command("time set 18000"));
        s.add(HologramCapture.command("execute at @s run fill ~-3 ~ ~2 ~3 ~3 ~2 minecraft:stone replace minecraft:air"));
        s.add(alt(true));
        s.add(HologramCapture.waitTicks(8));
        s.add(attack(true));
        s.add(HologramCapture.until("orange", () -> ClientMiningController.meterTicks() >= 14, () -> {}, 40));
        s.add(HologramCapture.screenshot("59_pm_orange_night"));
        s.add(attack(false));
        s.add(HologramCapture.waitTicks(30));
        // Earlier strikes may have broken the wall block under the crosshair: restore it.
        s.add(HologramCapture.command("execute at @s run fill ~-3 ~ ~2 ~3 ~3 ~2 minecraft:stone replace minecraft:air"));
        s.add(HologramCapture.run("third person", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK)));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.waitTicks(10));
        s.add(attack(true));
        s.add(HologramCapture.until("green", () -> ClientMiningController.meterTicks() >= 10, () -> {}, 40));
        s.add(HologramCapture.screenshot("59_pm_green_third_person"));
        s.add(attack(false));
        s.add(HologramCapture.waitTicks(30));
        s.add(HologramCapture.run("first person", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON)));
        s.add(HologramCapture.waitTicks(10));

        // Cave: a dark deepslate pocket, charging to Red against ore-flecked stone.
        double[] surface = new double[3];
        s.add(HologramCapture.run("remember the surface position", () -> {
            var p = Minecraft.getInstance().player;
            surface[0] = p.getX();
            surface[1] = p.getY();
            surface[2] = p.getZ();
        }));
        s.add(HologramCapture.command("execute at @s run fill ~-2 -41 ~-2 ~2 -38 ~1 minecraft:air"));
        s.add(HologramCapture.command("execute at @s run fill ~-2 -41 ~2 ~2 -38 ~2 minecraft:deepslate"));
        s.add(HologramCapture.command("execute at @s run setblock ~ -40 ~2 minecraft:deepslate_iron_ore"));
        s.add(HologramCapture.command("execute at @s run tp @s ~ -41 ~ 0 12"));
        s.add(HologramCapture.waitTicks(40));
        s.add(attack(true));
        s.add(HologramCapture.until("red", () -> ClientMiningController.meterTicks() >= 17, () -> {}, 40));
        s.add(HologramCapture.screenshot("59_pm_red_cave"));
        s.add(attack(false));
        s.add(HologramCapture.waitTicks(30));
        s.add(alt(false));
        s.add(mc -> HologramCapture.command(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f 0 12", surface[0], surface[1], surface[2])).tick(mc));
        s.add(HologramCapture.waitTicks(30));
        return s;
    }

    private static ItemStack held() {
        return Minecraft.getInstance().player.getMainHandItem();
    }

    static Step alt(boolean down) {
        return HologramCapture.run((down ? "hold" : "release") + " the modifier (Alt)",
                () -> ModKeybinds.simulatePhysicalPress(ModKeybinds.RADIAL_MODIFIER, down));
    }

    static Step attack(boolean down) {
        return HologramCapture.run((down ? "hold" : "release") + " Attack (left click)",
                () -> Minecraft.getInstance().options.keyAttack.setDown(down));
    }

    /** Collects the HUD states seen over {@code ticks} ticks. */
    private static Step watch(String[] states, int ticks) {
        int[] t = {0};
        return mc -> {
            states[0] += PowerMiningMeterHud.lastState() + " ";
            return ++t[0] >= ticks;
        };
    }

    /** Records every tick of the charge; screenshots at the given hold ticks. */
    private static Step charge(List<Sample> samples, int until, int[] shotTicks, String[] shots) {
        return mc -> {
            int ticks = ClientMiningController.meterTicks();
            if (ClientMiningController.isMeterActive()) {
                samples.add(new Sample(ticks, ClientMiningController.meterValue(), PowerMiningMeterHud.lastCharge(),
                        PowerMiningMeterHud.lastState(), PowerMiningMeterHud.lastBracketOffset()));
                for (int i = 0; i < shotTicks.length; i++) {
                    if (ticks == shotTicks[i]) HologramCapture.screenshot(shots[i]).tick(mc);
                }
            }
            return ticks >= until || samples.size() > 60;
        };
    }

    /**
     * The HUD reads the gameplay charge (never its own timer), shows the band the SAME function resolves
     * for it, and its brackets only move outward while the charge rises.
     */
    private static Step check(List<Sample> samples) {
        return mc -> {
            boolean sameState = true, outward = true, sawAll = true;
            StringBuilder firsts = new StringBuilder();
            String prev = "";
            Sample last = null;
            for (Sample x : samples) {
                if (!x.state().startsWith("charging_")) continue;     // first frame may not be drawn yet
                String expected = "charging_" + PowerMiningMeterHud.bandName(MiningTuning.presentationBand(x.hud(), false));
                if (!expected.equals(x.state()) || Math.abs(x.hud() - x.meter()) > 1f / MiningTuning.METER_PERIOD_TICKS * 2 + 1e-4f) {
                    sameState = false;
                }
                if (last != null && x.meter() > last.meter() && x.offset() + 1e-4f < last.offset()) outward = false;
                if (!x.state().equals(prev)) {
                    firsts.append(String.format(Locale.ROOT, "%s from tick %d (charge %.2f); ", x.state(), x.ticks(), x.hud()));
                    prev = x.state();
                }
                last = x;
            }
            for (String band : new String[] {"white", "green", "orange", "red"}) {
                if (firsts.indexOf("charging_" + band) < 0) sawAll = false;
            }
            HologramCapture.log((sameState ? "PASS" : "FAIL") + ": Power HUD: brackets and rail read the gameplay charge; band = presentationBand(charge) on every frame ("
                    + samples.size() + " ticks)");
            HologramCapture.log((outward ? "PASS" : "FAIL") + ": Power HUD: brackets move outward as the charge rises");
            HologramCapture.log((sawAll ? "PASS" : "FAIL") + ": Power HUD: White -> Green -> Orange -> Red at the real thresholds ("
                    + MiningTuning.WHITE_ZONE_MAX + " / " + MiningTuning.GREEN_ZONE_MAX + " / " + MiningTuning.RED_ZONE + "): " + firsts);
            return true;
        };
    }
}
