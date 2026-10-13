package com.qwennobert.nethercracker.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.qwennobert.nethercracker.NetherCrackerAddon;
import com.qwennobert.nethercracker.cracker.BedrockBlock;
import com.qwennobert.nethercracker.cracker.SeedCandidate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Chat messages, the seeds file and handing the seed to other mods.
 */
public final class SeedOutput {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private SeedOutput() {
    }

    public static Path folder() {
        return MeteorClient.FOLDER.toPath().resolve("nether-seedcracker");
    }

    /** A seed in chat that is copied when clicked. */
    public static MutableText seedText(long seed) {
        String value = Long.toString(seed);
        return Text.literal(value).setStyle(Style.EMPTY
            .withColor(Formatting.GREEN)
            .withUnderline(true)
            .withClickEvent(new ClickEvent.CopyToClipboard(value))
            .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to copy"))));
    }

    /** One structure seed and its possible world seeds. */
    public static MutableText describe(SeedCandidate candidate) {
        MutableText text = Text.literal("Structure seed ").append(seedText(candidate.structureSeed()));
        if (candidate.worldSeedCount() == 0) return text.append(", no random or text world seed");

        text.append(", world seed: ");
        boolean first = true;
        for (long seed : candidate.randomWorldSeeds()) {
            if (!first) text.append(" or ");
            text.append(seedText(seed));
            first = false;
        }
        if (candidate.intWorldSeed().isPresent()) {
            if (!first) text.append(" or ");
            text.append(seedText(candidate.intWorldSeed().getAsLong())).append(" (text seed)");
        }
        return text;
    }

    public static String formatTime(long seconds) {
        return seconds >= 3600
            ? String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            : String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // File

    public static void saveWorldSeed(String server, long seed, boolean verified, long structureSeed) {
        append(String.format("%s | %s | world seed %d (%s) | structure seed %d", LocalDateTime.now().format(TIME), server, seed,
            verified ? "verified with hashed seed" : "not verified", structureSeed));
    }

    public static void saveCandidates(String server, Collection<SeedCandidate> candidates) {
        String time = LocalDateTime.now().format(TIME);
        List<String> lines = new ArrayList<>();
        for (SeedCandidate candidate : candidates) {
            StringBuilder line = new StringBuilder(String.format("%s | %s | structure seed %d | possible world seeds:", time, server, candidate.structureSeed()));
            for (long seed : candidate.randomWorldSeeds()) line.append(' ').append(seed);
            candidate.intWorldSeed().ifPresent(seed -> line.append(' ').append(seed).append(" (text seed)"));
            lines.add(line.toString());
        }
        append(String.join(System.lineSeparator(), lines));
    }

    private static void append(String text) {
        try {
            Path file = folder().resolve("seeds.txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, text + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException e) {
            NetherCrackerAddon.LOG.error("Could not save the seed", e);
        }
    }

    /**
     * Writes blocks in the format of the Nether Bedrock Cracker ({@code x y z Bedrock|Other}).
     *
     * @return the file
     */
    public static Path exportBlocks(String server, Collection<BedrockBlock> blocks) throws IOException {
        String name = "bedrock-" + server.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + LocalDateTime.now().format(FILE_TIME) + ".txt";
        Path file = folder().resolve(name);
        Files.createDirectories(file.getParent());

        List<String> lines = new ArrayList<>(blocks.size());
        for (BedrockBlock block : blocks) lines.add(block.toString());
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    // Other mods

    /** Saves the seed for the current server in SeedMapper's SavedSeeds, which SeedMapper (and Seed Miner) then use. */
    public static void sendToSeedMapper(Module module, long seed) {
        if (!FabricLoader.getInstance().isModLoaded("seedmapper")) return;

        // SeedMapper's client commands are handled by Fabric API before anything is sent to the server
        ChatUtils.sendPlayerMsg("/sm:config SavedSeeds add " + seed, false);
        module.info("Saved the seed in SeedMapper (SavedSeeds).");
    }

    /** Sets the manual seed of the Seed Miner addon through its command. */
    public static void sendToSeedMiner(Module module, long seed) {
        if (Commands.get("seedminer") == null) return;

        try {
            Commands.dispatch("seedminer seed " + seed);
        }
        catch (CommandSyntaxException e) {
            module.warning("Could not set the seed in Seed Miner: %s", e.getMessage());
        }
    }
}
