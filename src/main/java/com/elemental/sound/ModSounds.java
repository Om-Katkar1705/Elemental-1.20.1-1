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

    private static SoundEvent registerSound(String name) {
        Identifier id = Elemental.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void registerModSounds() {
        Elemental.LOGGER.info("Registering ModSounds for " + Elemental.MOD_ID);
    }
}
