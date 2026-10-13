package com.qwennobert.nethercracker.cracker;

/**
 * The two bedrock surfaces of the nether. Both get their own random from the same seed, mixed with the name of the
 * surface rule.
 */
public enum Surface {
    /** Bedrock at y 0 to 4, random at y 1 to 4. */
    FLOOR("minecraft:bedrock_floor", 1, 4),
    /** Bedrock at y 123 to 127, random at y 123 to 126. */
    ROOF("minecraft:bedrock_roof", 123, 126);

    /** {@code name.hashCode()}, mixed into the seed by {@code fromHashOf(name)}. */
    public final long nameHash;
    /** Lowest and highest y where bedrock is random. */
    public final int minY, maxY;

    Surface(String name, int minY, int maxY) {
        this.nameHash = name.hashCode();
        this.minY = minY;
        this.maxY = maxY;
    }

    /** The surface whose random layers contain {@code y}, or null. */
    public static Surface at(int y) {
        for (Surface surface : values()) {
            if (y >= surface.minY && y <= surface.maxY) return surface;
        }
        return null;
    }

    public Surface other() {
        return this == FLOOR ? ROOF : FLOOR;
    }
}
