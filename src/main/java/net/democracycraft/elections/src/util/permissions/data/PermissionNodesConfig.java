package net.democracycraft.elections.src.util.permissions.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Serializable configuration holder used for YAML persistence of permission nodes.
 * This class exists to allow reflection-based serialization/deserialization with AutoYML
 * and provides adapters to/from the public PermissionNodesDto.
 */
public class PermissionNodesConfig implements Serializable {

    private List<String> nodes = new ArrayList<>();

    public PermissionNodesConfig() {}

    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes == null ? new ArrayList<>() : nodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Creates an immutable DTO view of this config. */
    public PermissionNodesDto toDto() {
        return new PermissionNodesDto(List.copyOf(nodes));
    }

    /** Creates a config instance from a DTO. */
    public static PermissionNodesConfig fromDto(PermissionNodesDto dto) {
        PermissionNodesConfig cfg = new PermissionNodesConfig();
        if (dto != null && dto.nodes() != null) {
            cfg.setNodes(dto.nodes());
        }
        return cfg;
    }
}
