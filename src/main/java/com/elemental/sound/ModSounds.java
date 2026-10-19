package com.elemental.sound;

import com.elemental.Elemental;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    public static final SoundEvent NPC_DIALOGUE = registerSound("npc_dialogue");
    public static final SoundEvent QUEST_ACCEPTED = registerSound("quest_accepted");
    public static final SoundEvent OBJECTIVE_UPDATED = registerSound("objective_updated");
    public static final SoundEvent QUEST_COMPLETED = registerSound("quest_completed");
    public static final SoundEvent JETPACK_GUIDANCE = registerSound("jetpack_guidance");
    public static final SoundEvent JETPACK_THRUST = registerSound("jetpack_thrust");
    public static final SoundEvent JETPACK_WARMUP = registerSound("jetpack_warmup");

    // Seismic Colossus
    public static final SoundEvent SEISMIC_COLOSSUS_STEP = registerSound("seismic_colossus_step");
    public static final SoundEvent SEISMIC_COLOSSUS_SLAM = registerSound("seismic_colossus_slam");
    public static final SoundEvent SEISMIC_COLOSSUS_ROAR = registerSound("seismic_colossus_roar");
    public static final SoundEvent SEISMIC_COLOSSUS_THROW = registerSound("seismic_colossus_throw");
    public static final SoundEvent SEISMIC_COLOSSUS_HURT = registerSound("seismic_colossus_hurt");
    public static final SoundEvent SEISMIC_COLOSSUS_DEATH = registerSound("seismic_colossus_death");
    public static final SoundEvent HAMMER_SPARK = registerSound("hammer_spark");

    private static SoundEvent registerSound(String name) {
        Identifier id = Elemental.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void registerModSounds() {
        Elemental.LOGGER.info("Registering ModSounds for " + Elemental.MOD_ID);
    }
}
