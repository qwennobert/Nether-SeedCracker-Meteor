package com.qwennobert.nethercracker.cracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.qwennobert.nethercracker.cracker.JavaRandom.ADDEND;
import static com.qwennobert.nethercracker.cracker.JavaRandom.MASK48;
import static com.qwennobert.nethercracker.cracker.JavaRandom.MULTIPLIER;

/**
 * Finds the structure seeds (lower 48 bits of the world seed) that generate the observed nether bedrock.
 * <p>
 * Java port of the search of 19MisterX98's Nether Bedrock Cracker (LGPL-3.0,
 * https://github.com/19MisterX98/Nether_Bedrock_Cracker):
 * <ol>
 * <li>The 48 bit seed of one surface (the one with more information, the "primary" surface) is brute forced. The upper
 * {@value #TOP_BITS} bits are enumerated, the lower {@value #TREE_BITS} bits bit by bit in a tree. Even with unknown
 * lower bits a block can rule out a seed, because the unknown bits only move the next random state by a bounded
 * amount.</li>
 * <li>Every surface seed that matches all primary blocks is reversed to the common factory seed, which gives the seed
 * of the other surface. Its blocks are checked.</li>
 * <li>The factory seed is reversed to the structure seed.</li>
 * </ol>
 */
public final class BedrockCracker {
    /** Bits of the primary surface seed that are enumerated directly. */
    public static final int TOP_BITS = 36;
    /** Bits of the primary surface seed that are found in the tree. */
    public static final int TREE_BITS = 48 - TOP_BITS;
    /** Number of top layer checks done without branches in {@link #search}. */
    private static final int PREFIX = 4;
    /** Number of top values a worker takes at once. */
    private static final long CHUNK = 1L << 22;
    public static final long TOTAL_CHUNKS = (1L << TOP_BITS) / CHUNK;

    private final Surface primary;
    private final int primaryBlocks, secondaryBlocks;
    private final double primaryBits, secondaryBits;

    /** Checks per tree layer, index = number of unknown lower bits. */
    private final long[][] layerHash = new long[TREE_BITS + 1][];
    private final long[][] layerAddend = new long[TREE_BITS + 1][];
    private final long[][] layerLimit = new long[TREE_BITS + 1][];

    /** Exact checks for the secondary surface. */
    private final long[] secondaryHash, secondaryAddend, secondaryLimit;

    public BedrockCracker(Collection<BedrockBlock> blocks, BedrockGeneration generation) {
        List<BedrockModel.Constraint> floor = new ArrayList<>();
        List<BedrockModel.Constraint> roof = new ArrayList<>();
        for (BedrockBlock block : blocks) {
            BedrockModel.Constraint constraint = BedrockModel.Constraint.of(block, generation);
            (constraint.surface() == Surface.FLOOR ? floor : roof).add(constraint);
        }

        double floorBits = bits(floor), roofBits = bits(roof);
        primary = floorBits >= roofBits ? Surface.FLOOR : Surface.ROOF;
        List<BedrockModel.Constraint> primaryList = primary == Surface.FLOOR ? floor : roof;
        List<BedrockModel.Constraint> secondaryList = primary == Surface.FLOOR ? roof : floor;

        primaryBlocks = primaryList.size();
        secondaryBlocks = secondaryList.size();
        primaryBits = bits(primaryList);
        secondaryBits = bits(secondaryList);

        buildLayers(primaryList);

        int n = secondaryList.size();
        secondaryHash = new long[n];
        secondaryAddend = new long[n];
        secondaryLimit = new long[n];
        for (int i = 0; i < n; i++) {
            BedrockModel.Constraint c = secondaryList.get(i);
            secondaryHash[i] = c.positionHash();
            secondaryAddend[i] = (ADDEND - c.lower()) & MASK48;
            secondaryLimit[i] = c.size();
        }
    }

    /**
     * A check with {@code bits} unknown lower bits. The unknown bits {@code l} change the state by {@code l * MULTIPLIER},
     * at most {@code window}. The check only rejects a seed if no such shift brings the next state into the allowed
     * range, so it never drops the right seed.
     */
    private record Check(BedrockModel.Constraint constraint, int bits, double rejected) {
        long hash() {
            long lowMask = (1L << bits) - 1;
            return constraint.positionHash() & ~lowMask;
        }

