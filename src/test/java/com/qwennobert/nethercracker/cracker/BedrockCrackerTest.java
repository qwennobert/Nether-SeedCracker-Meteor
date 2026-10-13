package com.qwennobert.nethercracker.cracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockCrackerTest {
    /** Top values searched around the right one, a full search takes minutes. */
    private static final long RADIUS = 1 << 20;

    private static Set<Long> searchAround(BedrockCracker cracker, long worldSeed) {
        long surfaceSeed = BedrockModel.surfaceSeed(worldSeed, cracker.primary()) & JavaRandom.MASK48;
        long top = surfaceSeed >>> BedrockCracker.TREE_BITS;

        Set<Long> found = ConcurrentHashMap.newKeySet();
        cracker.search(Math.max(0, top - RADIUS), Math.min(1L << BedrockCracker.TOP_BITS, top + RADIUS), found::add);
        return found;
    }

    /**
     * The search must find the structure seed of the world. It may also find "twins": nextLong can map two structure
     * seeds to the same factory seed, then both generate exactly the same bedrock and only the hashed seed tells them
     * apart.
     */
    private static void assertFoundWithTwins(long worldSeed, Set<Long> found) {
        assertTrue(found.contains(worldSeed & JavaRandom.MASK48), "world seed not found: " + found);
        for (long seed : found) {
            for (Surface surface : Surface.values()) {
                assertEquals(BedrockModel.surfaceSeed(worldSeed, surface) & JavaRandom.MASK48, BedrockModel.surfaceSeed(seed, surface) & JavaRandom.MASK48, "not a twin: " + seed);
            }
        }
        assertTrue(found.size() <= 2, "too many results: " + found);
    }

    @Test
    void findsExampleSeed() {
        long worldSeed = BedrockModelTest.WORLD_SEED;
        BedrockCracker cracker = new BedrockCracker(TestData.example(), BedrockGeneration.Vanilla);

        assertFoundWithTwins(worldSeed, searchAround(cracker, worldSeed));
    }

    @Test
    void findsRandomSeedsFromOneChunk() {
        Random random = new Random(7);
        for (int i = 0; i < 5; i++) {
            long worldSeed = random.nextLong();
            List<BedrockBlock> chunk = TestData.chunk(worldSeed, random.nextInt(200) - 100, random.nextInt(200) - 100, BedrockGeneration.Vanilla);
            List<BedrockBlock> blocks = BlockSelector.select(chunk, 64);

            BedrockCracker cracker = new BedrockCracker(blocks, BedrockGeneration.Vanilla);
            assertTrue(cracker.log2ExpectedResults() < -100, "expected results 2^" + cracker.log2ExpectedResults());
            assertFoundWithTwins(worldSeed, searchAround(cracker, worldSeed));
        }
    }

    @Test
    void findsSeedFromFloorOnly() {
        long worldSeed = new Random(8).nextLong();
        List<BedrockBlock> floor = TestData.chunk(worldSeed, 3, -2, BedrockGeneration.Vanilla).stream()
            .filter(block -> block.y() < 64)
            .toList();

        List<BedrockBlock> blocks = BlockSelector.select(floor, 64);
        BedrockCracker cracker = new BedrockCracker(blocks, BedrockGeneration.Vanilla);
        assertEquals(Surface.FLOOR, cracker.primary());
        assertEquals(0, cracker.secondaryBlocks());
        Set<Long> found = searchAround(cracker, worldSeed);
        assertTrue(found.contains(worldSeed & JavaRandom.MASK48));
        assertTrue(found.size() < 200, "too many results: " + found.size());
        for (long seed : found) assertTrue(BedrockModel.matches(seed, blocks, BedrockGeneration.Vanilla));
    }

    @Test
    void findsOldPaperSeed() {
        long worldSeed = new Random(9).nextLong();
        List<BedrockBlock> chunk = TestData.chunk(worldSeed, -7, 11, BedrockGeneration.PaperOld);

        BedrockCracker cracker = new BedrockCracker(BlockSelector.select(chunk, 64), BedrockGeneration.PaperOld);
        assertFoundWithTwins(worldSeed, searchAround(cracker, worldSeed));
    }

    @Test
    void wrongBlockFindsNothing() {
        long worldSeed = new Random(10).nextLong();
        List<BedrockBlock> blocks = new ArrayList<>(BlockSelector.select(TestData.chunk(worldSeed, 0, 0, BedrockGeneration.Vanilla), 64));
        BedrockBlock first = blocks.get(0);
        blocks.set(0, new BedrockBlock(first.x(), first.y(), first.z(), !first.bedrock()));

        BedrockCracker cracker = new BedrockCracker(blocks, BedrockGeneration.Vanilla);
        assertEquals(Set.of(), searchAround(cracker, worldSeed));
    }

    @Test
    void topLayerOnlyHasUsefulChecks() {
        long worldSeed = new Random(11).nextLong();
        BedrockCracker cracker = new BedrockCracker(BlockSelector.select(TestData.chunk(worldSeed, 0, 0, BedrockGeneration.Vanilla), 64), BedrockGeneration.Vanilla);
        assertEquals(64, cracker.primaryBlocks());
        assertEquals(64, cracker.topChecks());
    }

    /** The whole 2^48 search, like in the game. Run with: ./gradlew test -Dnsc.fullSearch=true */
    @Test
    @EnabledIfSystemProperty(named = "nsc.fullSearch", matches = "true")
    void fullSearch() throws InterruptedException {
        long worldSeed = new Random(12).nextLong();
        List<BedrockBlock> blocks = BlockSelector.select(TestData.chunk(worldSeed, 5, 5, BedrockGeneration.Vanilla), 64);
        BedrockCracker cracker = new BedrockCracker(blocks, BedrockGeneration.Vanilla);

        Set<Long> found = ConcurrentHashMap.newKeySet();
        CountDownLatch done = new CountDownLatch(1);
        int threads = Runtime.getRuntime().availableProcessors();
        long start = System.nanoTime();
        BedrockCracker.Search search = cracker.start(threads, found::add, done::countDown);

        while (!done.await(10, TimeUnit.SECONDS)) {
            System.out.printf("%.1f%% after %ds, %ds left%n", search.progress() * 100, search.secondsElapsed(), search.secondsLeft());
        }
        System.out.printf("Full search with %d threads took %.1fs%n", threads, (System.nanoTime() - start) / 1e9);

        assertFoundWithTwins(worldSeed, found);
    }
}
