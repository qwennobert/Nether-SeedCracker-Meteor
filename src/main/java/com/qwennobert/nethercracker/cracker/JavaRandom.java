package com.qwennobert.nethercracker.cracker;

/**
 * The math of {@link java.util.Random}, which Minecraft uses as the "legacy" random source for nether bedrock.
 * <p>
 * {@link #reverseNextLong} is a port of the next_long_reverser crate used by 19MisterX98's Nether Bedrock Cracker
 * (LGPL-3.0).
 */
public final class JavaRandom {
    public static final long MULTIPLIER = 0x5DEECE66DL;
    public static final long ADDEND = 0xBL;
    public static final long MASK48 = (1L << 48) - 1;

    private JavaRandom() {
    }

    /** The internal state of {@code new Random(seed)}. */
    public static long scramble(long seed) {
        return (seed ^ MULTIPLIER) & MASK48;
    }

    /** Advances an internal state by one call. */
    public static long step(long state) {
        return (state * MULTIPLIER + ADDEND) & MASK48;
    }

    /** {@code new Random(seed).nextLong()} */
    public static long nextLong(long seed) {
        long state = step(scramble(seed));
        int high = (int) (state >>> 16);
        state = step(state);
        int low = (int) (state >>> 16);
        return ((long) high << 32) + low;
    }

    /** The 24 bits behind {@code new Random(seed).nextFloat()}, the float is this value divided by 2^24. */
    public static int nextFloatBits(long seed) {
        return (int) (step(scramble(seed)) >>> 24);
    }

    /**
     * Finds the seeds whose {@code new Random(seed).nextLong()} has the given lower 48 bits. Only the lower 48 bits of
     * a seed matter for {@link java.util.Random}, so the returned seeds are 48 bit values.
     *
     * @param out receives the seeds, needs room for 4
     * @return the number of seeds written to {@code out}
     */
    public static int reverseNextLong(long lower48, long[] out) {
        return reverseNextLong(lower48, out, 0);
    }

    /**
     * Like {@link #reverseNextLong(long, long[])}, but writes the seeds to {@code out} starting at {@code offset}.
     */
    public static int reverseNextLong(long lower48, long[] out, int offset) {
        long structureSeed = lower48 & MASK48;
        long lowerBits = structureSeed & 0xFFFF_FFFFL;
        long upperBits = structureSeed >>> 32;
        // nextLong() adds the lower int sign-extended, restore the upper bits from before that
        if ((lowerBits & 0x8000_0000L) != 0) upperBits += 1;

        // Lattice reduction by Matthew Bolan. bitsOfDanger = 0 would be exact but overflows, 1 keeps it in a long.
        final int bitsOfDanger = 1;
        long lowMin = lowerBits << (16 - bitsOfDanger);
        long lowMax = ((lowerBits + 1) << (16 - bitsOfDanger)) - 1;
        long upperMin = ((upperBits << 16) - 107048004364969L) >> bitsOfDanger;

        long m1lv = Math.floorDiv(lowMax * -33441L + upperMin * 17549L, 1L << (31 - bitsOfDanger)) + 1;
        long m2lv = Math.floorDiv(lowMin * 46603L + upperMin * 39761L, 1L << (32 - bitsOfDanger)) + 1;

        int count = 0;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                long seed = -39761L * (m1lv + i) + 35098L * (m2lv + j);
                if (((46603L * (m1lv + i) + 66882L * (m2lv + j) + 107048004364969L) >> 16) == upperBits
                    && (seed >>> 16) == lowerBits) {
                    // Go back two calls to the internal state, then undo the scrambling of setSeed
                    long state = (seed * 254681119335897L + 120305458776662L) & MASK48;
                    out[offset + count++] = state ^ MULTIPLIER;
                }
            }
        }
        return count;
    }
}
