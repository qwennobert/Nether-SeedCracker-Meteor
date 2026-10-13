package com.qwennobert.nethercracker.cracker;

/**
 * How the server generated the nether bedrock.
 */
public enum BedrockGeneration {
    /** Vanilla, Fabric, Paper 1.19.2 build 213 and newer, ... */
    Vanilla,
    /** PaperMC before 1.19.2 build 213 used the wrong height for the random, the bedrock looks like pillars. */
    PaperOld
}
