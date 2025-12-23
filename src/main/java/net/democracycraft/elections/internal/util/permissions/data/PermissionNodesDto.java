package net.democracycraft.elections.internal.util.permissions.data;

import net.democracycraft.elections.internal.data.Dto;

import java.util.List;


public record PermissionNodesDto(List<String> nodes) implements Dto {
}
