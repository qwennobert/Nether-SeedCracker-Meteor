package com.qwennobert.nethercracker.cracker;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaRandomTest {
    @Test
    void nextLongMatchesJavaUtilRandom() {
        Random seeds = new Random(1);
        for (int i = 0; i < 10_000; i++) {
            long seed = seeds.nextLong();
            assertEquals(new Random(seed).nextLong(), JavaRandom.nextLong(seed));
        }
    }

    @Test
    void nextFloatBitsMatchJavaUtilRandom() {
        Random seeds = new Random(2);
        for (int i = 0; i < 10_000; i++) {
            long seed = seeds.nextLong();
            assertEquals(new Random(seed).nextFloat(), JavaRandom.nextFloatBits(seed) / (float) (1 << 24));
        }
    }

    @Test
    void reverseNextLongFindsTheSeed() {
        long[] out = new long[4];
        Random seeds = new Random(3);
        for (int i = 0; i < 1_000_000; i++) {
            long seed = seeds.nextLong() & JavaRandom.MASK48;
            int count = JavaRandom.reverseNextLong(JavaRandom.nextLong(seed), out);

            boolean found = false;
            for (int j = 0; j < count; j++) {
                assertEquals(JavaRandom.nextLong(seed) & JavaRandom.MASK48, JavaRandom.nextLong(out[j]) & JavaRandom.MASK48);
                found |= out[j] == seed;
            }
            assertTrue(found, "seed " + seed + " not found");
        }
    }
}
