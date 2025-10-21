package net.democracycraft.democracyelections.util.yml;

public enum DataFolder {
    NPC("npcs"),
    PERMISSIONS("permissions");


    private final String path;

    DataFolder(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
