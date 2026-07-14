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

    private long currentCredits = 0L;
    private boolean creditsInitialized = false;
    private boolean stockInitialized = false;
    private Identifier assortmentPoolId = DEFAULT_ASSORTMENT_POOL_ID;
    private final List<MerchantStockEntry> stock = new ArrayList<>();

    public ProvisionerNpcEntity(EntityType<? extends ProvisionerNpcEntity> type, Level level) {
        super(type, level);
        setDialogueId(GREETING_DIALOGUE);
    }

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
        ensureCreditsAndStockInitialized();
    }

    /**
     * Rolls Credits/stock exactly once each, guarded independently by their own persisted
     * markers. If {@link #assortmentPoolId} does not resolve to a valid loaded pool, logs a
     * clear real-content error and leaves {@link #stockInitialized} false — no partial stock is
     * ever created, and this method safely retries on the next call (fresh spawn, then every
     * subsequent tick via {@link #customServerAiStep}) once the pool becomes valid.
     */
    private void ensureCreditsAndStockInitialized() {
        if (!creditsInitialized) {
            currentCredits = MerchantRuntimeRegistry.BASE_STARTING_CREDITS;
            creditsInitialized = true;
        }
        if (!stockInitialized) {
            Optional<ProvisionerAssortmentPool> pool = ProvisionerAssortmentRegistry.INSTANCE.get(assortmentPoolId);
            if (pool.isEmpty()) {
                Totality.LOGGER.error(
                        "Provisioner {} could not initialize stock: assortment pool {} not found or failed validation",
                        getUUID(), assortmentPoolId);
                return;
            }
            List<MerchantStockEntry> rolled = pool.get().roll(this.random);
            stock.clear();
            stock.addAll(rolled);
            stockInitialized = true;
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
    }

    @Override
    public Set<TagKey<Item>> acceptedTags() {
        return ACCEPTED_TAGS;
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
        currentCredits = input.getLongOr("CurrentCredits", 0L);
        creditsInitialized = input.getBooleanOr("CreditsInitialized", false);
        stockInitialized = input.getBooleanOr("StockInitialized", false);

        String poolId = input.getStringOr("AssortmentPoolId", DEFAULT_ASSORTMENT_POOL_ID.toString());
        Identifier parsedPoolId = Identifier.tryParse(poolId);
        assortmentPoolId = parsedPoolId != null ? parsedPoolId : DEFAULT_ASSORTMENT_POOL_ID;

        stock.clear();
        stock.addAll(input.read("Stock", MerchantStockEntry.CODEC.listOf()).orElse(List.of()));
    }
}
