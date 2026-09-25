package zcylas.totality.api.mining;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Block Breaking V2 Pass 2: the same-id replacement safeguard. A damaged block's record must be deleted the moment
 * the block is removed, not at a later id comparison; ordinary same-block state changes and the {@code transformBlock}
 * transaction must be untouched. Live behaviour is covered by {@code MiningVerification}.
 */
class BlockRemovalHookSourceRegressionTest {

    private static final Path MIXIN = Path.of("src/main/java/zcylas/totality/mixin/mining/LevelChunkBlockRemovalMixin.java");
    private static final Path STORAGE = Path.of("src/main/java/zcylas/totality/api/mining/BlockDamageStorage.java");

    @Test
    void hookIsRegisteredAsACommonMixin() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/totality.mixins.json"));
        int common = config.indexOf("\"mixins\"");
        int client = config.indexOf("\"client\"");
        int hook = config.indexOf("\"mining.LevelChunkBlockRemovalMixin\"");
        assertTrue(hook > common && hook < client, "server-side hook must be in the common mixin list");
    }

    @Test
    void hookFiresOnlyForADifferentBlockOnTheServerBeforeTheChange() throws Exception {
        String src = Files.readString(MIXIN);
        assertTrue(src.contains("@Mixin(LevelChunk.class)"));
        assertTrue(src.contains("@Inject(method = \"setBlockState\", at = @At(\"HEAD\"))"));
        int server = src.indexOf("if (!(this.level instanceof ServerLevel serverLevel)) return;");
        int sameBlock = src.indexOf("if (old.is(state.getBlock())) return;");
        int notify = src.indexOf("BlockDamageStorage.onBlockRemoved(serverLevel, pos);");
        assertTrue(server >= 0 && sameBlock > server && notify > sameBlock,
                "client chunks and same-block state changes are ignored before any notification");
        assertFalse(src.contains(".setBlock(") || src.contains(".setBlockState("), "the hook must not re-enter block setting");
    }

    @Test
    void removalHandlerExemptsTheTransformationTransactionAndOnlyTouchesItsOwnPosition() throws Exception {
        String src = Files.readString(STORAGE);
        int body = src.indexOf("public static void onBlockRemoved(ServerLevel level, BlockPos pos) {");
        String handler = src.substring(body, src.indexOf("\n    }\n", body));
        assertTrue(handler.indexOf("SUPPRESSED.contains(new Suppressed(level, pos.asLong()))") < handler.indexOf("storage.remove(e)"),
                "positions inside a running transaction are exempt");
        assertTrue(handler.contains("storage.map().get(pos.asLong())"), "exact-position lookup, no scan");
        assertFalse(handler.contains("getBlockState") || handler.contains("for ("), "no chunk reads or iteration");

        // Pass 4A: suppression covers exactly the snapshotted positions, is popped in finally, and every suppressed
        // position is reconciled after the mutation (so suppression can never keep a record by accident).
        int tx = src.indexOf("public static <T> T transaction(");
        int push = src.indexOf("for (Before b : before) SUPPRESSED.push(new Suppressed(level, b.pos().asLong()));", tx);
        int run = src.indexOf("result = mutation.get();", tx);
        int pop = src.indexOf("for (int i = 0; i < before.size(); i++) SUPPRESSED.pop();", tx);
        int reconcile = src.indexOf("for (Before b : before) {", pop);
        assertTrue(push > tx && run > push && pop > run && reconcile > pop, "suppression brackets exactly the mutation");
    }
}
