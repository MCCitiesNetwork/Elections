package net.democracycraft.elections.util.permissions.data;

import net.democracycraft.elections.data.Dto;

import java.util.List;


public record PermissionNodesDto(List<String> nodes) implements Dto {
}