        long addend() {
            return (ADDEND - constraint.lower() + window(bits)) & MASK48;
        }

        long limit() {
            return constraint.size() + window(bits);
        }
    }

    private static long window(int bits) {
        return ((1L << bits) - 1) * MULTIPLIER;
    }

    private void buildLayers(List<BedrockModel.Constraint> constraints) {
        // How much of the seed space each block still lets through after the checks in the layers above
        long[] range = new long[constraints.size()];
        Arrays.fill(range, 1L << 48);

        for (int bits = TREE_BITS; bits >= 0; bits--) {
            List<Check> checks = new ArrayList<>();
            for (int i = 0; i < constraints.size(); i++) {
                BedrockModel.Constraint constraint = constraints.get(i);
                long newRange = constraint.size() + window(bits);
                if (newRange >= range[i]) continue;

                checks.add(new Check(constraint, bits, 1.0 - (double) newRange / range[i]));
                range[i] = newRange;
            }

            // Checks that reject the most come first, so the loop ends early
            checks.sort(Comparator.comparingDouble(Check::rejected).reversed());

            int n = checks.size();
            layerHash[bits] = new long[n];
            layerAddend[bits] = new long[n];
            layerLimit[bits] = new long[n];
            for (int i = 0; i < n; i++) {
                Check check = checks.get(i);
                layerHash[bits][i] = check.hash();
                layerAddend[bits][i] = check.addend();
                layerLimit[bits][i] = check.limit();
            }
        }
    }

    private static double bits(List<BedrockModel.Constraint> constraints) {
        double bits = 0;
        for (BedrockModel.Constraint constraint : constraints) bits += constraint.bits();
        return bits;
    }

    /**
     * Searches the top values {@code [fromTop, toTop)} of the primary surface seed.
     */
    public void search(long fromTop, long toTop, ResultSink sink) {
        final long[] hash = layerHash[TREE_BITS], addend = layerAddend[TREE_BITS], limit = layerLimit[TREE_BITS];
        final int n = hash.length;
        final long half = 1L << (TREE_BITS - 1);
        final long[] buffer = new long[8];

        // The first checks are done without branches, a top value passes each one about half of the time, so the
        // CPU could not predict a branch per check. Missing checks are filled with ones that always pass.
        final long h0 = at(hash, 0, 0), a0 = at(addend, 0, 0), l0 = at(limit, 0, 1L << 48);
        final long h1 = at(hash, 1, 0), a1 = at(addend, 1, 0), l1 = at(limit, 1, 1L << 48);
        final long h2 = at(hash, 2, 0), a2 = at(addend, 2, 0), l2 = at(limit, 2, 1L << 48);
        final long h3 = at(hash, 3, 0), a3 = at(addend, 3, 0), l3 = at(limit, 3, 1L << 48);

        for (long top = fromTop; top < toTop; top++) {
            final long value = top << TREE_BITS;

            // state - limit is negative when the check passes, so the AND is negative when all of them pass
            long passed = ((((value ^ h0) * MULTIPLIER + a0) & MASK48) - l0)
                & ((((value ^ h1) * MULTIPLIER + a1) & MASK48) - l1)
                & ((((value ^ h2) * MULTIPLIER + a2) & MASK48) - l2)
                & ((((value ^ h3) * MULTIPLIER + a3) & MASK48) - l3);
            if (passed >= 0) continue;

            int i = PREFIX;
            while (i < n && (((value ^ hash[i]) * MULTIPLIER + addend[i]) & MASK48) < limit[i]) i++;
            if (i < n) continue;

            descend(TREE_BITS - 1, value, sink, buffer);
            descend(TREE_BITS - 1, value | half, sink, buffer);
        }
    }

    private static long at(long[] array, int index, long fallback) {
        return index < array.length ? array[index] : fallback;
    }

    private void descend(int bits, long value, ResultSink sink, long[] buffer) {
        final long[] hash = layerHash[bits], addend = layerAddend[bits], limit = layerLimit[bits];
        for (int i = 0; i < hash.length; i++) {
            if ((((value ^ hash[i]) * MULTIPLIER + addend[i]) & MASK48) >= limit[i]) return;
        }

        if (bits == 0) {
            crossCheck(value, sink, buffer);
            return;
        }

        descend(bits - 1, value, sink, buffer);
        descend(bits - 1, value | (1L << (bits - 1)), sink, buffer);
    }

