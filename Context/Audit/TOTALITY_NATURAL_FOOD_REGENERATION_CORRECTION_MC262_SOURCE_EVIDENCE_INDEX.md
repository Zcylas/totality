# MC 26.2 Source Evidence Index — Natural Food/Peaceful Health Regeneration Correction

**Purpose:** a concise, standalone citation record of the exact Minecraft 26.2 vanilla source this correction's two mixins depend on. Extracted directly from the Fabric Loom-cached sources jar for this project's exact resolved Minecraft version — not decompiled bytecode, not reconstructed from memory of any older Minecraft release.

**Source jar (local, Gradle-cached, not packaged into this bundle):**
`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-043a8b3edf/26.2/minecraft-merged-043a8b3edf-26.2-sources.jar`

**Extraction method:** `unzip` of the exact `.java` entries listed below, read in full. No other file in the jar was modified or is included in this bundle.

---

## `net/minecraft/world/food/FoodData.java`

Full method, `public void tick(final ServerPlayer player)`:

```java
public void tick(final ServerPlayer player) {
    ServerLevel level = player.level();
    Difficulty difficulty = level.getDifficulty();
    if (this.exhaustionLevel > 4.0F) {
        this.exhaustionLevel -= 4.0F;
        if (this.saturationLevel > 0.0F) {
            this.saturationLevel = Math.max(this.saturationLevel - 1.0F, 0.0F);
        } else if (difficulty != Difficulty.PEACEFUL) {
            this.foodLevel = Math.max(this.foodLevel - 1, 0);
        }
    }

    boolean naturalRegen = level.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
    if (naturalRegen && this.saturationLevel > 0.0F && player.isHurt() && this.foodLevel >= 20) {
        this.tickTimer++;
        if (this.tickTimer >= 10) {
            float saturationSpent = Math.min(this.saturationLevel, 6.0F);
            player.heal(saturationSpent / 6.0F);
            this.addExhaustion(saturationSpent);
            this.tickTimer = 0;
        }
    } else if (naturalRegen && this.foodLevel >= 18 && player.isHurt()) {
        this.tickTimer++;
        if (this.tickTimer >= 80) {
            player.heal(1.0F);
            this.addExhaustion(6.0F);
            this.tickTimer = 0;
        }
    } else if (this.foodLevel <= 0) {
        this.tickTimer++;
        if (this.tickTimer >= 80) {
            if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD
                    || player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
                player.hurtServer(level, player.damageSources().starve(), 1.0F);
            }
            this.tickTimer = 0;
        }
    } else {
        this.tickTimer = 0;
    }
}
```

Descriptor: `tick(Lnet/minecraft/server/level/ServerPlayer;)V`. Exactly one `boolean` local (`naturalRegen`) is declared in this method.

---

## `net/minecraft/world/entity/player/Player.java`

```java
public void aiStep() {
    ...
    this.tickRegeneration();
    ...
}

protected void tickRegeneration() {
}
```

`tickRegeneration()` is an empty, overridable hook called unconditionally once per tick from `aiStep()`.

---

## `net/minecraft/server/level/ServerPlayer.java`

```java
@Override
protected void tickRegeneration() {
    if (this.level().getDifficulty() == Difficulty.PEACEFUL
            && this.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION)) {
        if (this.tickCount % 20 == 0) {
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0F);
            }
            float saturation = this.foodData.getSaturationLevel();
            if (saturation < 20.0F) {
                this.foodData.setSaturation(saturation + 1.0F);
            }
        }
        if (this.tickCount % 10 == 0 && this.foodData.needsFood()) {
            this.foodData.setFoodLevel(this.foodData.getFoodLevel() + 1);
        }
    }
}
```

Descriptor: `tickRegeneration()V`, `protected`, `@Override` of `Player#tickRegeneration()`.

---

## `net/minecraft/world/entity/LivingEntity.java`

```java
public void heal(final float heal) {
    ...
}
```

Confirmed by grep as the *only* declaration of `heal(float)` anywhere in `LivingEntity`/`Player`/`ServerPlayer` — neither `Player` nor `ServerPlayer` overrides it. `this.heal(1.0F)` inside `ServerPlayer#tickRegeneration()` resolves to this method by inheritance; the `@Redirect` target owner used is `ServerPlayer` (the class actually being compiled/mixed into at the call site), matching this codebase's own established convention for inherited-method call-site interception (see `PlayerRestSleepMixin`/`LivingEntityRestSleepMixin`).
