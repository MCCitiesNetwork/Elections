package net.democracycraft.democracyelections.util.permissions.data;

import net.democracycraft.democracyelections.data.Dto;

import java.util.List;


public record PermissionNodesDto(List<String> nodes) implements Dto {
}
