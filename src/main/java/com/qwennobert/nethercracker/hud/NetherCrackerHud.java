package com.qwennobert.nethercracker.hud;

import com.qwennobert.nethercracker.NetherCrackerAddon;
import com.qwennobert.nethercracker.modules.NetherSeedCracker;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.List;

public class NetherCrackerHud extends HudElement {
    public static final HudElementInfo<NetherCrackerHud> INFO = new HudElementInfo<>(NetherCrackerAddon.HUD_GROUP, "nether-cracker", "Shows the Nether Seed Cracker status.", NetherCrackerHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showData = sgGeneral.add(new BoolSetting.Builder()
        .name("show-data")
        .description("Show how much bedrock was collected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Text shadow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> titleColor = sgGeneral.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Color of the title.")
        .defaultValue(new SettingColor(200, 40, 40))
        .build()
    );

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Color of the text.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    public NetherCrackerHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        NetherSeedCracker module = Modules.get().get(NetherSeedCracker.class);

        String title = "Nether Cracker";
        List<String> lines = new ArrayList<>();
        lines.add(module.status());
        if (showData.get() && module.state() != NetherSeedCracker.State.Done) {
            lines.add(String.format("Floor %.0f / Roof %.0f bits", module.floorBits(), module.roofBits()));
        }
        if (module.worldSeed() != null) {
            lines.add("Seed: " + module.worldSeed() + (module.worldSeedVerified() ? "" : " (?)"));
        }
        else if (!module.candidates().isEmpty()) {
            lines.add("Structure seeds: " + module.candidates().size());
        }

        double lineHeight = renderer.textHeight(shadow.get());
        double width = renderer.textWidth(title, shadow.get());
        for (String line : lines) width = Math.max(width, renderer.textWidth(line, shadow.get()));
        setSize(width, lineHeight * (lines.size() + 1));

        double y = this.y;
        renderer.text(title, x, y, titleColor.get(), shadow.get());
        for (String line : lines) {
            y += lineHeight;
            renderer.text(line, x, y, textColor.get(), shadow.get());
        }
    }
}
