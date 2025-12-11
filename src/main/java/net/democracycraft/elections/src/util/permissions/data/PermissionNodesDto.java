package net.democracycraft.elections.src.util.permissions.data;

import net.democracycraft.elections.src.data.Dto;

import java.util.List;


public record PermissionNodesDto(List<String> nodes) implements Dto {
}
