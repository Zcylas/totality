# Appendix — Totality mining runtime table and reproduction harness (2026-09-21)

Supporting material for `TOTALITY_UNIFIED_CODE_AND_MINING_BASELINE_AUDIT_2026-09-21.md`. All numbers were produced by running the **real** `BlockDurability`, `PlayerMiningPower`, `MiningTuning` and `PlayerMiningManager.strike` code of `master` @ `7213407` inside a **scratch copy of the repository outside the working tree** (one extra class, below, registered from the copy's `Totality.java`). The repository itself was not modified.

Stats used: **STR 10, DEX 10** (hand rows: **DEX 12**), on ground, Efficiency modelled as the attribute modifier `N²+1` (as vanilla's enchantment), `eligible=false` = source Tier below the block's Required Mining Tier (INEFFECTIVE). `ticks = windUp + (strikes−1) × cycle`.

## Drop / eligibility reproduction (real `ItemEntity`s, chunk near spawn)

```
STONE        hand   STR10 DEX10 | first=INEFFECTIVE (tier 0 < 1)                       drops=[]
STONE        hand   STR14 DEX12 | DAMAGED x34 -> BROKEN, HarvestGrant served=1        drops=[1x cobblestone]
COBBLESTONE  hand   STR10 DEX10 | first=INEFFECTIVE                                    drops=[]
COBBLESTONE  hand   STR14 DEX12 | DAMAGED x45 -> BROKEN, HarvestGrant served=1        drops=[1x cobblestone]
STONE        iron pickaxe (control) | 3 strikes -> BROKEN                              drops=[1x cobblestone]
COBBLESTONE  iron pickaxe (control) | 4 strikes -> BROKEN                              drops=[1x cobblestone]
OAK_LOG hand STR14 | BROKEN, drops=[1x oak_log]      DIRT hand | BROKEN, drops=[1x dirt]
NETHERRACK hand STR14 DEX12 | BROKEN, drops=[1x netherrack]
DEEPSLATE hand STR40 DEX12 | BROKEN, drops=[1x cobbled_deepslate]
```

## Bare hand vs Obsidian (DEX 28 = tier 5)

STR 10: 3334 strikes · STR 20: 556 · STR 50: 159 · STR 100: 73.

## Cadence (Netherite Pickaxe on Obsidian)

none: ratio 1.00 → cycle 10 · Haste I: 1.20 → 9 · Haste II: 1.40 → 7 · Mining Fatigue I: 0.30 → 33.

## Full table (18 blocks × tools, Efficiency 0 / 5 where shown)

```
TABLE STONE             hand      eff0 | hard=1.50 maxDur=100.0 reqTier=1 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=100 ticks=993 sec=49.65
TABLE STONE             wood      eff0 | hard=1.50 maxDur=100.0 reqTier=1 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=9 ticks=83 sec=4.15
TABLE STONE             stone     eff0 | hard=1.50 maxDur=100.0 reqTier=1 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=5 ticks=43 sec=2.15
TABLE STONE             copper    eff0 | hard=1.50 maxDur=100.0 reqTier=1 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=4 ticks=33 sec=1.65
TABLE STONE             iron      eff0 | hard=1.50 maxDur=100.0 reqTier=1 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=3 ticks=23 sec=1.15
TABLE STONE             iron      eff5 | hard=1.50 maxDur=100.0 reqTier=1 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=true strikes=3 ticks=8 sec=0.40
TABLE STONE             diamond   eff0 | hard=1.50 maxDur=100.0 reqTier=1 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=3 ticks=23 sec=1.15
TABLE STONE             diamond   eff5 | hard=1.50 maxDur=100.0 reqTier=1 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=3 ticks=10 sec=0.50
TABLE STONE             netherite eff0 | hard=1.50 maxDur=100.0 reqTier=1 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=2 ticks=13 sec=0.65
TABLE STONE             netherite eff5 | hard=1.50 maxDur=100.0 reqTier=1 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=2 ticks=6 sec=0.30
TABLE COBBLESTONE       hand      eff0 | hard=2.00 maxDur=133.3 reqTier=1 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=134 ticks=1333 sec=66.65
TABLE COBBLESTONE       wood      eff0 | hard=2.00 maxDur=133.3 reqTier=1 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=12 ticks=113 sec=5.65
TABLE COBBLESTONE       stone     eff0 | hard=2.00 maxDur=133.3 reqTier=1 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE COBBLESTONE       copper    eff0 | hard=2.00 maxDur=133.3 reqTier=1 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=5 ticks=43 sec=2.15
TABLE COBBLESTONE       iron      eff0 | hard=2.00 maxDur=133.3 reqTier=1 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=4 ticks=33 sec=1.65
TABLE COBBLESTONE       iron      eff5 | hard=2.00 maxDur=133.3 reqTier=1 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=true strikes=4 ticks=11 sec=0.55
TABLE COBBLESTONE       diamond   eff0 | hard=2.00 maxDur=133.3 reqTier=1 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=3 ticks=23 sec=1.15
TABLE COBBLESTONE       diamond   eff5 | hard=2.00 maxDur=133.3 reqTier=1 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=3 ticks=10 sec=0.50
TABLE COBBLESTONE       netherite eff0 | hard=2.00 maxDur=133.3 reqTier=1 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=3 ticks=23 sec=1.15
TABLE COBBLESTONE       netherite eff5 | hard=2.00 maxDur=133.3 reqTier=1 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=3 ticks=10 sec=0.50
TABLE DEEPSLATE         hand      eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=200 ticks=1993 sec=99.65
TABLE DEEPSLATE         wood      eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=17 ticks=163 sec=8.15
TABLE DEEPSLATE         stone     eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=9 ticks=83 sec=4.15
TABLE DEEPSLATE         copper    eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=7 ticks=63 sec=3.15
TABLE DEEPSLATE         iron      eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE DEEPSLATE         iron      eff5 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=true strikes=6 ticks=17 sec=0.85
TABLE DEEPSLATE         diamond   eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=5 ticks=43 sec=2.15
TABLE DEEPSLATE         diamond   eff5 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=5 ticks=18 sec=0.90
TABLE DEEPSLATE         netherite eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=4 ticks=33 sec=1.65
TABLE DEEPSLATE         netherite eff5 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=4 ticks=14 sec=0.70
TABLE COBBLED_DEEPSLATE hand      eff0 | hard=3.50 maxDur=233.3 reqTier=1 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=234 ticks=2333 sec=116.65
TABLE COBBLED_DEEPSLATE wood      eff0 | hard=3.50 maxDur=233.3 reqTier=1 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=20 ticks=193 sec=9.65
TABLE COBBLED_DEEPSLATE stone     eff0 | hard=3.50 maxDur=233.3 reqTier=1 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=10 ticks=93 sec=4.65
TABLE COBBLED_DEEPSLATE copper    eff0 | hard=3.50 maxDur=233.3 reqTier=1 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=8 ticks=73 sec=3.65
TABLE COBBLED_DEEPSLATE iron      eff0 | hard=3.50 maxDur=233.3 reqTier=1 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=7 ticks=63 sec=3.15
TABLE COBBLED_DEEPSLATE iron      eff5 | hard=3.50 maxDur=233.3 reqTier=1 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=true strikes=7 ticks=20 sec=1.00
TABLE COBBLED_DEEPSLATE diamond   eff0 | hard=3.50 maxDur=233.3 reqTier=1 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=5 ticks=43 sec=2.15
TABLE COBBLED_DEEPSLATE diamond   eff5 | hard=3.50 maxDur=233.3 reqTier=1 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=5 ticks=18 sec=0.90
TABLE COBBLED_DEEPSLATE netherite eff0 | hard=3.50 maxDur=233.3 reqTier=1 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=5 ticks=43 sec=2.15
TABLE COBBLED_DEEPSLATE netherite eff5 | hard=3.50 maxDur=233.3 reqTier=1 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=5 ticks=18 sec=0.90
TABLE DIRT              hand      eff0 | hard=0.50 maxDur=33.3 reqTier=0 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=34 ticks=333 sec=16.65
TABLE DIRT              wood      eff0 | hard=0.50 maxDur=33.3 reqTier=0 | srcTier=1 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE DIRT              stone     eff0 | hard=0.50 maxDur=33.3 reqTier=0 | srcTier=2 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE DIRT              copper    eff0 | hard=0.50 maxDur=33.3 reqTier=0 | srcTier=2 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE DIRT              iron      eff0 | hard=0.50 maxDur=33.3 reqTier=0 | srcTier=3 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE DIRT              iron      eff5 | hard=0.50 maxDur=33.3 reqTier=0 | srcTier=3 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE DIRT              diamond   eff0 | hard=0.50 maxDur=33.3 reqTier=0 | srcTier=4 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE DIRT              diamond   eff5 | hard=0.50 maxDur=33.3 reqTier=0 | srcTier=4 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE DIRT              netherite eff0 | hard=0.50 maxDur=33.3 reqTier=0 | srcTier=4 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE DIRT              netherite eff5 | hard=0.50 maxDur=33.3 reqTier=0 | srcTier=4 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE NETHERRACK        hand      eff0 | hard=0.40 maxDur=26.7 reqTier=1 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=27 ticks=263 sec=13.15
TABLE NETHERRACK        wood      eff0 | hard=0.40 maxDur=26.7 reqTier=1 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=3 ticks=23 sec=1.15
TABLE NETHERRACK        stone     eff0 | hard=0.40 maxDur=26.7 reqTier=1 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=2 ticks=13 sec=0.65
TABLE NETHERRACK        copper    eff0 | hard=0.40 maxDur=26.7 reqTier=1 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=1 ticks=3 sec=0.15
TABLE NETHERRACK        iron      eff0 | hard=0.40 maxDur=26.7 reqTier=1 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=1 ticks=3 sec=0.15
TABLE NETHERRACK        iron      eff5 | hard=0.40 maxDur=26.7 reqTier=1 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=true strikes=1 ticks=2 sec=0.10
TABLE NETHERRACK        diamond   eff0 | hard=0.40 maxDur=26.7 reqTier=1 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=1 ticks=3 sec=0.15
TABLE NETHERRACK        diamond   eff5 | hard=0.40 maxDur=26.7 reqTier=1 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=1 ticks=2 sec=0.10
TABLE NETHERRACK        netherite eff0 | hard=0.40 maxDur=26.7 reqTier=1 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=1 ticks=3 sec=0.15
TABLE NETHERRACK        netherite eff5 | hard=0.40 maxDur=26.7 reqTier=1 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=1 ticks=2 sec=0.10
TABLE COAL_ORE          hand      eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=200 ticks=1993 sec=99.65
TABLE COAL_ORE          wood      eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=17 ticks=163 sec=8.15
TABLE COAL_ORE          stone     eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=9 ticks=83 sec=4.15
TABLE COAL_ORE          copper    eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=7 ticks=63 sec=3.15
TABLE COAL_ORE          iron      eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE COAL_ORE          iron      eff5 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=true strikes=6 ticks=17 sec=0.85
TABLE COAL_ORE          diamond   eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=5 ticks=43 sec=2.15
TABLE COAL_ORE          diamond   eff5 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=5 ticks=18 sec=0.90
TABLE COAL_ORE          netherite eff0 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=4 ticks=33 sec=1.65
TABLE COAL_ORE          netherite eff5 | hard=3.00 maxDur=200.0 reqTier=1 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=4 ticks=14 sec=0.70
TABLE IRON_ORE          hand      eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE IRON_ORE          wood      eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE IRON_ORE          stone     eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=9 ticks=83 sec=4.15
TABLE IRON_ORE          copper    eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=7 ticks=63 sec=3.15
TABLE IRON_ORE          iron      eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE IRON_ORE          iron      eff5 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=true strikes=6 ticks=17 sec=0.85
TABLE IRON_ORE          diamond   eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=5 ticks=43 sec=2.15
TABLE IRON_ORE          diamond   eff5 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=5 ticks=18 sec=0.90
TABLE IRON_ORE          netherite eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=4 ticks=33 sec=1.65
TABLE IRON_ORE          netherite eff5 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=4 ticks=14 sec=0.70
TABLE COPPER_ORE        hand      eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE COPPER_ORE        wood      eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE COPPER_ORE        stone     eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=9 ticks=83 sec=4.15
TABLE COPPER_ORE        copper    eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=7 ticks=63 sec=3.15
TABLE COPPER_ORE        iron      eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE COPPER_ORE        iron      eff5 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=true strikes=6 ticks=17 sec=0.85
TABLE COPPER_ORE        diamond   eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=5 ticks=43 sec=2.15
TABLE COPPER_ORE        diamond   eff5 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=5 ticks=18 sec=0.90
TABLE COPPER_ORE        netherite eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=4 ticks=33 sec=1.65
TABLE COPPER_ORE        netherite eff5 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=4 ticks=14 sec=0.70
TABLE GOLD_ORE          hand      eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE GOLD_ORE          wood      eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE GOLD_ORE          stone     eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE GOLD_ORE          copper    eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE GOLD_ORE          iron      eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE GOLD_ORE          iron      eff5 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=true strikes=6 ticks=17 sec=0.85
TABLE GOLD_ORE          diamond   eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=5 ticks=43 sec=2.15
TABLE GOLD_ORE          diamond   eff5 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=5 ticks=18 sec=0.90
TABLE GOLD_ORE          netherite eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=4 ticks=33 sec=1.65
TABLE GOLD_ORE          netherite eff5 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=4 ticks=14 sec=0.70
TABLE REDSTONE_ORE      hand      eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE REDSTONE_ORE      wood      eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE REDSTONE_ORE      stone     eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE REDSTONE_ORE      copper    eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE REDSTONE_ORE      iron      eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE REDSTONE_ORE      iron      eff5 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=true strikes=6 ticks=17 sec=0.85
TABLE REDSTONE_ORE      diamond   eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=5 ticks=43 sec=2.15
TABLE REDSTONE_ORE      diamond   eff5 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=5 ticks=18 sec=0.90
TABLE REDSTONE_ORE      netherite eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=4 ticks=33 sec=1.65
TABLE REDSTONE_ORE      netherite eff5 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=4 ticks=14 sec=0.70
TABLE LAPIS_ORE         hand      eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE LAPIS_ORE         wood      eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE LAPIS_ORE         stone     eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=9 ticks=83 sec=4.15
TABLE LAPIS_ORE         copper    eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=7 ticks=63 sec=3.15
TABLE LAPIS_ORE         iron      eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE LAPIS_ORE         iron      eff5 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=true strikes=6 ticks=17 sec=0.85
TABLE LAPIS_ORE         diamond   eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=5 ticks=43 sec=2.15
TABLE LAPIS_ORE         diamond   eff5 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=5 ticks=18 sec=0.90
TABLE LAPIS_ORE         netherite eff0 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=4 ticks=33 sec=1.65
TABLE LAPIS_ORE         netherite eff5 | hard=3.00 maxDur=200.0 reqTier=2 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=4 ticks=14 sec=0.70
TABLE DIAMOND_ORE       hand      eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE DIAMOND_ORE       wood      eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE DIAMOND_ORE       stone     eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE DIAMOND_ORE       copper    eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE DIAMOND_ORE       iron      eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE DIAMOND_ORE       iron      eff5 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=true strikes=6 ticks=17 sec=0.85
TABLE DIAMOND_ORE       diamond   eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=5 ticks=43 sec=2.15
TABLE DIAMOND_ORE       diamond   eff5 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=5 ticks=18 sec=0.90
TABLE DIAMOND_ORE       netherite eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=4 ticks=33 sec=1.65
TABLE DIAMOND_ORE       netherite eff5 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=4 ticks=14 sec=0.70
TABLE EMERALD_ORE       hand      eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE EMERALD_ORE       wood      eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE EMERALD_ORE       stone     eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE EMERALD_ORE       copper    eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE EMERALD_ORE       iron      eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=6 ticks=53 sec=2.65
TABLE EMERALD_ORE       iron      eff5 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=true strikes=6 ticks=17 sec=0.85
TABLE EMERALD_ORE       diamond   eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=5 ticks=43 sec=2.15
TABLE EMERALD_ORE       diamond   eff5 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=5 ticks=18 sec=0.90
TABLE EMERALD_ORE       netherite eff0 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=4 ticks=33 sec=1.65
TABLE EMERALD_ORE       netherite eff5 | hard=3.00 maxDur=200.0 reqTier=3 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=4 ticks=14 sec=0.70
TABLE ANCIENT_DEBRIS    hand      eff0 | hard=30.00 maxDur=2000.0 reqTier=4 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE ANCIENT_DEBRIS    wood      eff0 | hard=30.00 maxDur=2000.0 reqTier=4 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE ANCIENT_DEBRIS    stone     eff0 | hard=30.00 maxDur=2000.0 reqTier=4 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE ANCIENT_DEBRIS    copper    eff0 | hard=30.00 maxDur=2000.0 reqTier=4 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE ANCIENT_DEBRIS    iron      eff0 | hard=30.00 maxDur=2000.0 reqTier=4 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE ANCIENT_DEBRIS    iron      eff5 | hard=30.00 maxDur=2000.0 reqTier=4 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE ANCIENT_DEBRIS    diamond   eff0 | hard=30.00 maxDur=2000.0 reqTier=4 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=42 ticks=413 sec=20.65
TABLE ANCIENT_DEBRIS    diamond   eff5 | hard=30.00 maxDur=2000.0 reqTier=4 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=42 ticks=166 sec=8.30
TABLE ANCIENT_DEBRIS    netherite eff0 | hard=30.00 maxDur=2000.0 reqTier=4 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=38 ticks=373 sec=18.65
TABLE ANCIENT_DEBRIS    netherite eff5 | hard=30.00 maxDur=2000.0 reqTier=4 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=38 ticks=150 sec=7.50
TABLE OBSIDIAN          hand      eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE OBSIDIAN          wood      eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE OBSIDIAN          stone     eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE OBSIDIAN          copper    eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE OBSIDIAN          iron      eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE OBSIDIAN          iron      eff5 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE OBSIDIAN          diamond   eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=70 ticks=693 sec=34.65
TABLE OBSIDIAN          diamond   eff5 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=70 ticks=278 sec=13.90
TABLE OBSIDIAN          netherite eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=62 ticks=613 sec=30.65
TABLE OBSIDIAN          netherite eff5 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=62 ticks=246 sec=12.30
TABLE CRYING_OBSIDIAN   hand      eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE CRYING_OBSIDIAN   wood      eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=1 dmg=12.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE CRYING_OBSIDIAN   stone     eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=2 dmg=24.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE CRYING_OBSIDIAN   copper    eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=2 dmg=30.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE CRYING_OBSIDIAN   iron      eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=3 dmg=36.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE CRYING_OBSIDIAN   iron      eff5 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=3 dmg=36.0 | ratio=5.33 wind=2 rec=1 cycle=3 | eligible=false strikes=-1 ticks=-1 sec=-0.05
TABLE CRYING_OBSIDIAN   diamond   eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=4 dmg=48.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=70 ticks=693 sec=34.65
TABLE CRYING_OBSIDIAN   diamond   eff5 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=4 dmg=48.0 | ratio=4.25 wind=2 rec=2 cycle=4 | eligible=true strikes=70 ticks=278 sec=13.90
TABLE CRYING_OBSIDIAN   netherite eff0 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=4 dmg=54.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=62 ticks=613 sec=30.65
TABLE CRYING_OBSIDIAN   netherite eff5 | hard=50.00 maxDur=3333.3 reqTier=4 | srcTier=4 dmg=54.0 | ratio=3.89 wind=2 rec=2 cycle=4 | eligible=true strikes=62 ticks=246 sec=12.30
TABLE OAK_LOG           hand      eff0 | hard=2.00 maxDur=133.3 reqTier=0 | srcTier=1 dmg=1.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=134 ticks=1333 sec=66.65
TABLE OAK_LOG           wood      eff0 | hard=2.00 maxDur=133.3 reqTier=0 | srcTier=1 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=23 ticks=223 sec=11.15
TABLE OAK_LOG           stone     eff0 | hard=2.00 maxDur=133.3 reqTier=0 | srcTier=2 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=23 ticks=223 sec=11.15
TABLE OAK_LOG           copper    eff0 | hard=2.00 maxDur=133.3 reqTier=0 | srcTier=2 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=23 ticks=223 sec=11.15
TABLE OAK_LOG           iron      eff0 | hard=2.00 maxDur=133.3 reqTier=0 | srcTier=3 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=23 ticks=223 sec=11.15
TABLE OAK_LOG           iron      eff5 | hard=2.00 maxDur=133.3 reqTier=0 | srcTier=3 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=23 ticks=223 sec=11.15
TABLE OAK_LOG           diamond   eff0 | hard=2.00 maxDur=133.3 reqTier=0 | srcTier=4 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=23 ticks=223 sec=11.15
TABLE OAK_LOG           diamond   eff5 | hard=2.00 maxDur=133.3 reqTier=0 | srcTier=4 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=23 ticks=223 sec=11.15
TABLE OAK_LOG           netherite eff0 | hard=2.00 maxDur=133.3 reqTier=0 | srcTier=4 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=23 ticks=223 sec=11.15
TABLE OAK_LOG           netherite eff5 | hard=2.00 maxDur=133.3 reqTier=0 | srcTier=4 dmg=6.0 | ratio=1.00 wind=3 rec=7 cycle=10 | eligible=true strikes=23 ticks=223 sec=11.15
```

## Harness source (scratch copy only; NOT part of the repository)

```java
package zcylas.totality.api.mining;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.StatsComponents;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;

/** SCRATCH-COPY ONLY (audit). Not part of the repository. */
public final class AuditRepro {
    public static void register() { ServerLifecycleEvents.SERVER_STARTED.register(AuditRepro::run); }

    static void score(ServerPlayer p, AbilityScore a, int v) {
        var st = StatsComponents.getStats(p); st.setSpentPointsDirectly(a, v - 10); st.recalculate();
    }
    static BlockHitResult hit(BlockPos pos) { return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false); }
    static List<ItemEntity> items(ServerLevel l, BlockPos pos) {
        List<ItemEntity> out = new ArrayList<>();
        for (var e : l.getAllEntities()) if (e instanceof ItemEntity i && i.blockPosition().distManhattan(pos) <= 6) out.add(i);
        return out;
    }
    static void log(String s) { Totality.LOGGER.info("[AUDIT] {}", s); }

    static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos pos = new BlockPos(0, level.getMaxY() - 8, 0);
        level.getChunkAt(pos);
        BlockState original = level.getBlockState(pos);
        ServerPlayer p = TotalityFakePlayer.create(level, "[audit]");
        p.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        p.setXRot(90f); p.setOnGround(true);
        try {
            // ---------- 1. eligibility / drops through the real strike path ----------
            Object[][] cases = {
                    {"STONE", Blocks.STONE, "hand", ItemStack.EMPTY, 10, 10},
                    {"STONE", Blocks.STONE, "hand", ItemStack.EMPTY, 14, 12},
                    {"COBBLESTONE", Blocks.COBBLESTONE, "hand", ItemStack.EMPTY, 10, 10},
                    {"COBBLESTONE", Blocks.COBBLESTONE, "hand", ItemStack.EMPTY, 14, 12},
                    {"STONE", Blocks.STONE, "iron pickaxe (control)", new ItemStack(Items.IRON_PICKAXE), 10, 10},
                    {"COBBLESTONE", Blocks.COBBLESTONE, "iron pickaxe (control)", new ItemStack(Items.IRON_PICKAXE), 10, 10},
                    {"OAK_LOG", Blocks.OAK_LOG, "hand", ItemStack.EMPTY, 14, 10},
                    {"DIRT", Blocks.DIRT, "hand", ItemStack.EMPTY, 10, 10},
                    {"NETHERRACK", Blocks.NETHERRACK, "hand", ItemStack.EMPTY, 14, 12},
                    {"DEEPSLATE", Blocks.DEEPSLATE, "hand", ItemStack.EMPTY, 40, 12},
            };
            for (Object[] c : cases) {
                Block b = (Block) c[1];
                level.setBlockAndUpdate(pos, b.defaultBlockState());
                var storage = BlockDamageStorage.get(level);
                var e0 = storage.get(pos, level.getBlockState(pos)); if (e0 != null) storage.remove(e0);
                for (var i : items(level, pos)) i.discard();
                p.setItemSlot(EquipmentSlot.MAINHAND, ((ItemStack) c[3]).copy());
                score(p, AbilityScore.STR, (Integer) c[4]);
                score(p, AbilityScore.DEX, (Integer) c[5]);
                var dur = BlockDurability.resolve(level, pos, b.defaultBlockState());
                int served0 = HarvestGrant.served();
                MiningResult r = null; int n = 0; String first = "";
                for (; n < 600; n++) {
                    r = PlayerMiningManager.strike(p, hit(pos), false, 0f);
                    if (n == 0) first = r.outcome() + "";
                    if (r.outcome() != MiningResult.Outcome.DAMAGED) break;
                }
                List<String> drops = new ArrayList<>();
                for (var i : items(level, pos)) drops.add(i.getItem().getCount() + "x " + i.getItem().getItem());
                log(String.format("DROPCASE %-12s %-24s STR%-3d DEX%-3d | maxDur=%.1f reqTier=%d | first=%s strikes=%d last=%s | grantServed=%d | drops=%s",
                        c[0], c[2], c[4], c[5], dur.max(), dur.requiredTier(), first, n + 1, r.outcome(), HarvestGrant.served() - served0, drops));
            }

            // ---------- 2. numeric table via the real classes (STR 10 / DEX 10 unless stated) ----------
            score(p, AbilityScore.STR, 10); score(p, AbilityScore.DEX, 10);
            Object[][] blocks = {
                    {"STONE", Blocks.STONE}, {"COBBLESTONE", Blocks.COBBLESTONE}, {"DEEPSLATE", Blocks.DEEPSLATE},
                    {"COBBLED_DEEPSLATE", Blocks.COBBLED_DEEPSLATE}, {"DIRT", Blocks.DIRT}, {"NETHERRACK", Blocks.NETHERRACK},
                    {"COAL_ORE", Blocks.COAL_ORE}, {"IRON_ORE", Blocks.IRON_ORE}, {"COPPER_ORE", Blocks.COPPER_ORE},
                    {"GOLD_ORE", Blocks.GOLD_ORE}, {"REDSTONE_ORE", Blocks.REDSTONE_ORE}, {"LAPIS_ORE", Blocks.LAPIS_ORE},
                    {"DIAMOND_ORE", Blocks.DIAMOND_ORE}, {"EMERALD_ORE", Blocks.EMERALD_ORE}, {"ANCIENT_DEBRIS", Blocks.ANCIENT_DEBRIS},
                    {"OBSIDIAN", Blocks.OBSIDIAN}, {"CRYING_OBSIDIAN", Blocks.CRYING_OBSIDIAN}, {"OAK_LOG", Blocks.OAK_LOG}};
            Object[][] tools = {
                    {"hand", ItemStack.EMPTY}, {"wood", new ItemStack(Items.WOODEN_PICKAXE)}, {"stone", new ItemStack(Items.STONE_PICKAXE)},
                    {"copper", new ItemStack(Items.COPPER_PICKAXE)}, {"iron", new ItemStack(Items.IRON_PICKAXE)},
                    {"diamond", new ItemStack(Items.DIAMOND_PICKAXE)}, {"netherite", new ItemStack(Items.NETHERITE_PICKAXE)}};
            var eff = p.getAttribute(Attributes.MINING_EFFICIENCY);
            var effId = net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "audit_eff");
            for (Object[] bl : blocks) {
                Block b = (Block) bl[0 + 1];
                level.setBlockAndUpdate(pos, b.defaultBlockState());
                var dur = BlockDurability.resolve(level, pos, b.defaultBlockState());
                for (Object[] t : tools) {
                    for (int effLevel : new int[]{0, 5}) {
                        if (effLevel == 5 && !(t[0].equals("diamond") || t[0].equals("netherite") || t[0].equals("iron"))) continue;
                        p.setItemSlot(EquipmentSlot.MAINHAND, ((ItemStack) t[1]).copy());
                        eff.removeModifier(effId);
                        if (effLevel > 0) eff.addTransientModifier(new AttributeModifier(effId, effLevel * effLevel + 1.0, AttributeModifier.Operation.ADD_VALUE));
                        int dexUse = t[0].equals("hand") ? 12 : 10;
                        score(p, AbilityScore.DEX, dexUse);
                        var res = PlayerMiningPower.compute(p, b.defaultBlockState(), 0f);
                        float ratio = PlayerMiningPower.cadenceRatio(p, b.defaultBlockState());
                        int dur6 = p.getMainHandItem().getSwingAnimation().duration();
                        int wind = MiningTuning.windUpTicks(dur6, false, ratio), rec = MiningTuning.recoveryTicks(ratio);
                        boolean eligible = res.tier() >= dur.requiredTier();
                        int strikes = eligible ? (int) Math.ceil(dur.max() / res.damage()) : -1;
                        int ticks = eligible ? wind + (strikes - 1) * (wind + rec) : -1;
                        log(String.format("TABLE %-17s %-9s eff%d | hard=%.2f maxDur=%.1f reqTier=%d | srcTier=%d dmg=%.1f | ratio=%.2f wind=%d rec=%d cycle=%d | eligible=%s strikes=%d ticks=%d sec=%.2f",
                                bl[0], t[0], effLevel, b.defaultBlockState().getDestroySpeed(level, pos), dur.max(), dur.requiredTier(),
                                res.tier(), res.damage(), ratio, wind, rec, wind + rec, eligible, strikes, ticks, ticks / 20f));
                    }
                }
                eff.removeModifier(effId);
            }
            // ---------- 3. bare hand high STR: obsidian ----------
            for (int str : new int[]{10, 20, 50, 100}) {
                score(p, AbilityScore.STR, str); score(p, AbilityScore.DEX, 28);
                p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                var res = PlayerMiningPower.compute(p, Blocks.OBSIDIAN.defaultBlockState(), 0f);
                log(String.format("HANDOBS STR%d DEX28 obsidian dmg=%.1f tier=%d strikes=%d", str, res.damage(), res.tier(), (int) Math.ceil(3333.33f / res.damage())));
            }
            // ---------- 4. haste / fatigue cadence on netherite obsidian ----------
            score(p, AbilityScore.STR, 10);
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_PICKAXE));
            for (String name : new String[]{"none", "haste1", "haste2", "fatigue1"}) {
                p.removeAllEffects(); eff.removeModifier(effId);
                if (name.equals("haste1")) p.addEffect(new MobEffectInstance(MobEffects.HASTE, 4000, 0));
                if (name.equals("haste2")) p.addEffect(new MobEffectInstance(MobEffects.HASTE, 4000, 1));
                if (name.equals("fatigue1")) p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 4000, 0));
                float ratio = PlayerMiningPower.cadenceRatio(p, Blocks.OBSIDIAN.defaultBlockState());
                log(String.format("CADENCE netherite/obsidian %-8s ratio=%.3f cycle=%d", name, ratio, MiningTuning.cycleTicks(6, ratio)));
            }
            p.removeAllEffects();
        } catch (Throwable t) {
            Totality.LOGGER.error("[AUDIT] failure", t);
        } finally {
            for (var i : items(level, pos)) i.discard();
            level.setBlockAndUpdate(pos, original);
            log("DONE");
        }
    }
}

```
