package com.qwennobert.nethercracker.cracker;

import java.util.OptionalLong;

/**
 * A structure seed found by the cracker and the world seeds it can belong to.
 *
 * @param structureSeed    lower 48 bits of the world seed
 * @param verifiedSeed     the world seed whose hash matches the hashed seed of the server, if it was known
 * @param randomWorldSeeds world seeds a random seed ({@code new Random().nextLong()}) could be, if the admin did not
 *                         set one
 * @param intWorldSeed     the world seed if it fits in an int (text seeds, small numbers)
 */
public record SeedCandidate(long structureSeed, OptionalLong verifiedSeed, long[] randomWorldSeeds, OptionalLong intWorldSeed) {
    /**
     * @param hashedSeed the hashed seed the server sent, or null if it is unknown
     */
    public static SeedCandidate resolve(long structureSeed, Long hashedSeed) {
        OptionalLong verified = hashedSeed == null ? OptionalLong.empty() : WorldSeeds.fromHashedSeed(structureSeed, hashedSeed);
        return new SeedCandidate(structureSeed, verified, WorldSeeds.randomWorldSeeds(structureSeed), WorldSeeds.intWorldSeed(structureSeed));
    }

    public boolean verified() {
        return verifiedSeed.isPresent();
    }

    /** Number of possible world seeds without a hashed seed. */
    public int worldSeedCount() {
        return randomWorldSeeds.length + (intWorldSeed.isPresent() ? 1 : 0);
    }
}
