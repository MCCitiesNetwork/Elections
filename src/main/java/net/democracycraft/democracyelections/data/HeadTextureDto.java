package net.democracycraft.democracyelections.data;

/**
 * Normalized DTO for a candidate head texture mapping.
 * This decouples candidate identity from the actual ItemStack bytes or provider.
 * Persistence can store (candidateId, texture) pairs for reconstruction later.
 */
public class HeadTextureDto implements Dto {
    private final int candidateId;
    private final String item;

    public HeadTextureDto(int candidateId, String texture) {
        this.candidateId = candidateId;
        this.item = texture;
    }

    public int getCandidateId() { return candidateId; }
    public String getItem() { return item; }
}

