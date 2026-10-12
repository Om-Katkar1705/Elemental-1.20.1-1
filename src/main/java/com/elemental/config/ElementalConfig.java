package com.elemental.config;

import com.elemental.Elemental;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ElementalConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "elemental.json");

    public boolean enableIntroNpc = true;
    public boolean enableJetpackGuidance = true;
    public int markerRenderDistance = 1000;
    public boolean spawnOnFirstPlayerJoin = true;

    private static ElementalConfig INSTANCE;

    public static ElementalConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ElementalConfig.class);
            } catch (Exception e) {
                Elemental.LOGGER.error("Failed to load elemental config, using defaults", e);
                INSTANCE = new ElementalConfig();
            }
        } else {
            INSTANCE = new ElementalConfig();
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE != null ? INSTANCE : new ElementalConfig(), writer);
        } catch (Exception e) {
            Elemental.LOGGER.error("Failed to save elemental config", e);
        }
    }
}
