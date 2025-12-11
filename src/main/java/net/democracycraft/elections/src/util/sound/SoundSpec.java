package net.democracycraft.elections.src.util.sound;

import org.bukkit.SoundCategory;

import java.io.Serializable;

/**
 * Serializable data holder for a sound configuration that can be loaded from YAML.
 */
public class SoundSpec implements Serializable {
    /** Whether the sound should be played. */
    public boolean enabled = true;
    /** Sound name. Can be a Bukkit Sound enum constant or a namespaced key like minecraft:block.note_block.pling */
    public String sound = "ENTITY_PLAYER_LEVELUP";
    /** Category to play the sound in. Defaults to MASTER if null. */
    public SoundCategory category = SoundCategory.MASTER;
    /** Volume of the sound, default 1.0 */
    public float volume = 1.0f;
    /** Pitch of the sound, default 1.0 */
    public float pitch = 1.0f;

    public SoundSpec() {}
}

