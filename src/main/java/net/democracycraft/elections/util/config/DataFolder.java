package net.democracycraft.elections.util.config;

public enum DataFolder {
    PERMISSIONS("permissions"),
    MENUS("menus"),
    EXPORTS("exports");


    private final String path;

    DataFolder(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
