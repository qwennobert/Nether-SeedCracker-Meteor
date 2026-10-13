package com.qwennobert.nethercracker.cracker;

/**
 * An observed block in one of the random bedrock layers: either bedrock or anything else.
 */
public record BedrockBlock(int x, int y, int z, boolean bedrock) {
    /** One line in the format of the Nether Bedrock Cracker: {@code x y z Bedrock|Other}. */
    @Override
    public String toString() {
        return x + " " + y + " " + z + " " + (bedrock ? "Bedrock" : "Other");
    }

    /** Parses a line written by {@link #toString()}, returns null if it is not one. */
    public static BedrockBlock parse(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 4) return null;
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return switch (parts[3].toLowerCase()) {
                case "bedrock" -> new BedrockBlock(x, y, z, true);
                case "other" -> new BedrockBlock(x, y, z, false);
                default -> null;
            };
        }
        catch (NumberFormatException e) {
            return null;
        }
    }
}
