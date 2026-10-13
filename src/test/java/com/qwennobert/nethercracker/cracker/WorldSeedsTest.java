package com.qwennobert.nethercracker.cracker;

import com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSeedsTest {
    @Test
    void hashSeedMatchesMinecraft() {
        Random random = new Random(13);
        for (int i = 0; i < 1000; i++) {
            long seed = random.nextLong();
            // BiomeAccess.hashSeed
            assertEquals(Hashing.sha256().hashLong(seed).asLong(), WorldSeeds.hashSeed(seed));
        }
    }

    @Test
    void randomWorldSeedsContainNextLongSeeds() {
        Random random = new Random(14);
        for (int i = 0; i < 10_000; i++) {
            long worldSeed = new Random(random.nextLong()).nextLong();
            boolean found = false;
            for (long seed : WorldSeeds.randomWorldSeeds(worldSeed & JavaRandom.MASK48)) {
                assertEquals(worldSeed & JavaRandom.MASK48, seed & JavaRandom.MASK48);
                found |= seed == worldSeed;
            }
            assertTrue(found);
        }
        assertTrue(contains(WorldSeeds.randomWorldSeeds(BedrockModelTest.WORLD_SEED & JavaRandom.MASK48), BedrockModelTest.WORLD_SEED));
    }

    @Test
    void intWorldSeed() {
        long textSeed = "Hello World".hashCode();
        assertEquals(OptionalLong.of(textSeed), WorldSeeds.intWorldSeed(textSeed & JavaRandom.MASK48));
        assertEquals(OptionalLong.of(-5L), WorldSeeds.intWorldSeed(-5L & JavaRandom.MASK48));
        assertEquals(OptionalLong.empty(), WorldSeeds.intWorldSeed(BedrockModelTest.WORLD_SEED & JavaRandom.MASK48));
    }

    @Test
    void fromHashedSeed() {
        Random random = new Random(15);
        for (int i = 0; i < 5; i++) {
            long worldSeed = random.nextLong();
            assertEquals(OptionalLong.of(worldSeed), WorldSeeds.fromHashedSeed(worldSeed & JavaRandom.MASK48, WorldSeeds.hashSeed(worldSeed)));
        }
        assertEquals(OptionalLong.empty(), WorldSeeds.fromHashedSeed(123, WorldSeeds.hashSeed(456)));
    }

    private static boolean contains(long[] array, long value) {
        for (long l : array) if (l == value) return true;
        return false;
    }
}
