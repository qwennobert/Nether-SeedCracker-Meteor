package com.qwennobert.nethercracker.cracker;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.RandomSplitter;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockModelTest {
    /** Values from the tests of the Nether Bedrock Cracker. */
    static final long WORLD_SEED = 765906787396911863L;
    static final long ROOF_SEED = 191924403737289L;
    static final long FLOOR_SEED = 18240473916414L;

    @Test
    void surfaceNameHashes() {
        assertEquals(2042456806L, Surface.FLOOR.nameHash);
        assertEquals(343340730L, Surface.ROOF.nameHash);
    }

    @Test
    void surfaceSeeds() {
        assertEquals(ROOF_SEED, BedrockModel.surfaceSeed(WORLD_SEED, Surface.ROOF) & JavaRandom.MASK48);
        assertEquals(FLOOR_SEED, BedrockModel.surfaceSeed(WORLD_SEED, Surface.FLOOR) & JavaRandom.MASK48);
    }

    @Test
    @SuppressWarnings("deprecation")
    void positionSeed() {
        assertEquals(99261249361405L, BedrockModel.positionSeed(-98, 4, -469) & JavaRandom.MASK48);

        Random random = new Random(4);
        for (int i = 0; i < 10_000; i++) {
            int x = random.nextInt(60_000_000) - 30_000_000;
            int y = random.nextInt(128);
            int z = random.nextInt(60_000_000) - 30_000_000;
            assertEquals(MathHelper.hashCode(x, y, z), BedrockModel.positionSeed(x, y, z));
        }
    }

    /** Same chain as the game: NoiseConfig.randomDeriver, getOrCreateRandomDeriver, VerticalGradientPredicate. */
    @Test
    void matchesMinecraftRandom() {
        Random random = new Random(5);
        for (int i = 0; i < 200; i++) {
            long worldSeed = random.nextLong();
            RandomSplitter deriver = new CheckedRandom(worldSeed).nextSplitter();

            for (Surface surface : Surface.values()) {
                RandomSplitter positional = deriver.split("minecraft:" + (surface == Surface.FLOOR ? "bedrock_floor" : "bedrock_roof")).nextSplitter();
                int trueAtAndBelow = surface == Surface.FLOOR ? 0 : 122;
                int falseAtAndAbove = surface == Surface.FLOOR ? 5 : 127;

                for (int j = 0; j < 100; j++) {
                    int x = random.nextInt(2000) - 1000;
                    int z = random.nextInt(2000) - 1000;
                    int y = surface.minY + random.nextInt(4);

                    double chance = MathHelper.map(y, trueAtAndBelow, falseAtAndAbove, 1.0, 0.0);
                    boolean gradient = positional.split(x, y, z).nextFloat() < chance;
                    boolean bedrock = surface == Surface.FLOOR ? gradient : !gradient;

                    assertEquals(bedrock, BedrockModel.isBedrock(worldSeed, x, y, z, BedrockGeneration.Vanilla), "seed " + worldSeed + " at " + x + " " + y + " " + z);
                }
            }
        }
    }

    @Test
    void exampleDataMatchesItsSeed() {
        var blocks = TestData.example();
        assertEquals(36, blocks.size());
        assertTrue(BedrockModel.matches(WORLD_SEED, blocks, BedrockGeneration.Vanilla));
        assertTrue(!BedrockModel.matches(WORLD_SEED + 1, blocks, BedrockGeneration.Vanilla));
    }

    @Test
    void constraintsAgreeWithTheModel() {
        Random random = new Random(6);
        for (int i = 0; i < 20_000; i++) {
            long worldSeed = random.nextLong();
            int x = random.nextInt(2000) - 1000;
            int z = random.nextInt(2000) - 1000;
            Surface surface = random.nextBoolean() ? Surface.FLOOR : Surface.ROOF;
            int y = surface.minY + random.nextInt(4);

            boolean bedrock = BedrockModel.isBedrock(worldSeed, x, y, z, BedrockGeneration.Vanilla);
            BedrockModel.Constraint constraint = BedrockModel.Constraint.of(new BedrockBlock(x, y, z, bedrock), BedrockGeneration.Vanilla);
            BedrockModel.Constraint opposite = BedrockModel.Constraint.of(new BedrockBlock(x, y, z, !bedrock), BedrockGeneration.Vanilla);

            long surfaceSeed = BedrockModel.surfaceSeed(worldSeed, surface) & JavaRandom.MASK48;
            long next = JavaRandom.step(surfaceSeed ^ constraint.positionHash());
            assertTrue(((next - constraint.lower()) & JavaRandom.MASK48) < constraint.size());
            assertTrue(((next - opposite.lower()) & JavaRandom.MASK48) >= opposite.size());
        }
    }
}
