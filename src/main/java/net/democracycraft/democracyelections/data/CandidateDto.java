package net.democracycraft.democracyelections.data;

public class CandidateDto implements Dto {
    private final int id;
    private final String name;
    private final String headDatabaseId;

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
}

