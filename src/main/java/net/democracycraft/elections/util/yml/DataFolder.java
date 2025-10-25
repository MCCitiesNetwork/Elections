package net.democracycraft.elections.util.yml;

public enum DataFolder {
    NPC("npcs"),
    PERMISSIONS("permissions"),
    MENUS("menus");


    private final String path;

    DataFolder(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
