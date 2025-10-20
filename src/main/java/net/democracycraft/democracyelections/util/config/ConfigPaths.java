package net.democracycraft.democracyelections.util.config;

public enum ConfigPaths {
    ELECTION_MANAGER_TILE("");

    private final String path;

    ConfigPaths(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}

