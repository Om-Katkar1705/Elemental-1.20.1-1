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

    // Seismic Colossus Boss Settings
    public float seismicColossusPhase1Health = 400.0f;
    public float seismicColossusPhase2Health = 300.0f;
    public float seismicColossusPhase3Health = 250.0f;
    public float seismicColossusBaseDamage = 16.0f;
    public double seismicColossusSpeed = 0.22;
    public int seismicColossusAttackCooldown = 40;
    public double seismicColossusBlockThrowRange = 24.0;
    public int seismicColossusMinionCount = 4;
    public int seismicColossusWeaponStickDuration = 110;
    public boolean seismicColossusRestoreTerrain = true;

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
