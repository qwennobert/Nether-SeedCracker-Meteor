package com.qwennobert.nethercracker.mixin;

import net.minecraft.world.biome.source.BiomeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The client's biome access holds the hashed seed the server sends on join (first 8 bytes of SHA-256 of the world
 * seed). It is used to find the full 64 bit world seed for a cracked structure seed.
 */
@Mixin(BiomeAccess.class)
public interface BiomeAccessAccessor {
    @Accessor("seed")
    long nethercracker$getHashedSeed();
}
