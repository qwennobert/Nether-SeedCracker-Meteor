package com.qwennobert.nethercracker.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.qwennobert.nethercracker.cracker.SeedCandidate;
import com.qwennobert.nethercracker.modules.NetherSeedCracker;
import com.qwennobert.nethercracker.util.SeedOutput;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Path;

public class NetherCrackerCommand extends Command {
    public NetherCrackerCommand() {
        super("nethercracker", "Controls the Nether Seed Cracker.", "nsc");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> status());
        builder.then(literal("status").executes(context -> status()));

        builder.then(literal("start").executes(context -> {
            module().start(true);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("stop").executes(context -> {
            module().stop();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("reset").executes(context -> {
            module().reset();
            info("Collected bedrock and results cleared.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("seeds").executes(context -> {
            NetherSeedCracker module = module();
            if (module.candidates().isEmpty()) info("No seeds found yet.");
            for (SeedCandidate candidate : module.candidates()) info(SeedOutput.describe(candidate));
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("export").executes(context -> {
            NetherSeedCracker module = module();
            if (module.collector().size() == 0) {
                error("No bedrock collected yet.");
                return SINGLE_SUCCESS;
            }
            try {
                Path file = SeedOutput.exportBlocks(module.server(), module.collector().blocks());
                info("Saved %d blocks to (highlight)%s(default).", module.collector().size(), file);
            }
            catch (IOException e) {
                error("Could not export: %s", e.getMessage());
            }
            return SINGLE_SUCCESS;
        }));
    }

    private int status() {
        NetherSeedCracker module = module();
        info("Status: %s", module.status());
        info("Data: %d blocks from %d chunks, floor %.0f / roof %.0f bits", module.collector().size(), module.collector().scannedChunks(), module.floorBits(), module.roofBits());
        if (module.worldSeed() != null) {
            info(Text.literal("World seed: ").append(SeedOutput.seedText(module.worldSeed())).append(module.worldSeedVerified() ? " (verified)" : " (not verified)"));
        }
        else if (!module.candidates().isEmpty()) {
            info("Structure seeds: %d (list them with .nsc seeds)", module.candidates().size());
        }
        return SINGLE_SUCCESS;
    }

    private static NetherSeedCracker module() {
        return Modules.get().get(NetherSeedCracker.class);
    }
}
