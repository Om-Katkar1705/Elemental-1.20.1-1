package com.elemental.client.sound;

import com.elemental.sound.ModSounds;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;

/**
 * Looping moving sound instance for continuous 3D spatialized jetpack flight audio.
 * Seamlessly loops the 8-second thruster audio file while active and stops immediately when flight ends.
 */
public class JetpackFlightSoundInstance extends MovingSoundInstance {
    private final AbstractClientPlayerEntity player;
    private boolean active = true;

    public JetpackFlightSoundInstance(AbstractClientPlayerEntity player) {
        super(ModSounds.JETPACK_THRUST, SoundCategory.PLAYERS, SoundInstance.createRandom());
        this.player = player;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 0.35f; // pleasant low volume
        this.pitch = 1.0f;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    public void stopSound() {
        this.active = false;
        this.setDone();
    }

    @Override
    public void tick() {
        if (this.player.isRemoved() || !this.active) {
            this.setDone();
            return;
        }
        this.x = this.player.getX();
        this.y = this.player.getY();
        this.z = this.player.getZ();
    }
}
