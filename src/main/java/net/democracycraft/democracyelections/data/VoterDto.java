package net.democracycraft.democracyelections.data;

public class VoterDto implements Dto {
    private final int id;
    private final String name;

    public VoterDto(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() { return id; }
    public String getName() { return name; }
}

