package com.qwennobert.nethercracker.cracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Picks the blocks that tell the most about the seed. Bedrock at y 4 and 123 and non-bedrock at y 1 and 126 are rare,
 * so each of them reveals more (2.3 bits) than a common block (0.3 bits).
 */
public final class BlockSelector {
    private BlockSelector() {
    }

    /**
     * Returns up to {@code maxPerSurface} blocks of each surface, the most informative first.
     */
    public static List<BedrockBlock> select(Collection<BedrockBlock> blocks, int maxPerSurface) {
        List<Scored> floor = new ArrayList<>();
        List<Scored> roof = new ArrayList<>();
        for (BedrockBlock block : blocks) {
            Surface surface = Surface.at(block.y());
            if (surface == null) continue;
            Scored scored = new Scored(block, BedrockModel.bits(block.y(), block.bedrock()));
            (surface == Surface.FLOOR ? floor : roof).add(scored);
        }

        List<BedrockBlock> result = new ArrayList<>();
        for (List<Scored> list : List.of(floor, roof)) {
            // Stable sort: blocks with the same information keep the order they were found in
            list.sort(Comparator.comparingDouble(Scored::bits).reversed());
            for (int i = 0; i < Math.min(maxPerSurface, list.size()); i++) result.add(list.get(i).block());
        }
        return result;
    }

    /** Total information of the blocks of one surface in bits. */
    public static double bits(Collection<BedrockBlock> blocks, Surface surface) {
        double bits = 0;
        for (BedrockBlock block : blocks) {
            if (Surface.at(block.y()) == surface) bits += BedrockModel.bits(block.y(), block.bedrock());
        }
        return bits;
    }

    private record Scored(BedrockBlock block, double bits) {
    }
}
