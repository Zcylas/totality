package zcylas.totality.api.mining;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1 follow-up: an authored {@code from -> to} pair alone must never let a record cross block ids on a lazy read
 * (a removed block's damage could otherwise reach an unrelated replacement placed later at the same position). Only
 * {@code BlockDamageStorage.transformBlock}, which checks the live block and changes it in the same call, may consult
 * the transformation registry. Live behaviour is covered by {@code MiningVerification}.
 */
class StorageTransformationSourceRegressionTest {

    private static final Path STORAGE = Path.of("src/main/java/zcylas/totality/api/mining/BlockDamageStorage.java");

    @Test
    void onlyTheTransformationCallConsultsTheTransformationRegistry() throws Exception {
        // Pass 4A: transformBlock delegates to the generic transaction, which is now the ONLY registry consumer.
        String src = Files.readString(STORAGE);
        int transformBlock = src.indexOf("    public static <T> T transaction(");
        assertTrue(transformBlock >= 0, "transaction(...) must exist");
        assertTrue(src.contains("return transaction(level, pos, () -> level.setBlock(pos, newState, flags));"),
                "transformBlock is a transaction around its own setBlock");
        int uses = 0;
        for (int i = src.indexOf("BlockProfiles.transformsTo("); i >= 0; i = src.indexOf("BlockProfiles.transformsTo(", i + 1)) {
            uses++;
            int enclosing = src.lastIndexOf("    public ", i);
            enclosing = Math.max(enclosing, src.lastIndexOf("    private ", i));
            assertEquals(transformBlock, enclosing, "transformsTo may only be consulted inside the transaction");
        }
        assertEquals(1, uses);
    }

    @Test
    void transformationChecksTheLiveBlockBeforeChangingIt() throws Exception {
        // The block standing on each tied position is captured BEFORE the mutation runs, and the record is matched to
        // that captured block (recordIsLiveBlock) — never to whatever stands there afterwards.
        String src = Files.readString(STORAGE);
        int body = src.indexOf("public static <T> T transaction(");
        int capture = src.indexOf("before.add(new Before(p, e, level.getBlockState(p)));", body);
        int mutate = src.indexOf("result = mutation.get();", body);
        int liveCheck = src.indexOf("e.block.equals(key(b.state()))", body);
        int pairCheck = src.indexOf("BlockProfiles.transformsTo(b.state().getBlock(), after.getBlock())", body);
        assertTrue(capture > body && mutate > capture && liveCheck > mutate && pairCheck > mutate,
                "the record must be matched to the block standing there BEFORE that block is changed");
    }

    @Test
    void lazyReconcileHasNoTransformationInput() throws Exception {
        String src = Files.readString(STORAGE);
        int body = src.indexOf("private Entry reconcile(Entry e, BlockState state) {");
        String reconcile = src.substring(body, src.indexOf("\n    }\n", body));
        assertFalse(reconcile.contains("transformsTo"));
        assertTrue(reconcile.contains("IntegrityReconciliation.reconcile(e.integrity, e.max, e.block.equals(key(state)),"));
    }
}