    /** {@code primarySeed} matches all primary blocks, check the other surface and reverse to structure seeds. */
    private void crossCheck(long primarySeed, ResultSink sink, long[] buffer) {
        int factories = JavaRandom.reverseNextLong(primarySeed, buffer);
        for (int f = 0; f < factories; f++) {
            long factory = (buffer[f] ^ primary.nameHash) & MASK48;

            long secondarySeed = JavaRandom.nextLong(factory ^ primary.other().nameHash) & MASK48;
            if (!matchesSecondary(secondarySeed)) continue;

            int structures = JavaRandom.reverseNextLong(factory, buffer, 4);
            for (int s = 0; s < structures; s++) sink.accept(buffer[4 + s]);
        }
    }

    private boolean matchesSecondary(long seed) {
        for (int i = 0; i < secondaryHash.length; i++) {
            if ((((seed ^ secondaryHash[i]) * MULTIPLIER + secondaryAddend[i]) & MASK48) >= secondaryLimit[i]) return false;
        }
        return true;
    }

    /**
     * Searches the whole seed space on {@code threads} background threads.
     */
    public Search start(int threads, ResultSink sink, Runnable onFinished) {
        Search search = new Search(sink, onFinished);
        for (int i = 0; i < threads; i++) {
            Thread thread = new Thread(search::work, "Nether Seed Cracker #" + (i + 1));
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            search.running.incrementAndGet();
            thread.start();
        }
        return search;
    }

    public final class Search {
        private final ResultSink sink;
        private final Runnable onFinished;
        private final AtomicLong nextChunk = new AtomicLong();
        private final AtomicLong doneChunks = new AtomicLong();
        private final AtomicInteger running = new AtomicInteger();
        private final long startTime = System.nanoTime();
        private volatile boolean cancelled;
        private volatile boolean finished;

        private Search(ResultSink sink, Runnable onFinished) {
            this.sink = sink;
            this.onFinished = onFinished;
        }

        private void work() {
            try {
                long chunk;
                while (!cancelled && (chunk = nextChunk.getAndIncrement()) < TOTAL_CHUNKS) {
                    search(chunk * CHUNK, (chunk + 1) * CHUNK, sink);
                    doneChunks.incrementAndGet();
                }
            }
            finally {
                if (running.decrementAndGet() == 0) {
                    finished = true;
                    onFinished.run();
                }
            }
        }

        /** Stops the search, the threads end after their current chunk. */
        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public boolean isFinished() {
            return finished;
        }

        /** Searched part of the seed space, 0 to 1. */
        public double progress() {
            return (double) doneChunks.get() / TOTAL_CHUNKS;
        }

        /** Estimated seconds until the search is done, -1 if unknown. */
        public long secondsLeft() {
            long done = doneChunks.get();
            if (done == 0) return -1;
            double elapsed = (System.nanoTime() - startTime) / 1e9;
            return (long) (elapsed / done * (TOTAL_CHUNKS - done));
        }

        public long secondsElapsed() {
            return (System.nanoTime() - startTime) / 1_000_000_000L;
        }
    }

    @FunctionalInterface
    public interface ResultSink {
        /** Called from the worker threads with every structure seed that matches all blocks. */
        void accept(long structureSeed);
    }

    public Surface primary() {
        return primary;
    }

    public int primaryBlocks() {
        return primaryBlocks;
    }

    public int secondaryBlocks() {
        return secondaryBlocks;
    }

    /** Information in the primary surface blocks. */
    public double primaryBits() {
        return primaryBits;
    }

    /** Information in the secondary surface blocks. */
    public double secondaryBits() {
        return secondaryBits;
    }

    /**
     * log2 of the number of structure seeds expected to match by chance. Below 0 means the data most likely gives a
     * single result.
     */
    public double log2ExpectedResults() {
        return 48 - primaryBits - secondaryBits;
    }

    /** Number of checks in the top layer, mostly for tests. */
    int topChecks() {
        return layerHash[TREE_BITS].length;
    }
}
