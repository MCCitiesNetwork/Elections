package net.democracycraft.democracyelections.data;

import java.util.List;
import java.util.Set;

public class RequirementsDto implements Dto {
    private final List<String> permissions; // puede ser vacío
    private final long minActivePlaytimeMinutes; // 0 si no aplica

    public RequirementsDto(List<String> permissions, long minActivePlaytimeMinutes) {

        this.permissions = permissions;
        this.minActivePlaytimeMinutes = Math.max(0, minActivePlaytimeMinutes);
    }

    public List<String> getPermissions() { return permissions; }
    public long getMinActivePlaytimeMinutes() { return minActivePlaytimeMinutes; }
    public void setNewPermissions(List<String> newPermissions) {
        getPermissions().clear();
        getPermissions().addAll(newPermissions);
    }
}
