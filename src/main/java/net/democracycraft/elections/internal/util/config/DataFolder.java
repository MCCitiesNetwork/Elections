package net.democracycraft.elections.internal.util.config;

public enum DataFolder {
    PERMISSIONS("permissions"),
    MENUS("menus"),
    EXPORTS("exports"),
    GITHUB("github"),
    EXPORT_MESSAGES("export-messages"),
    ERRORS("errors");


    private final String path;

    DataFolder(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
