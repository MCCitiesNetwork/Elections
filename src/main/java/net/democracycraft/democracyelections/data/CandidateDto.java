package net.democracycraft.democracyelections.data;

/**
 * DTO representing a candidate in an election.
 * In addition to the immutable id and name, an optional HeadDatabase id can be
 * provided to render a custom player head icon, and an optional serialized
 * ItemStack (as bytes) can be stored to avoid recomputing the head item.
 */
public class CandidateDto implements Dto {
    private final int id;
    private final String name;
    private final String headDatabaseId;
    /**
     * Optional serialized ItemStack bytes representing the candidate head icon.
     * When present, the UI can deserialize this and show the exact item.
     */
    private byte[] headItemBytes;

    public CandidateDto(int id, String name) {
        this(id, name, null);
    }

    public CandidateDto(int id, String name, String headDatabaseId) {
        this.id = id;
        this.name = name;
        this.headDatabaseId = headDatabaseId;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getHeadDatabaseId() { return headDatabaseId; }

    /**
     * @return optional serialized ItemStack (player head) to display in dialogs.
     */
    public byte[] getHeadItemBytes() { return headItemBytes; }

    /**
     * Sets the optional serialized ItemStack (player head) bytes.
     * @param headItemBytes serialized ItemStack bytes or null to clear.
     */
    public void setHeadItemBytes(byte[] headItemBytes) { this.headItemBytes = headItemBytes; }
}
