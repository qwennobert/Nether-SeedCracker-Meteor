package com.qwennobert.nethercracker.cracker;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.OptionalLong;

import static com.qwennobert.nethercracker.cracker.JavaRandom.MASK48;

/**
 * Turns a structure seed (lower 48 bits) into full 64 bit world seeds.
 */
public final class WorldSeeds {
    private WorldSeeds() {
    }

    /**
     * World seeds that a random seed could be: without a level-seed, Minecraft uses {@code new Random().nextLong()},
     * and nextLong can only produce one or two values with these lower 48 bits.
     */
    public static long[] randomWorldSeeds(long structureSeed) {
        long[] seeds = new long[4];
        int count = JavaRandom.reverseNextLong(structureSeed, seeds);
        long[] result = new long[count];
        for (int i = 0; i < count; i++) result[i] = JavaRandom.nextLong(seeds[i]);
        return result;
    }

    /**
     * The world seed if it fits in an int, like seeds typed as text (they become {@code text.hashCode()}) or small
     * numbers.
     */
    public static OptionalLong intWorldSeed(long structureSeed) {
        long seed = (int) structureSeed;
        return (seed & MASK48) == (structureSeed & MASK48) ? OptionalLong.of(seed) : OptionalLong.empty();
    }

    /**
     * Finds the upper 16 bits of the world seed with the hashed seed the server sends to the client (it covers every
     * seed, also ones typed in by the admin).
     */
    public static OptionalLong fromHashedSeed(long structureSeed, long hashedSeed) {
        MessageDigest sha256 = sha256();
        ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        long lower = structureSeed & MASK48;

        for (long upper = 0; upper < 1 << 16; upper++) {
            long seed = upper << 48 | lower;
            if (hashSeed(seed, sha256, buffer) == hashedSeed) return OptionalLong.of(seed);
        }
        return OptionalLong.empty();
    }

    /** {@code BiomeAccess.hashSeed(seed)}: the first 8 bytes of SHA-256 of the seed, both little endian. */
    public static long hashSeed(long seed) {
        return hashSeed(seed, sha256(), ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN));
    }

    private static long hashSeed(long seed, MessageDigest sha256, ByteBuffer buffer) {
        buffer.clear();
        buffer.putLong(seed);
        byte[] hash = sha256.digest(buffer.array());
        return ByteBuffer.wrap(hash, 0, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
