package com.qwennobert.nethercracker.cracker;

import static com.qwennobert.nethercracker.cracker.JavaRandom.MASK48;
import static com.qwennobert.nethercracker.cracker.JavaRandom.MULTIPLIER;

/**
 * How nether bedrock is generated since 1.18 (unchanged up to 1.21.11).
 * <p>
 * The nether uses the legacy random source ({@link java.util.Random}). The surface rules
 * {@code verticalGradient("bedrock_floor", bottom, aboveBottom(5))} and
 * {@code not(verticalGradient("bedrock_roof", belowTop(5), top))} decide per block:
 * <pre>
 * factory  = new Random(worldSeed).nextLong()                        // RandomState, forkPositional()
 * surface  = new Random(nameHash ^ factory).nextLong()               // fromHashOf(name).forkPositional()
 * random   = new Random(Mth.getSeed(x, y, z) ^ surface)              // at(x, y, z)
 * gradient = random.nextFloat() &lt; map(y, from, to, 1, 0)
 * </pre>
 * Only the lower 48 bits of every seed matter, so bedrock reveals the lower 48 bits of the world seed (the structure
 * seed).
 */
public final class BedrockModel {
    private BedrockModel() {
    }

    /** {@code Mth.getSeed(x, y, z)} */
    public static long positionSeed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }

    /** The y that goes into the position seed. Old Paper used the bottom of the gradient for every layer. */
    public static int hashY(int y, Surface surface, BedrockGeneration generation) {
        if (generation == BedrockGeneration.PaperOld) return surface == Surface.FLOOR ? 0 : 122;
        return y;
    }

    /**
     * The chance from the vertical gradient as the surface rule computes it:
     * {@code Mth.map(y, trueAtAndBelow, falseAtAndAbove, 1.0, 0.0)}.
     */
    static double gradient(int y, Surface surface) {
        double from = surface == Surface.FLOOR ? 0 : 122;
        double to = surface == Surface.FLOOR ? 5 : 127;
        double delta = (y - from) / (to - from);
        return 1.0 + delta * (0.0 - 1.0);
    }

    /**
     * {@code nextFloat() < gradient} is true exactly when the next internal state of the random is below the returned
     * value (nextFloat uses the upper 24 of the 48 state bits).
     */
    public static long threshold(int y, Surface surface) {
        long bits = (long) Math.ceil(gradient(y, surface) * (1 << 24));
        return bits << 24;
    }

    /** The chance that a random seed generates this block (bedrock or not) at height {@code y}. */
    public static double probability(int y, boolean bedrock) {
        Surface surface = Surface.at(y);
        if (surface == null) throw new IllegalArgumentException("y " + y + " is not in a random bedrock layer");

        double below = (double) threshold(y, surface) / (1L << 48);
        return surface == Surface.FLOOR == bedrock ? below : 1 - below;
    }

    /** How many bits of the seed a block reveals. */
    public static double bits(int y, boolean bedrock) {
        return -Math.log(probability(y, bedrock)) / Math.log(2);
    }

    /** The seed of the positional random of a surface (only the lower 48 bits are used). */
    public static long surfaceSeed(long worldSeed, Surface surface) {
        long factory = JavaRandom.nextLong(worldSeed);
        return JavaRandom.nextLong(surface.nameHash ^ factory);
    }

    /** Whether the world seed generates bedrock at the position (y must be in a random layer). */
    public static boolean isBedrock(long worldSeed, int x, int y, int z, BedrockGeneration generation) {
        Surface surface = Surface.at(y);
        if (surface == null) throw new IllegalArgumentException("y " + y + " is not in a random bedrock layer");

        long seed = positionSeed(x, hashY(y, surface, generation), z) ^ surfaceSeed(worldSeed, surface);
        long nextState = JavaRandom.step(JavaRandom.scramble(seed));
        boolean below = nextState < threshold(y, surface);
        return surface == Surface.FLOOR ? below : !below;
    }

    /** Whether all blocks match what the world seed generates. */
    public static boolean matches(long worldSeed, Iterable<BedrockBlock> blocks, BedrockGeneration generation) {
        for (BedrockBlock block : blocks) {
            if (isBedrock(worldSeed, block.x(), block.y(), block.z(), generation) != block.bedrock()) return false;
        }
        return true;
    }

    /**
     * What an observed block says about the seed of its surface: the next internal state of the positional random
     * ({@code scramble(positionSeed ^ surfaceSeed)} stepped once) lies in {@code [lower, lower + size)}, wrapping
     * around at 2^48.
     */
    public record Constraint(Surface surface, long positionHash, long lower, long size) {
        public static Constraint of(BedrockBlock block, BedrockGeneration generation) {
            Surface surface = Surface.at(block.y());
            if (surface == null) throw new IllegalArgumentException("y " + block.y() + " is not in a random bedrock layer");

            // The scrambling of setSeed is folded into the hash: state = (surfaceSeed ^ positionHash) & MASK48
            long positionHash = (positionSeed(block.x(), hashY(block.y(), surface, generation), block.z()) ^ MULTIPLIER) & MASK48;

            long threshold = threshold(block.y(), surface);
            boolean below = surface == Surface.FLOOR == block.bedrock();
            return below
                ? new Constraint(surface, positionHash, 0, threshold)
                : new Constraint(surface, positionHash, threshold, (1L << 48) - threshold);
        }

        /** The chance that a random seed matches this block. */
        public double probability() {
            return (double) size / (1L << 48);
        }

        /** How many bits of the seed this block reveals. */
        public double bits() {
            return -Math.log(probability()) / Math.log(2);
        }
    }
}
