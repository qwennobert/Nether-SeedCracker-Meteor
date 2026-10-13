package com.qwennobert.nethercracker;

import com.mojang.logging.LogUtils;
import com.qwennobert.nethercracker.commands.NetherCrackerCommand;
import com.qwennobert.nethercracker.hud.NetherCrackerHud;
import com.qwennobert.nethercracker.modules.NetherSeedCracker;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;

public class NetherCrackerAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Nether Cracker", Items.BEDROCK.getDefaultStack());
    public static final HudGroup HUD_GROUP = new HudGroup("Nether Cracker");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Nether Seed Cracker");

        Modules.get().add(new NetherSeedCracker());
        Commands.add(new NetherCrackerCommand());
        Hud.get().register(NetherCrackerHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.qwennobert.nethercracker";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("qwennobert", "Nether_SeedCracker_Meteor");
    }
}
