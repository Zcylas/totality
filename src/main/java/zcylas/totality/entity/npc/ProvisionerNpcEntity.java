package zcylas.totality.entity.npc;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.shop.MerchantRuntime;
import zcylas.totality.api.shop.MerchantRuntimeRegistry;
import zcylas.totality.api.shop.MerchantStockEntry;
import zcylas.totality.api.shop.MerchantStockProvider;
import zcylas.totality.api.shop.assortment.ProvisionerAssortmentPool;
import zcylas.totality.api.shop.assortment.ProvisionerAssortmentRegistry;
import zcylas.totality.init.ModTags;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The first real Provisioner NPC (design document Section 8, Phase 3) — a thin
 * {@link TotalityNpcEntity} subclass, following the {@link BankerNpcEntity} precedent, extended
 * with exactly the persisted state a Provisioner genuinely needs on top of the generic
 * random-identity NPC: its own business Credits ({@link MerchantRuntime}) and its own,
 * roll-once, persistent BUY stock ({@link MerchantStockProvider}).
 *
 * <p>Identity (random gender + name) reuses {@link TotalityNpcEntity#finalizeSpawn} unchanged —
 * this class does not override {@code usesRandomIdentity()} or invent a separate skin-category
 * axis (design document Section 0/8: no such axis exists in code). Trading is reached through the
 * EXISTING generic {@code dialogueId} field/{@code mobInteract} path (set once, in the
 * constructor, to {@link #GREETING_DIALOGUE}) rather than an overridden {@code mobInteract} —
 * unlike {@link BankerNpcEntity}, a Provisioner has no "met before" narrative branching, so the
 * generic path is sufficient and no override is needed.
 *
 * <p>Credits and stock initialize independently of identity and of each other, each gated by its
 * own explicit persisted marker ({@link #creditsInitialized}/{@link #stockInitialized}) — NEVER
 * inferred from {@code currentCredits != 0} or {@code stock.isEmpty()}, both of which are
 * legitimate live states (a merchant can legitimately owe/hold 0 Credits post-Investment-someday,
 * and a sold-out stock list is a real, valid, non-empty-but-all-zero-stock state, not "never
 * rolled"). {@link #ensureCreditsAndStockInitialized()} runs from two places: {@link
 * #finalizeSpawn} (the normal path — mirrors {@code TotalityNpcEntity}'s own gender/name
 * roll-once call exactly, and — critically — {@code finalizeSpawn} is ONLY ever invoked for a
 * genuinely NEW spawn, never for an entity deserialized from saved NBT, which is what makes
 * "reload never rerolls" true for free) and {@link #customServerAiStep} (a cheap, per-tick,
 * near-zero-cost fallback retry — both checks short-circuit to nothing once both markers are
 * true — covering the one legitimate retry case: the referenced assortment pool was missing or
 * invalid at spawn time but a later datapack fix makes it valid before the next tick).
 */
public class ProvisionerNpcEntity extends TotalityNpcEntity implements MerchantRuntime, MerchantStockProvider {

    private static final Identifier GREETING_DIALOGUE =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "provisioner_greeting");

    public static final Identifier DEFAULT_ASSORTMENT_POOL_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "generic_provisioner");

    private static final Set<TagKey<Item>> ACCEPTED_TAGS = Set.of(ModTags.PROVISIONER_BUYS);

    /** Datapack safety bound (Phase 3 hardening pass, Section 3) on how often a missing/invalid
     *  assortment pool is retried from {@link #customServerAiStep} — protects against per-tick
     *  log spam and repeated pool lookups while a pool is genuinely absent, not a gameplay value. */
    private static final int STOCK_INIT_RETRY_INTERVAL_TICKS = 200;

    private long currentCredits = 0L;
    private boolean creditsInitialized = false;
    private boolean stockInitialized = false;
    private Identifier assortmentPoolId = DEFAULT_ASSORTMENT_POOL_ID;
    private final List<MerchantStockEntry> stock = new ArrayList<>();

    // Transient (never persisted — Phase 3 hardening pass, Section 3): throttles the
    // missing/invalid-pool retry path so it logs once and retries at most every
    // STOCK_INIT_RETRY_INTERVAL_TICKS ticks instead of every tick via customServerAiStep.
    private boolean stockInitFailureLogged = false;
    private int ticksUntilNextStockInitAttempt = 0;

    public ProvisionerNpcEntity(EntityType<? extends ProvisionerNpcEntity> type, Level level) {
        super(type, level);
        setDialogueId(GREETING_DIALOGUE);
    }

    /**
     * A dedicated Provisioner is a persistent civilian NPC, not a generic despawnable mob (Phase 4
     * correction pass, Part B) — its rolled identity, assortment, and Credits are meant to be
     * permanent for that entity instance. Overriding this (rather than only calling the inherited
     * {@code setPersistenceRequired()} once, which a stale/absent persisted NBT value could
     * disagree with on a later load) unconditionally exempts it from {@code Mob#checkDespawn}'s
     * distance/instant-despawn branch regardless of anything read from save data, exactly mirroring
     * {@code defaultDialogueId()}'s "always enforce this invariant, never rely on NBT alone"
     * approach just above. Does not affect natural death — {@link #die} is unchanged, and this
     * class does not override damage/invulnerability, so a Provisioner still dies normally when
     * actually killed.
     */
    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    /**
     * Phase 3 hardening pass, Section 1: a fresh {@code /summon totality:provisioner} (or any
     * other command/structure spawn with no explicit {@code DialogueId}) still routes through
     * {@link #readAdditionalSaveData} via {@code Entity#load}, which previously reset
     * {@code dialogueId} to {@code null} whenever the NBT key was absent — silently overwriting
     * this constructor's own default and leaving right-click doing nothing. Overriding this
     * default instead of relying on the constructor alone means the SAME fallback applies
     * whichever path constructed this instance (natural spawn — never calls {@code load} at all —
     * or command/structure spawn, which always does).
     */
    @Override
    @Nullable
    protected Identifier defaultDialogueId() { return GREETING_DIALOGUE; }

    public static AttributeSupplier.Builder createAttributes() {
        return TotalityNpcEntity.createAttributes();
    }

    /** Exposed for verification (and any future authored variant) to construct/inspect a
     *  Provisioner against a specific, possibly non-production, assortment pool id without
     *  touching the datapack-loaded default. */
    public void setAssortmentPoolId(Identifier id) {
        this.assortmentPoolId = id;
    }

    public Identifier getAssortmentPoolId() {
        return assortmentPoolId;
    }

    public boolean isCreditsInitialized() { return creditsInitialized; }
    public boolean isStockInitialized() { return stockInitialized; }

    /** Test-only hook ({@code ProvisionerVerification}): seeds Credits directly, marking it
     *  initialized, without requiring a real {@link #finalizeSpawn} call. */
    public void setCreditsForTest(long credits) {
        this.currentCredits = credits;
        this.creditsInitialized = true;
    }

    /** Test-only hook ({@code ProvisionerVerification}): seeds stock directly, marking it
     *  initialized, without requiring a real assortment pool roll. */
    public void setStockForTest(List<MerchantStockEntry> entries) {
        stock.clear();
        stock.addAll(entries);
        stockInitialized = true;
    }

    /** Test-only hook ({@code ProvisionerVerification}): drives one {@link #customServerAiStep}
     *  call on an isolated (never-added-to-a-level) instance, so the missing-pool retry-throttle
     *  behavior (Phase 3 hardening pass, Section 3) can be exercised tick-by-tick without a real
     *  ticking server. Safe to call on an entity with no interaction partner — the inherited
     *  per-tick safety net in {@code TotalityNpcEntity#customServerAiStep} returns immediately in
     *  that case, before any navigation/AI work runs. */
    public void runAiStepForTest(ServerLevel level) {
        customServerAiStep(level);
    }

    /** Test-only hook ({@code ProvisionerVerification}): drives the REAL, full
     *  {@code readAdditionalSaveData} chain (base {@code TotalityNpcEntity} identity/dialogue
     *  fields, THEN this class's own {@link #readPhase3State}) against an arbitrary {@link
     *  ValueInput} — including a genuinely empty one with no keys at all, exactly what {@code
     *  Entity#load} sees for a plain {@code /summon} with no explicit NBT. This is what actually
     *  exercises the {@code defaultDialogueId()} fallback fix (Phase 3 hardening pass, Section 1);
     *  {@link #readPhase3State} alone does not touch {@code dialogueId} at all. */
    public void simulateFullLoadForTest(ValueInput input) {
        readAdditionalSaveData(input);
    }

    /** Test-only hook, symmetric to {@link #simulateFullLoadForTest} — drives the REAL, full
     *  {@code addAdditionalSaveData} chain so a round-trip test can prove a value this class
     *  DIDN'T explicitly author (e.g. a defaulted {@code dialogueId}) still gets WRITTEN once
     *  established, and therefore survives a second load without needing the default again. */
    public void simulateFullSaveForTest(ValueOutput output) {
        addAdditionalSaveData(output);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        ensureCreditsAndStockInitialized();
        return data;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        // Cheap short-circuit once both markers are true (the overwhelmingly common steady
        // state) — avoids even touching the retry-throttle counter below on every tick of every
        // Provisioner's life (Phase 3 hardening pass, Section 3).
        if (creditsInitialized && stockInitialized) return;
        if (ticksUntilNextStockInitAttempt > 0) {
            ticksUntilNextStockInitAttempt--;
            return;
        }
        ticksUntilNextStockInitAttempt = STOCK_INIT_RETRY_INTERVAL_TICKS;
        ensureCreditsAndStockInitialized();
    }

    /**
     * Rolls Credits/stock exactly once each, guarded independently by their own persisted
     * markers. If {@link #assortmentPoolId} does not resolve to a valid loaded pool, logs a
     * clear real-content error EXACTLY ONCE per entity per logical-server lifetime (Phase 3
     * hardening pass, Section 3 — {@link #stockInitFailureLogged}, a transient marker that does
     * NOT need NBT persistence) and leaves {@link #stockInitialized} false — no partial stock is
     * ever created. This method itself may still be called repeatedly (fresh spawn, then a
     * throttled retry from {@link #customServerAiStep}, at most once every
     * {@value #STOCK_INIT_RETRY_INTERVAL_TICKS} ticks) so that a later datapack fix can still take
     * effect without a restart; only the LOGGING of an unchanged failure is silenced after the
     * first time. The registries this depends on do not currently promise a live {@code /reload}
     * — this retry exists primarily as protection against initialization ordering and future
     * compatibility, not as a substitute for one. A successful initialization clears the
     * transient failure/retry state.
     */
    private void ensureCreditsAndStockInitialized() {
        if (!creditsInitialized) {
            currentCredits = MerchantRuntimeRegistry.BASE_STARTING_CREDITS;
            creditsInitialized = true;
        }
        if (!stockInitialized) {
            Optional<ProvisionerAssortmentPool> pool = ProvisionerAssortmentRegistry.INSTANCE.get(assortmentPoolId);
            if (pool.isEmpty()) {
                if (!stockInitFailureLogged) {
                    Totality.LOGGER.error(
                            "Provisioner {} could not initialize stock: assortment pool {} not found or failed validation"
                                    + " (will retry silently every {} ticks until a valid pool is available)",
                            getUUID(), assortmentPoolId, STOCK_INIT_RETRY_INTERVAL_TICKS);
                    stockInitFailureLogged = true;
                }
                return;
            }
            List<MerchantStockEntry> rolled = pool.get().roll(this.random);
            stock.clear();
            stock.addAll(rolled);
            stockInitialized = true;
            stockInitFailureLogged = false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // MerchantRuntime
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public Identifier merchantId() {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, "provisioner/" + getUUID());
    }

    @Override
    public long currentCredits() {
        return currentCredits;
    }

    @Override
    public void setCurrentCredits(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("currentCredits must not be negative, was " + value);
        }
        this.currentCredits = value;
        // A successful, explicit set (including 0) always marks Credits initialized (Phase 3
        // hardening pass, Section 2) — otherwise setCurrentCredits(0) right after construction
        // would leave creditsInitialized false, and the next ensureCreditsAndStockInitialized()
        // call would silently replace the caller's explicit 0 with the 300-Credit baseline.
        this.creditsInitialized = true;
    }

    @Override
    public Set<TagKey<Item>> acceptedTags() {
        return ACCEPTED_TAGS;
    }

    @Override
    public String archetypeTranslationKey() {
        return "totality.trading.archetype.provisioner";
    }

    // ─────────────────────────────────────────────────────────────────────
    // MerchantStockProvider
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public List<MerchantStockEntry> stockEntries() {
        return List.copyOf(stock);
    }

    @Override
    public boolean canPurchaseStock(int entryIndex, int quantity) {
        if (entryIndex < 0 || entryIndex >= stock.size() || quantity <= 0) return false;
        return stock.get(entryIndex).currentStock() >= quantity;
    }

    @Override
    public boolean decrementStock(int entryIndex, int quantity) {
        if (!canPurchaseStock(entryIndex, quantity)) return false;
        MerchantStockEntry entry = stock.get(entryIndex);
        stock.set(entryIndex, entry.withStock(entry.currentStock() - quantity));
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Persistence
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        writePhase3State(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        readPhase3State(input);
    }

    /** Writes exactly the Phase 3 persisted fields (Credits, init markers, pool id, stock) —
     *  factored out of {@link #addAdditionalSaveData} so {@code ProvisionerVerification} can
     *  drive a focused NBT round-trip of just this state against a real {@link ValueOutput}/
     *  {@link ValueInput} pair, without needing a full {@code Entity#save}/{@code #load} cycle. */
    public void writePhase3State(ValueOutput output) {
        output.putLong("CurrentCredits", currentCredits);
        output.putBoolean("CreditsInitialized", creditsInitialized);
        output.putBoolean("StockInitialized", stockInitialized);
        output.putString("AssortmentPoolId", assortmentPoolId.toString());
        output.store("Stock", MerchantStockEntry.CODEC.listOf(), List.copyOf(stock));
    }

    /**
     * Reads exactly the Phase 3 persisted fields. All-or-nothing for {@code Stock}: if the
     * persisted list fails to decode (any single malformed entry), the codec's list decode fails
     * as a whole rather than silently dropping just the bad entry — deliberately, so a corrupted
     * save can never leave a partially-rerolled or duplicated stock behind. {@code
     * StockInitialized} is read independently either way, so a corruption event degrades to "this
     * Provisioner presents as fully sold out" rather than ever rerolling or duplicating.
     */
    public void readPhase3State(ValueInput input) {
        long persistedCredits = input.getLongOr("CurrentCredits", 0L);
        if (persistedCredits < 0) {
            // Sanitize only — never reroll the baseline here (Phase 3 hardening pass, Section 2):
            // creditsInitialized is read independently, below, exactly as persisted, so a
            // genuinely-initialized-but-corrupted balance degrades to 0, not back to 300.
            Totality.LOGGER.error(
                    "Provisioner {} loaded a corrupt negative CurrentCredits ({}); sanitizing to 0",
                    getUUID(), persistedCredits);
            persistedCredits = 0L;
        }
        currentCredits = persistedCredits;
        creditsInitialized = input.getBooleanOr("CreditsInitialized", false);
        stockInitialized = input.getBooleanOr("StockInitialized", false);

        String poolId = input.getStringOr("AssortmentPoolId", DEFAULT_ASSORTMENT_POOL_ID.toString());
        Identifier parsedPoolId = Identifier.tryParse(poolId);
        assortmentPoolId = parsedPoolId != null ? parsedPoolId : DEFAULT_ASSORTMENT_POOL_ID;

        // All-or-nothing recovery (Phase 3 hardening pass, Section 7): the CODEC's own list
        // decode already fails as a whole for a structurally malformed entry (e.g. negative
        // current_stock — out of Codec.intRange(0, MAX_VALUE)). This additionally rejects a
        // structurally VALID list that is still semantically corrupt (an empty item template, or
        // duplicate entries for the same item+components) — the entire list is discarded, never
        // just the offending entries, and StockInitialized is kept exactly as persisted either
        // way, so corruption degrades to "this Provisioner presents as fully sold out" rather than
        // ever rerolling or duplicating stock.
        List<MerchantStockEntry> decodedStock = input.read("Stock", MerchantStockEntry.CODEC.listOf()).orElse(List.of());
        if (!isValidPersistedStock(decodedStock)) {
            Totality.LOGGER.error(
                    "Provisioner {} loaded corrupt persisted stock (empty item template or duplicate entries); "
                            + "discarding entire list rather than partially recovering it", getUUID());
            decodedStock = List.of();
        }
        stock.clear();
        stock.addAll(decodedStock);
    }

    /** Rejects the whole persisted stock list if ANY entry is structurally invalid (Phase 3
     *  hardening pass, Section 7) — an empty item template, a negative stock (unreachable via the
     *  CODEC's own range validation, checked here anyway as defense-in-depth), or a duplicate
     *  item+components entry. All-or-nothing: never keeps a valid subset. Pure (no logging, no
     *  I/O) and public specifically so {@code ProvisionerVerification} can drive it directly
     *  against hand-built lists — including entries a real codec round-trip could never produce
     *  in the first place (e.g. an empty item template) — mirroring the same directly-testable
     *  {@code validate()} pattern {@link zcylas.totality.api.shop.assortment.ProvisionerAssortmentPool}
     *  already uses. */
    public static boolean isValidPersistedStock(List<MerchantStockEntry> entries) {
        for (MerchantStockEntry entry : entries) {
            if (entry.item().isEmpty() || entry.currentStock() < 0) return false;
        }
        for (int a = 0; a < entries.size(); a++) {
            for (int b = a + 1; b < entries.size(); b++) {
                if (ItemStack.isSameItemSameComponents(entries.get(a).item(), entries.get(b).item())) return false;
            }
        }
        return true;
    }
}
