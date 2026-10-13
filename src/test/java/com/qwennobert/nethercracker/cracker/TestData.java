package com.qwennobert.nethercracker.cracker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class TestData {
    private TestData() {
    }

    /** Example data of the Nether Bedrock Cracker for seed 765906787396911863. */
    static List<BedrockBlock> example() {
        return load("/seed-765906787396911863.txt");
    }

    static List<BedrockBlock> load(String resource) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(TestData.class.getResourceAsStream(resource)), StandardCharsets.UTF_8))) {
            List<BedrockBlock> blocks = new ArrayList<>();
            for (String line; (line = reader.readLine()) != null; ) {
                BedrockBlock block = BedrockBlock.parse(line);
                if (block != null) blocks.add(block);
            }
            return blocks;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** All random-layer blocks of one chunk as the world seed generates them. */
    static List<BedrockBlock> chunk(long worldSeed, int chunkX, int chunkZ, BedrockGeneration generation) {
        List<BedrockBlock> blocks = new ArrayList<>();
        for (Surface surface : Surface.values()) {
            for (int y = surface.minY; y <= surface.maxY; y++) {
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        int x = chunkX * 16 + dx, z = chunkZ * 16 + dz;
                        blocks.add(new BedrockBlock(x, y, z, BedrockModel.isBedrock(worldSeed, x, y, z, generation)));
                    }
                }
            }
        }
        return blocks;
    }
}
